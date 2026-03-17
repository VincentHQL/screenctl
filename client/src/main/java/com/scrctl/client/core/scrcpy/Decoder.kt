package com.scrctl.client.core.scrcpy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import kotlin.math.min

class Decoder(
	private val name: String = "media",
	private val callbacks: Callbacks = Callbacks.NONE,
) : Demuxer.PacketSink, AutoCloseable {

	data class AudioPlaybackConfig(
		val usage: Int = AudioAttributes.USAGE_MEDIA,
		val contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
		val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
		val channelMask: Int? = null,
		val bufferSizeBytes: Int? = null,
		val audioSessionId: Int = AudioManager.AUDIO_SESSION_ID_GENERATE,
	)

	interface Callbacks {
		fun getVideoSurface(decoder: Decoder): Surface? = null

		fun getAudioPlaybackConfig(decoder: Decoder, config: Demuxer.StreamConfig): AudioPlaybackConfig? = AudioPlaybackConfig()

		companion object {
			val NONE = object : Callbacks {}
		}
	}

	private enum class Mode {
		VIDEO,
		AUDIO,
	}

	private val lock = Any()

	@Volatile
	private var mode: Mode? = null

	@Volatile
	private var codec: Demuxer.CodecId? = null

	@Volatile
	private var decoder: MediaCodec? = null

	@Volatile
	private var mime: String? = null

	@Volatile
	private var width: Int = 0

	@Volatile
	private var height: Int = 0

	@Volatile
	private var sampleRate: Int = Demuxer.AUDIO_SAMPLE_RATE

	@Volatile
	private var channelCount: Int = Demuxer.AUDIO_CHANNEL_COUNT

	@Volatile
	private var boundSurface: Surface? = null

	@Volatile
	private var audioConfig: AudioPlaybackConfig? = null

	@Volatile
	private var track: AudioTrack? = null

	@Volatile
	private var playerRunning: Boolean = false

	private var playerThread: Thread? = null
	private val pcmBuffer = PcmRingBuffer(MAX_BUFFER_BYTES)
	private var playbackStarted = false

	override fun open(demuxer: Demuxer, config: Demuxer.StreamConfig): Boolean {
		val nextMode = if (config.codecId.isVideo()) Mode.VIDEO else Mode.AUDIO
		val nextMime = when (config.codecId) {
			Demuxer.CodecId.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
			Demuxer.CodecId.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
			Demuxer.CodecId.AV1 -> MediaFormat.MIMETYPE_VIDEO_AV1
			Demuxer.CodecId.OPUS -> MediaFormat.MIMETYPE_AUDIO_OPUS
			Demuxer.CodecId.AAC -> MediaFormat.MIMETYPE_AUDIO_AAC
			Demuxer.CodecId.FLAC -> MediaFormat.MIMETYPE_AUDIO_FLAC
			Demuxer.CodecId.RAW -> null
		}

		if (nextMode == Mode.VIDEO) {
			val nextWidth = config.width ?: 0
			val nextHeight = config.height ?: 0
			if (nextWidth <= 0 || nextHeight <= 0) {
				Log.w(TAG, "Decoder '$name': invalid size ${nextWidth}x$nextHeight")
				return false
			}

			synchronized(lock) {
				val changed = mode != nextMode || mime != nextMime || width != nextWidth || height != nextHeight
				mode = nextMode
				codec = config.codecId
				mime = nextMime
				width = nextWidth
				height = nextHeight
				if (changed) {
					releaseDecoderLocked()
				}
			}

			return true
		}

		val nextAudioConfig = callbacks.getAudioPlaybackConfig(this, config)
		synchronized(lock) {
			val changed = mode != nextMode || codec != config.codecId || mime != nextMime || sampleRate != config.sampleRate
				|| channelCount != config.channelCount || audioConfig != nextAudioConfig
			mode = nextMode
			codec = config.codecId
			mime = nextMime
			sampleRate = config.sampleRate
			channelCount = config.channelCount
			audioConfig = nextAudioConfig
			if (changed) {
				releaseDecoderLocked()
				releaseTrackLocked()
			}
		}

		return true
	}

	fun invalidateOutput() {
		synchronized(lock) {
			when (mode) {
				Mode.VIDEO -> releaseDecoderLocked()
				Mode.AUDIO -> {
					releaseDecoderLocked()
					releaseTrackLocked()
				}
				null -> Unit
			}
		}
	}

	override fun push(demuxer: Demuxer, packet: Demuxer.Packet): Boolean {
		return when (mode) {
			Mode.VIDEO -> pushVideo(packet)
			Mode.AUDIO -> pushAudio(packet)
			null -> true
		}
	}

	override fun close(demuxer: Demuxer) {
		close()
	}

	override fun disable(demuxer: Demuxer) {
		close()
	}

	override fun close() {
		synchronized(lock) {
			releaseDecoderLocked()
			releaseTrackLocked()
		}
	}

	private fun pushVideo(packet: Demuxer.Packet): Boolean {
		if (packet.isConfig) {
			return true
		}

		val localDecoder = obtainVideoDecoder() ?: return true

		try {
			val inputIndex = localDecoder.dequeueInputBuffer(TIMEOUT_US)
			if (inputIndex >= 0) {
				val inputBuffer = localDecoder.getInputBuffer(inputIndex) ?: return true
				inputBuffer.clear()
				inputBuffer.put(packet.data)
				val flags = if (packet.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
				localDecoder.queueInputBuffer(inputIndex, 0, packet.data.size, packet.ptsUs ?: 0L, flags)
			}

			drainVideo(localDecoder)
			return true
		} catch (e: Throwable) {
			Log.e(TAG, "Decoder '$name': could not decode video packet", e)
			return false
		}
	}

	private fun pushAudio(packet: Demuxer.Packet): Boolean {
		val localCodec = codec ?: return true

		if (localCodec == Demuxer.CodecId.RAW) {
			enqueuePcm(packet.data)
			return true
		}

		val localMime = mime ?: return true

		if (packet.isConfig) {
			return configureAudioDecoder(localMime, localCodec, packet.data)
		}

		val localDecoder = synchronized(lock) { decoder } ?: return true
		try {
			val inputIndex = localDecoder.dequeueInputBuffer(TIMEOUT_US)
			if (inputIndex >= 0) {
				val inputBuffer = localDecoder.getInputBuffer(inputIndex) ?: return true
				inputBuffer.clear()
				inputBuffer.put(packet.data)
				localDecoder.queueInputBuffer(inputIndex, 0, packet.data.size, packet.ptsUs ?: 0L, 0)
			}

			drainAudio(localDecoder)
			return true
		} catch (e: Throwable) {
			synchronized(lock) {
				if (decoder === localDecoder) {
					releaseDecoderLocked()
				}
			}
			Log.e(TAG, "Decoder '$name': could not decode audio packet", e)
			return false
		}
	}

	private fun obtainVideoDecoder(): MediaCodec? {
		synchronized(lock) {
			val localMime = mime ?: return null
			if (width <= 0 || height <= 0) {
				return null
			}

			val surface = callbacks.getVideoSurface(this)
			if (surface == null) {
				boundSurface = null
				releaseDecoderLocked()
				return null
			}

			if (boundSurface !== surface) {
				boundSurface = surface
				releaseDecoderLocked()
			}

			if (decoder == null) {
				try {
					val created = MediaCodec.createDecoderByType(localMime)
					val format = MediaFormat.createVideoFormat(localMime, width, height)
					created.configure(format, surface, null, 0)
					created.start()
					decoder = created
				} catch (e: Throwable) {
					Log.e(TAG, "Decoder '$name': could not open video codec", e)
					return null
				}
			}

			return decoder
		}
	}

	private fun configureAudioDecoder(mime: String, codec: Demuxer.CodecId, csd0: ByteArray): Boolean {
		synchronized(lock) {
			releaseDecoderLocked()

			return try {
				val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount)
				format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(csd0))
				if (codec == Demuxer.CodecId.AAC) {
					format.setInteger(MediaFormat.KEY_IS_ADTS, 0)
				}

				val created = MediaCodec.createDecoderByType(mime)
				created.configure(format, null, null, 0)
				created.start()
				decoder = created
				true
			} catch (e: Throwable) {
				Log.e(TAG, "Decoder '$name': could not configure audio codec", e)
				false
			}
		}
	}

	private fun drainVideo(localDecoder: MediaCodec) {
		val info = MediaCodec.BufferInfo()
		while (true) {
			when (val outputIndex = localDecoder.dequeueOutputBuffer(info, 0)) {
				MediaCodec.INFO_TRY_AGAIN_LATER,
				MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> break
				else -> {
					if (outputIndex >= 0) {
						localDecoder.releaseOutputBuffer(outputIndex, true)
					} else {
						break
					}
				}
			}
		}
	}

	private fun drainAudio(localDecoder: MediaCodec) {
		ensureTrack() ?: return
		val info = MediaCodec.BufferInfo()
		while (true) {
			val outputIndex = localDecoder.dequeueOutputBuffer(info, 0)
			if (outputIndex >= 0) {
				val outputBuffer = localDecoder.getOutputBuffer(outputIndex)
				if (outputBuffer != null && info.size > 0) {
					val bytes = ByteArray(info.size)
					outputBuffer.position(info.offset)
					outputBuffer.limit(info.offset + info.size)
					outputBuffer.get(bytes)
					enqueuePcm(bytes)
				}
				localDecoder.releaseOutputBuffer(outputIndex, false)
			} else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				continue
			} else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
				break
			} else {
				break
			}
		}
	}

	private fun enqueuePcm(bytes: ByteArray) {
		ensureTrack() ?: return
		synchronized(lock) {
			pcmBuffer.writeDropOldest(bytes, 0, bytes.size)
		}
	}

	private fun ensureTrack(): AudioTrack? {
		val current = synchronized(lock) { track }
		if (current != null) {
			return current
		}

		synchronized(lock) {
			val existing = track
			if (existing != null) {
				return existing
			}

			val config = audioConfig ?: return null
			val channelMask = config.channelMask ?: defaultChannelMask(channelCount) ?: run {
				Log.w(TAG, "Decoder '$name': unsupported audio channel count $channelCount")
				return null
			}

			val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, config.encoding)
				.coerceAtLeast(8192)
			val bufferSize = config.bufferSizeBytes?.coerceAtLeast(minBuffer) ?: minBuffer

			return try {
				val created = AudioTrack(
					AudioAttributes.Builder()
						.setUsage(config.usage)
						.setContentType(config.contentType)
						.build(),
					AudioFormat.Builder()
						.setEncoding(config.encoding)
						.setSampleRate(sampleRate)
						.setChannelMask(channelMask)
						.build(),
					bufferSize,
					AudioTrack.MODE_STREAM,
					config.audioSessionId,
				)
				created.play()
				track = created
				startPlayerIfNeededLocked()
				created
			} catch (e: Throwable) {
				Log.e(TAG, "Decoder '$name': could not create audio track", e)
				null
			}
		}
	}

	private fun startPlayerIfNeededLocked() {
		if (playerRunning) {
			return
		}
		playerRunning = true
		playbackStarted = false
		playerThread = Thread({ runPlayerLoop() }, "scrctl-$name-player").also { it.start() }
	}

	private fun runPlayerLoop() {
		val chunk = ByteArray(CHUNK_BYTES)
		while (playerRunning) {
			val localTrack = synchronized(lock) { track } ?: break

			val readBytes = synchronized(lock) {
				if (!playbackStarted) {
					if (pcmBuffer.availableRead() < TARGET_BUFFER_BYTES) {
						0
					} else {
						playbackStarted = true
						pcmBuffer.read(chunk, 0, CHUNK_BYTES)
					}
				} else {
					pcmBuffer.read(chunk, 0, CHUNK_BYTES)
				}
			}

			if (readBytes < CHUNK_BYTES) {
				chunk.fill(0, readBytes, CHUNK_BYTES)
			}

			runCatching { localTrack.write(chunk, 0, CHUNK_BYTES) }
				.onFailure { break }
		}
	}

	private fun releaseDecoderLocked() {
		val localDecoder = decoder ?: return
		decoder = null
		runCatching { localDecoder.stop() }
		runCatching { localDecoder.release() }
	}

	private fun releaseTrackLocked() {
		playerRunning = false
		playbackStarted = false
		pcmBuffer.clear()
		val player = playerThread
		playerThread = null
		runCatching { player?.join(200) }

		val localTrack = track ?: return
		track = null
		runCatching { localTrack.stop() }
		runCatching { localTrack.release() }
	}

	private fun Demuxer.CodecId.isVideo(): Boolean {
		return this == Demuxer.CodecId.H264 || this == Demuxer.CodecId.H265 || this == Demuxer.CodecId.AV1
	}

	private fun defaultChannelMask(channelCount: Int): Int? {
		return when (channelCount) {
			1 -> AudioFormat.CHANNEL_OUT_MONO
			2 -> AudioFormat.CHANNEL_OUT_STEREO
			else -> null
		}
	}

	private class PcmRingBuffer(capacity: Int) {
		private val data = ByteArray(capacity)
		private var readPos = 0
		private var writePos = 0
		private var size = 0

		fun clear() {
			readPos = 0
			writePos = 0
			size = 0
		}

		fun availableRead(): Int = size

		fun writeDropOldest(src: ByteArray, offset: Int, length: Int) {
			if (length <= 0) {
				return
			}

			var srcOffset = offset
			var writeLength = length

			if (writeLength >= data.size) {
				srcOffset = offset + (writeLength - data.size)
				writeLength = data.size
				clear()
			}

			val required = size + writeLength - data.size
			if (required > 0) {
				read(null, 0, required)
			}

			var remaining = writeLength
			var currentOffset = srcOffset
			while (remaining > 0) {
				val chunk = min(remaining, data.size - writePos)
				System.arraycopy(src, currentOffset, data, writePos, chunk)
				writePos = (writePos + chunk) % data.size
				size += chunk
				currentOffset += chunk
				remaining -= chunk
			}
		}

		fun read(dst: ByteArray?, offset: Int, length: Int): Int {
			if (length <= 0 || size <= 0) {
				return 0
			}

			var remaining = min(length, size)
			var outOffset = offset
			val readTotal = remaining
			while (remaining > 0) {
				val chunk = min(remaining, data.size - readPos)
				if (dst != null) {
					System.arraycopy(data, readPos, dst, outOffset, chunk)
					outOffset += chunk
				}
				readPos = (readPos + chunk) % data.size
				size -= chunk
				remaining -= chunk
			}

			return readTotal
		}
	}

	private companion object {
		const val TAG = "ScrcpyDecoder"
		const val TIMEOUT_US = 10_000L
		const val PCM_SAMPLE_SIZE_BYTES = 4
		const val CHUNK_MS = 20
		const val TARGET_BUFFER_MS = 80
		const val MAX_BUFFER_MS = 400
		const val CHUNK_BYTES = Demuxer.AUDIO_SAMPLE_RATE * PCM_SAMPLE_SIZE_BYTES * CHUNK_MS / 1000
		const val TARGET_BUFFER_BYTES = Demuxer.AUDIO_SAMPLE_RATE * PCM_SAMPLE_SIZE_BYTES * TARGET_BUFFER_MS / 1000
		const val MAX_BUFFER_BYTES = Demuxer.AUDIO_SAMPLE_RATE * PCM_SAMPLE_SIZE_BYTES * MAX_BUFFER_MS / 1000
	}
}

