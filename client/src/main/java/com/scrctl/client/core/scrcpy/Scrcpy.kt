package com.scrctl.client.core.scrcpy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.util.Log
import android.view.Surface
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale
import kotlin.random.Random

class Scrcpy(
	private val kadb: Kadb,
	private val scope: CoroutineScope,
	private val serverRuntime: Server.Runtime,
) : AutoCloseable {
	private data class SessionResources(
		val session: Server.Session? = null,
		val videoDemuxer: Demuxer? = null,
		val audioDemuxer: Demuxer? = null,
		val controller: Controller? = null,
	)

	private class VideoDecodeState {
		var firstPacketReceived = false
		var decodeStarted = false
		var decodeSuccess = false
	}

	private var videoDemuxer: Demuxer? = null
	private var audioDemuxer: Demuxer? = null
	private var controller: Controller? = null
	private var activeSession: Server.Session? = null
	private var activeOptions: ScrcpyOptions? = null

	@Volatile
	private var attachedSurface: Surface? = null

	private val decoderCallbacks = object : Decoder.Callbacks {
		override fun getVideoSurface(decoder: Decoder): Surface? {
			return attachedSurface
		}

		override fun getAudioPlaybackConfig(
			decoder: Decoder,
			config: Demuxer.StreamConfig,
		): Decoder.AudioPlaybackConfig? {
			val channelMask = when (config.channelCount) {
				1 -> AudioFormat.CHANNEL_OUT_MONO
				2 -> AudioFormat.CHANNEL_OUT_STEREO
				else -> null
			}
			return Decoder.AudioPlaybackConfig(
				usage = AudioAttributes.USAGE_MEDIA,
				contentType = AudioAttributes.CONTENT_TYPE_MUSIC,
				encoding = AudioFormat.ENCODING_PCM_16BIT,
				channelMask = channelMask,
			)
		}
	}

	private val videoDecoder = Decoder(name = "video", callbacks = decoderCallbacks)
	private val audioDecoder = Decoder(name = "audio", callbacks = decoderCallbacks)

	@Volatile
	private var started = false

	private val eventDispatcher = ScrcpyEventDispatcher(
		requireAudioEnabled = { activeOptions?.requireAudio == true },
	)

	fun observeSessionEvents(): SharedFlow<SessionEvent> = eventDispatcher.observeSessionEvents()

	fun getController(): Controller {
		return controller ?: throw IllegalStateException("Scrcpy 控制通道未就绪，请先确保 start() 成功并处于连接状态")
	}

	suspend fun start(options: ScrcpyOptions = ScrcpyOptions()) {
		if (started) {
			Log.d(TAG, "start() ignored: session already started")
			return
		}

		Log.i(
			TAG,
			"Starting scrcpy session: video=${options.video}, audio=${options.audio}, control=${options.control}, scid=${if (options.scid >= 0) String.format(Locale.US, "%08x", options.scid) else "auto"}",
		)

		resetSessionSignals()
		pushServerJar()
		val session = startServerSession(options)
		val resources = try {
			Log.d(TAG, "Creating session resources")
			createSessionResources(options, session)
		} catch (t: Throwable) {
			Log.e(TAG, "Failed to create session resources", t)
			shutdownResources(SessionResources(session = session), emitDisconnected = true)
			throw t
		}

		try {
			Log.d(TAG, "Starting session resources")
			startSessionResources(resources)
			activateSession(resources, options)
			Log.i(TAG, "Scrcpy session started")
		} catch (t: Throwable) {
			Log.e(TAG, "Failed to activate scrcpy session", t)
			shutdownResources(resources, emitDisconnected = true)
			throw t
		}
	}

	fun isStarted(): Boolean = started

	fun attachSurface(surface: Surface) {
		Log.d(TAG, "Attaching render surface")
		attachedSurface = surface
		videoDecoder.invalidateOutput()
	}

	fun detachSurface() {
		Log.d(TAG, "Detaching render surface")
		attachedSurface = null
		videoDecoder.invalidateOutput()
	}

	fun stop() {
		if (!started && activeSession == null && videoDemuxer == null && audioDemuxer == null && controller == null) {
			Log.d(TAG, "stop() ignored: no active session")
			return
		}

		Log.i(TAG, "Stopping scrcpy session")

		val resources = clearActiveSession()
		shutdownResources(resources, emitDisconnected = true)
	}

	override fun close() {
		stop()
	}

	private fun generateScid(): Int {
		return Random.nextInt() and Int.MAX_VALUE
	}

	private fun resetSessionSignals() {
		eventDispatcher.reset()
	}

	private fun pushServerJar() {
		emitStage(SessionStage.PUSH_SERVER_JAR, StageStatus.STARTED)
		Log.d(TAG, "Pushing server jar to device: ${serverRuntime.remoteJarPath}")
		runCatching {
			kadb.shell("echo connected")
			kadb.push(serverRuntime.serverJar, serverRuntime.remoteJarPath)
		}.onSuccess {
			Log.i(TAG, "Server jar pushed successfully")
			emitStage(SessionStage.PUSH_SERVER_JAR, StageStatus.SUCCESS)
		}.onFailure {
			Log.e(TAG, "Failed to push server jar", it)
			emitStage(SessionStage.PUSH_SERVER_JAR, StageStatus.FAILED, it)
			throw it
		}
	}

	private suspend fun startServerSession(options: ScrcpyOptions): Server.Session {
		val scid = if (options.scid >= 0) options.scid else generateScid()
		Log.d(TAG, "Starting server session with scid=${String.format(Locale.US, "%08x", scid)}")
		val server = Server(
			kadb = kadb,
			scope = scope,
			runtime = serverRuntime,
			callbacks = eventDispatcher.serverCallbacks,
		)

		emitStage(SessionStage.START_SERVER_SERVICE, StageStatus.STARTED)
		return runCatching {
			server.start(
				Server.Params(
					scid = scid,
					video = options.video,
					audio = options.audio,
					control = options.control,
					forceAdbForward = options.forceAdbForward,
					portRange = options.portRange,
					videoBitRate = options.videoBitRate,
					audioBitRate = options.audioBitRate,
					maxSize = options.maxSize,
					videoCodec = options.videoCodec,
					audioCodec = options.audioCodec,
					wmWidth = options.wmWidth,
					wmHeight = options.wmHeight,
					wmDensity = options.wmDensity,
				)
			)
		}.onSuccess {
			Log.i(TAG, "Server session established")
			emitStage(SessionStage.START_SERVER_SERVICE, StageStatus.SUCCESS)
		}.onFailure {
			Log.e(TAG, "Failed to start server session", it)
			emitStage(SessionStage.START_SERVER_SERVICE, StageStatus.FAILED, it)
		}.getOrThrow()
	}

	private fun createSessionResources(options: ScrcpyOptions, session: Server.Session): SessionResources {
		return SessionResources(
			session = session,
			videoDemuxer = buildVideoDemuxer(options, session),
			audioDemuxer = buildAudioDemuxer(options, session),
			controller = buildController(options, session),
		)
	}

	private fun startSessionResources(resources: SessionResources) {
		videoDecoder.invalidateOutput()

		if (resources.videoDemuxer != null && !resources.videoDemuxer.start()) {
			Log.e(TAG, "Failed to start video demuxer")
			throw IllegalStateException("视频 demuxer 启动失败")
		}
		if (resources.videoDemuxer != null) {
			Log.d(TAG, "Video demuxer started")
		}

		if (resources.audioDemuxer != null && !resources.audioDemuxer.start()) {
			Log.e(TAG, "Failed to start audio demuxer")
			throw IllegalStateException("音频 demuxer 启动失败")
		}
		if (resources.audioDemuxer != null) {
			Log.d(TAG, "Audio demuxer started")
		}
	}

	private fun activateSession(resources: SessionResources, options: ScrcpyOptions) {
		videoDemuxer = resources.videoDemuxer
		audioDemuxer = resources.audioDemuxer
		controller = resources.controller
		activeSession = resources.session
		activeOptions = options
		started = true
		Log.d(TAG, "Session activated: device=${resources.session?.deviceName ?: "unknown"}")
	}

	private fun clearActiveSession(): SessionResources {
		started = false
		val resources = SessionResources(
			session = activeSession,
			videoDemuxer = videoDemuxer,
			audioDemuxer = audioDemuxer,
			controller = controller,
		)

		videoDemuxer = null
		audioDemuxer = null
		controller = null
		activeSession = null
		activeOptions = null
		return resources
	}

	private fun shutdownResources(resources: SessionResources, emitDisconnected: Boolean) {
		Log.d(
			TAG,
			"Shutting down resources: video=${resources.videoDemuxer != null}, audio=${resources.audioDemuxer != null}, control=${resources.controller != null}, emitDisconnected=$emitDisconnected",
		)
		runCatching { resources.videoDemuxer?.close() }
		runCatching { resources.audioDemuxer?.close() }
		runCatching { resources.controller?.close() }

		runCatching { resources.videoDemuxer?.join() }
		runCatching { resources.audioDemuxer?.join() }

		runCatching { resources.session?.videoChannel?.close() }
		runCatching { resources.session?.audioChannel?.close() }
		runCatching { resources.session?.controlChannel?.close() }
		runCatching { resources.session?.forwarder?.close() }
		runCatching { resources.session?.serverJob?.cancel() }

		runCatching { audioDecoder.close() }
		runCatching { videoDecoder.close() }

		if (emitDisconnected) {
			eventDispatcher.emitConnectionLost()
		}
		Log.i(TAG, "Scrcpy resources released")
	}

	private fun buildVideoDemuxer(options: ScrcpyOptions, session: Server.Session): Demuxer? {
		if (!options.video) {
			Log.d(TAG, "Video stream disabled by options")
			return null
		}

		val channel = session.videoChannel ?: throw IllegalStateException("视频通道未建立")
		Log.d(TAG, "Creating video demuxer")
		val demuxer = Demuxer(MediaStream.VIDEO.streamName, channel.inputStream, channel)
		demuxer.addSink(createVideoDecoderSink())
		return demuxer
	}

	private fun buildAudioDemuxer(options: ScrcpyOptions, session: Server.Session): Demuxer? {
		if (!options.audio) {
			Log.d(TAG, "Audio stream disabled by options")
			return null
		}

		val channel = session.audioChannel
		if (channel == null) {
			if (options.requireAudio) {
				Log.e(TAG, "Audio channel is required but not established")
				reportDemuxerTermination(MediaStream.AUDIO, Demuxer.Status.ERROR)
				throw IllegalStateException("音频通道未建立")
			}
			Log.w(TAG, "Audio channel not established, marking audio stream disabled")
			reportDemuxerTermination(MediaStream.AUDIO, Demuxer.Status.DISABLED)
			return null
		}

		Log.d(TAG, "Creating audio demuxer")
		val demuxer = Demuxer(MediaStream.AUDIO.streamName, channel.inputStream, channel)
		demuxer.addSink(createAudioDecoderSink())
		return demuxer
	}

	private fun createVideoDecoderSink(): Demuxer.PacketSink {
		val state = VideoDecodeState()
		return object : Demuxer.PacketSink {
			override fun open(demuxer: Demuxer, config: Demuxer.StreamConfig): Boolean {
				return videoDecoder.open(demuxer, config)
			}

			override fun push(demuxer: Demuxer, packet: Demuxer.Packet): Boolean {
				handleVideoPacketState(state)
				return forwardDecoderPacket(
					demuxer = demuxer,
					packet = packet,
					stream = MediaStream.VIDEO,
					decoderPush = videoDecoder::push,
					onFailure = {
						emitStage(SessionStage.START_DECODE, StageStatus.FAILED)
					},
					onFirstSuccess = {
						if (!state.decodeSuccess) {
							state.decodeSuccess = true
							emitStage(SessionStage.START_DECODE, StageStatus.SUCCESS)
							emitStage(SessionStage.DECODE_SUCCESS, StageStatus.SUCCESS)
						}
					},
				)
			}

			override fun close(demuxer: Demuxer) {
				closeDecoderSink(demuxer, MediaStream.VIDEO, videoDecoder::close)
			}

			override fun disable(demuxer: Demuxer) {
				disableDecoderSink(demuxer, MediaStream.VIDEO, videoDecoder::disable)
			}
		}
	}

	private fun createAudioDecoderSink(): Demuxer.PacketSink {
		return createDecoderSink(
			stream = MediaStream.AUDIO,
			decoderOpen = audioDecoder::open,
			decoderPush = audioDecoder::push,
			decoderClose = audioDecoder::close,
			decoderDisable = audioDecoder::disable,
		)
	}

	private fun createDecoderSink(
		stream: MediaStream,
		decoderOpen: (Demuxer, Demuxer.StreamConfig) -> Boolean,
		decoderPush: (Demuxer, Demuxer.Packet) -> Boolean,
		decoderClose: (Demuxer) -> Unit,
		decoderDisable: (Demuxer) -> Unit,
		onBeforePush: (() -> Unit)? = null,
		onFailure: (() -> Unit)? = null,
		onFirstSuccess: (() -> Unit)? = null,
	): Demuxer.PacketSink {
		var delivered = false
		return object : Demuxer.PacketSink {
			override fun open(demuxer: Demuxer, config: Demuxer.StreamConfig): Boolean {
				return decoderOpen(demuxer, config)
			}

			override fun push(demuxer: Demuxer, packet: Demuxer.Packet): Boolean {
				onBeforePush?.invoke()
				return forwardDecoderPacket(
					demuxer = demuxer,
					packet = packet,
					stream = stream,
					decoderPush = decoderPush,
					onFailure = onFailure,
					onFirstSuccess = {
						if (!delivered) {
							delivered = true
							onFirstSuccess?.invoke()
						}
					},
				)
			}

			override fun close(demuxer: Demuxer) {
				closeDecoderSink(demuxer, stream, decoderClose)
			}

			override fun disable(demuxer: Demuxer) {
				disableDecoderSink(demuxer, stream, decoderDisable)
			}
		}
	}

	private fun handleVideoPacketState(state: VideoDecodeState) {
		if (!state.firstPacketReceived) {
			state.firstPacketReceived = true
			emitStage(SessionStage.FIRST_VIDEO_PACKET, StageStatus.SUCCESS)
		}

		if (!state.decodeStarted) {
			state.decodeStarted = true
			emitStage(SessionStage.START_DECODE, StageStatus.STARTED)
		}
	}

	private fun forwardDecoderPacket(
		demuxer: Demuxer,
		packet: Demuxer.Packet,
		stream: MediaStream,
		decoderPush: (Demuxer, Demuxer.Packet) -> Boolean,
		onFailure: (() -> Unit)? = null,
		onFirstSuccess: (() -> Unit)? = null,
	): Boolean {
		val ok = decoderPush(demuxer, packet)
		if (!ok) {
			reportDemuxerTermination(stream, Demuxer.Status.ERROR)
			onFailure?.invoke()
			return false
		}

		onFirstSuccess?.invoke()
		return true
	}

	private fun closeDecoderSink(
		demuxer: Demuxer,
		stream: MediaStream,
		decoderClose: (Demuxer) -> Unit,
	) {
		decoderClose(demuxer)
		reportDemuxerTermination(stream, Demuxer.Status.EOS)
	}

	private fun disableDecoderSink(
		demuxer: Demuxer,
		stream: MediaStream,
		decoderDisable: (Demuxer) -> Unit,
	) {
		decoderDisable(demuxer)
		reportDemuxerTermination(stream, Demuxer.Status.DISABLED)
	}

	private fun buildController(options: ScrcpyOptions, session: Server.Session): Controller? {
		if (!options.control) {
			Log.d(TAG, "Control channel disabled by options")
			return null
		}

		val controlChannel = session.controlChannel ?: throw IllegalStateException("控制通道未建立")
		Log.d(TAG, "Creating controller")
		return Controller(
			channel = controlChannel,
			callbacks = eventDispatcher.controllerCallbacks,
		)
	}

	private fun emitStage(stage: SessionStage, status: StageStatus, error: Throwable? = null) {
		eventDispatcher.emitStage(stage, status, error)
	}

	private fun reportDemuxerTermination(stream: MediaStream, status: Demuxer.Status) {
		when (status) {
			Demuxer.Status.ERROR -> Log.e(TAG, "Stream terminated with error: ${stream.streamName}")
			Demuxer.Status.EOS -> Log.i(TAG, "Stream ended: ${stream.streamName}")
			Demuxer.Status.DISABLED -> Log.i(TAG, "Stream disabled: ${stream.streamName}")
		}
		eventDispatcher.reportStreamTermination(stream, status)
	}

	enum class SessionStage {
		PUSH_SERVER_JAR,
		START_SERVER_SERVICE,
		FIRST_VIDEO_PACKET,
		START_DECODE,
		DECODE_SUCCESS,
	}

	enum class StageStatus {
		STARTED,
		SUCCESS,
		FAILED,
	}

	enum class SessionIssue {
		STREAM,
		CONTROL,
	}

	enum class SessionTermination {
		CONNECTION_LOST,
	}

	enum class MediaStream(
		val streamName: String,
	) {
		VIDEO("video"),
		AUDIO("audio"),
	}

	enum class StreamStatus {
		DISABLED,
		ENDED,
		ERROR,
	}

	sealed interface SessionEvent {
		data class StageChanged(
			val stage: SessionStage,
			val status: StageStatus,
			val error: Throwable? = null,
		) : SessionEvent

		data class IssueOccurred(
			val issue: SessionIssue,
		) : SessionEvent

		data class Ended(
			val termination: SessionTermination,
		) : SessionEvent

		data class StreamEnded(
			val stream: MediaStream,
			val status: StreamStatus,
		) : SessionEvent

		data class DisplayRotationChanged(
			val rotation: Int,
		) : SessionEvent
	}

	data class ScrcpyOptions(
		val video: Boolean = true,
		val audio: Boolean = true,
		val requireAudio: Boolean = false,
		val control: Boolean = true,
		val forceAdbForward: Boolean = false,
		val portRange: Server.PortRange = Server.PortRange(27183, 27199),
		val scid: Int = -1,
		val videoBitRate: Int = 8000000,
		val audioBitRate: Int = 128000,
		val maxSize: Int = 0,
		val videoCodec: String = "h264",
		val audioCodec: String = "aac",
		val wmWidth: Int = 0,
		val wmHeight: Int = 0,
		val wmDensity: Int = 0,
	)

	internal class ScrcpyEventDispatcher(
		private val requireAudioEnabled: () -> Boolean,
	) {
		private val sessionEvents = MutableSharedFlow<SessionEvent>(
			extraBufferCapacity = 32,
			onBufferOverflow = BufferOverflow.DROP_OLDEST,
		)

		@Volatile
		private var disconnectedEmitted = false

		private val terminalStatusLock = Any()
		private val terminalStatuses = mutableMapOf<MediaStream, StreamStatus>()

		val serverCallbacks = object : Server.Callbacks {
			override fun onDisconnected(server: Server) {
				dispatchNativeEvent(NativeEvent.ServerDisconnected)
			}
		}

		val controllerCallbacks = object : Controller.Callbacks {
			override fun onEnded(controller: Controller, error: Boolean) {
				dispatchNativeEvent(NativeEvent.ControllerEnded(error))
			}

			override fun onDisplayRotationChanged(controller: Controller, rotation: Int) {
				dispatchNativeEvent(NativeEvent.DisplayRotationChanged(rotation))
			}
		}

		fun observeSessionEvents(): SharedFlow<SessionEvent> = sessionEvents.asSharedFlow()

		fun reset() {
			synchronized(terminalStatusLock) {
				terminalStatuses.clear()
			}
			disconnectedEmitted = false
		}

		fun emitStage(stage: SessionStage, status: StageStatus, error: Throwable? = null) {
			dispatchNativeEvent(NativeEvent.StageChanged(stage, status, error))
		}

		fun reportStreamTermination(stream: MediaStream, status: Demuxer.Status) {
			val streamStatus = status.toStreamStatus()
			synchronized(terminalStatusLock) {
				if (terminalStatuses.containsKey(stream)) {
					return
				}
				terminalStatuses[stream] = streamStatus
			}

			dispatchNativeEvent(NativeEvent.StreamEnded(stream, streamStatus))
		}

		fun emitConnectionLost() {
			if (disconnectedEmitted) {
				return
			}
			disconnectedEmitted = true
			sessionEvents.tryEmit(SessionEvent.Ended(SessionTermination.CONNECTION_LOST))
		}

		private fun dispatchNativeEvent(event: NativeEvent) {
			when (event) {
				is NativeEvent.StageChanged -> {
					sessionEvents.tryEmit(
						SessionEvent.StageChanged(
							stage = event.stage,
							status = event.status,
							error = event.error,
						),
					)
				}

				is NativeEvent.StreamEnded -> {
					sessionEvents.tryEmit(SessionEvent.StreamEnded(event.stream, event.status))

					when {
						shouldEmitStreamIssue(event.stream, event.status) -> {
							sessionEvents.tryEmit(SessionEvent.IssueOccurred(SessionIssue.STREAM))
						}

						shouldEmitConnectionLost(event.stream, event.status) -> {
							emitConnectionLost()
						}
					}
				}

				is NativeEvent.ControllerEnded -> {
					if (event.error) {
						sessionEvents.tryEmit(SessionEvent.IssueOccurred(SessionIssue.CONTROL))
					} else {
						emitConnectionLost()
					}
				}

				is NativeEvent.DisplayRotationChanged -> {
					sessionEvents.tryEmit(SessionEvent.DisplayRotationChanged(event.rotation))
				}

				NativeEvent.ServerDisconnected -> {
					emitConnectionLost()
				}
			}
		}

		private fun shouldEmitStreamIssue(stream: MediaStream, status: StreamStatus): Boolean {
			return when (stream) {
				MediaStream.VIDEO -> status != StreamStatus.ENDED
				MediaStream.AUDIO -> {
					status == StreamStatus.ERROR ||
						(status == StreamStatus.DISABLED && requireAudioEnabled())
				}
			}
		}

		private fun shouldEmitConnectionLost(stream: MediaStream, status: StreamStatus): Boolean {
			if (status != StreamStatus.ENDED) {
				return false
			}

			return stream == MediaStream.VIDEO || stream == MediaStream.AUDIO
		}

		private fun Demuxer.Status.toStreamStatus(): StreamStatus {
			return when (this) {
				Demuxer.Status.DISABLED -> StreamStatus.DISABLED
				Demuxer.Status.EOS -> StreamStatus.ENDED
				Demuxer.Status.ERROR -> StreamStatus.ERROR
			}
		}

		private sealed interface NativeEvent {
			data class StageChanged(
				val stage: SessionStage,
				val status: StageStatus,
				val error: Throwable? = null,
			) : NativeEvent

			data class StreamEnded(
				val stream: MediaStream,
				val status: StreamStatus,
			) : NativeEvent

			data class ControllerEnded(
				val error: Boolean,
			) : NativeEvent

			data class DisplayRotationChanged(
				val rotation: Int,
			) : NativeEvent

			data object ServerDisconnected : NativeEvent
		}
	}

	private companion object {
		private const val TAG = "Scrcpy"
	}
}
