package com.scrctl.client.core.scrcpy

import android.util.Log
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class Demuxer(
	private val name: String,
	private val inputStream: InputStream,
	private val closeable: AutoCloseable,
) : AutoCloseable {

	enum class Status {
		DISABLED,
		EOS,
		ERROR,
	}

	enum class CodecId {
		H264,
		H265,
		AV1,
		OPUS,
		AAC,
		FLAC,
		RAW,
	}

	data class StreamConfig(
		val codecId: CodecId,
		val width: Int? = null,
		val height: Int? = null,
		val sampleRate: Int = AUDIO_SAMPLE_RATE,
		val channelCount: Int = AUDIO_CHANNEL_COUNT,
	)

	data class Packet(
		val data: ByteArray,
		val ptsUs: Long?,
		val isKeyFrame: Boolean,
		val isConfig: Boolean,
	)

	interface PacketSink {
		fun open(demuxer: Demuxer, config: StreamConfig): Boolean = true

		fun close(demuxer: Demuxer) = Unit

		fun push(demuxer: Demuxer, packet: Packet): Boolean

		fun disable(demuxer: Demuxer) = Unit
	}

	private val closed = AtomicBoolean(false)
	private val thread = Thread({ runDemuxer() }, "scrctl-demuxer-$name")
	private val sinksLock = Any()
	private val sinks = mutableListOf<PacketSink>()

	fun addSink(sink: PacketSink) {
		synchronized(sinksLock) {
			if (!sinks.contains(sink)) {
				sinks += sink
			}
		}
	}

	fun removeSink(sink: PacketSink) {
		synchronized(sinksLock) {
			sinks.remove(sink)
		}
	}

	fun start(): Boolean {
		return try {
			Log.d(TAG, "Starting demuxer '$name'")
			thread.start()
			true
		} catch (t: Throwable) {
			Log.e(TAG, "Failed to start demuxer '$name'", t)
			false
		}
	}

	fun join() {
		runCatching { thread.join() }
	}

	override fun close() {
		if (!closed.compareAndSet(false, true)) {
			return
		}
		Log.d(TAG, "Closing demuxer '$name'")
		runCatching { closeable.close() }
		runCatching { thread.interrupt() }
	}

	private fun runDemuxer() {
		var status = Status.ERROR
		val input = DataInputStream(BufferedInputStream(inputStream))
		var sinksOpened = false

		try {
			Log.d(TAG, "Demuxer '$name' loop started")
			val rawCodecId = recvCodecId(input)
				?: run {
					Log.e(TAG, "Demuxer '$name': failed to read codec id")
					status = Status.ERROR
					return
				}

			if (rawCodecId == CODEC_DISABLED) {
				Log.i(TAG, "Demuxer '$name': stream disabled by server")
				disableSinks()
				status = Status.DISABLED
				return
			}

			if (rawCodecId == CODEC_ERROR) {
				Log.e(TAG, "Demuxer '$name': server reported stream error")
				status = Status.ERROR
				return
			}

			val codecId = toCodecId(rawCodecId)
				?: run {
					Log.e(TAG, "Demuxer '$name': unsupported codec id=$rawCodecId")
					disableSinks()
					status = Status.ERROR
					return
				}

			val config = if (codecId.isVideo()) {
				val size = recvVideoSize(input) ?: run {
					Log.e(TAG, "Demuxer '$name': failed to read video size")
					status = Status.ERROR
					return
				}
				StreamConfig(codecId = codecId, width = size.first, height = size.second)
			} else {
				StreamConfig(codecId = codecId)
			}
			Log.i(TAG, "Demuxer '$name' opened with codec=$codecId${if (config.width != null && config.height != null) ", size=${config.width}x${config.height}" else ""}")


			if (!openSinks(config)) {
				Log.e(TAG, "Demuxer '$name': failed to open sinks")
				status = Status.ERROR
				return
			}
			sinksOpened = true

			val mustMergeConfigPacket = codecId == CodecId.H264 || codecId == CodecId.H265
			var pendingConfig: ByteArray? = null

			while (!closed.get()) {
				val packet = recvPacket(input)
					?: run {
						status = Status.EOS
						break
					}

				val outPacket = if (mustMergeConfigPacket) {
					if (packet.isConfig) {
						pendingConfig = packet.data
						null
					} else if (pendingConfig != null) {
						val merged = ByteArray(pendingConfig!!.size + packet.data.size)
						System.arraycopy(pendingConfig!!, 0, merged, 0, pendingConfig!!.size)
						System.arraycopy(packet.data, 0, merged, pendingConfig!!.size, packet.data.size)
						pendingConfig = null
						packet.copy(data = merged)
					} else {
						packet
					}
				} else {
					packet
				}

				if (outPacket != null) {
					val ok = pushToSinks(outPacket)
					if (!ok) {
						Log.e(TAG, "Demuxer '$name': sink rejected packet")
						status = Status.ERROR
						break
					}
				}
			}
		} catch (t: Throwable) {
			Log.e(TAG, "Demuxer '$name' terminated unexpectedly", t)
			status = if (closed.get()) Status.EOS else Status.ERROR
		} finally {
			Log.i(TAG, "Demuxer '$name' finished with status=$status")
			if (sinksOpened) {
				closeSinks()
			}
			runCatching { input.close() }
		}
	}

	private fun openSinks(config: StreamConfig): Boolean {
		val opened = ArrayList<PacketSink>()
		for (sink in snapshotSinks()) {
			val ok = runCatching { sink.open(this, config) }.getOrDefault(false)
			if (!ok) {
				Log.e(TAG, "Demuxer '$name': sink open failed")
				for (index in opened.indices.reversed()) {
					runCatching { opened[index].close(this) }
				}
				return false
			}
			opened += sink
		}

		return true
	}

	private fun closeSinks() {
		val currentSinks = snapshotSinks()
		for (index in currentSinks.indices.reversed()) {
			runCatching { currentSinks[index].close(this) }
		}
	}

	private fun pushToSinks(packet: Packet): Boolean {
		for (sink in snapshotSinks()) {
			val ok = runCatching { sink.push(this, packet) }.getOrDefault(false)
			if (!ok) {
				return false
			}
		}

		return true
	}

	private fun disableSinks() {
		for (sink in snapshotSinks()) {
			runCatching { sink.disable(this) }
		}
	}

	private fun snapshotSinks(): List<PacketSink> {
		return synchronized(sinksLock) { sinks.toList() }
	}

	private fun recvCodecId(input: DataInputStream): Int? {
		return try {
			input.readInt()
		} catch (_: EOFException) {
			null
		} catch (_: IOException) {
			null
		}
	}

	private fun recvVideoSize(input: DataInputStream): Pair<Int, Int>? {
		return try {
			val width = input.readInt()
			val height = input.readInt()
			width to height
		} catch (_: EOFException) {
			null
		} catch (_: IOException) {
			null
		}
	}

	private fun recvPacket(input: DataInputStream): Packet? {
		val header = ByteArray(PACKET_HEADER_SIZE)
		try {
			input.readFully(header)
		} catch (_: EOFException) {
			return null
		} catch (_: IOException) {
			return null
		}

		val ptsFlags = readLongBE(header, 0)
		val len = readIntBE(header, 8)
		if (len <= 0) {
			return null
		}

		val payload = ByteArray(len)
		try {
			input.readFully(payload)
		} catch (_: EOFException) {
			return null
		} catch (_: IOException) {
			return null
		}

		val isConfig = (ptsFlags and PACKET_FLAG_CONFIG) != 0L
		val isKeyFrame = (ptsFlags and PACKET_FLAG_KEY_FRAME) != 0L
		val pts = if (isConfig) null else (ptsFlags and PACKET_PTS_MASK)

		return Packet(
			data = payload,
			ptsUs = pts,
			isKeyFrame = isKeyFrame,
			isConfig = isConfig,
		)
	}

	private fun toCodecId(rawCodecId: Int): CodecId? {
		return when (rawCodecId) {
			CODEC_H264 -> CodecId.H264
			CODEC_H265 -> CodecId.H265
			CODEC_AV1 -> CodecId.AV1
			CODEC_OPUS -> CodecId.OPUS
			CODEC_AAC -> CodecId.AAC
			CODEC_FLAC -> CodecId.FLAC
			CODEC_RAW -> CodecId.RAW
			else -> null
		}
	}

	private fun CodecId.isVideo(): Boolean {
		return this == CodecId.H264 || this == CodecId.H265 || this == CodecId.AV1
	}

	private fun readIntBE(buffer: ByteArray, offset: Int): Int {
		return ((buffer[offset].toInt() and 0xFF) shl 24) or
			((buffer[offset + 1].toInt() and 0xFF) shl 16) or
			((buffer[offset + 2].toInt() and 0xFF) shl 8) or
			(buffer[offset + 3].toInt() and 0xFF)
	}

	private fun readLongBE(buffer: ByteArray, offset: Int): Long {
		return ((buffer[offset].toLong() and 0xFF) shl 56) or
			((buffer[offset + 1].toLong() and 0xFF) shl 48) or
			((buffer[offset + 2].toLong() and 0xFF) shl 40) or
			((buffer[offset + 3].toLong() and 0xFF) shl 32) or
			((buffer[offset + 4].toLong() and 0xFF) shl 24) or
			((buffer[offset + 5].toLong() and 0xFF) shl 16) or
			((buffer[offset + 6].toLong() and 0xFF) shl 8) or
			(buffer[offset + 7].toLong() and 0xFF)
	}

	companion object {
		private const val TAG = "ScrcpyDemuxer"

		const val PACKET_HEADER_SIZE = 12

		const val PACKET_FLAG_CONFIG = 1L shl 63
		const val PACKET_FLAG_KEY_FRAME = 1L shl 62
		const val PACKET_PTS_MASK = PACKET_FLAG_KEY_FRAME - 1

		const val CODEC_DISABLED = 0
		const val CODEC_ERROR = 1
		const val CODEC_H264 = 0x68323634
		const val CODEC_H265 = 0x68323635
		const val CODEC_AV1 = 0x00617631
		const val CODEC_OPUS = 0x6f707573
		const val CODEC_AAC = 0x00616163
		const val CODEC_FLAC = 0x666c6163
		const val CODEC_RAW = 0x00726177

		const val AUDIO_SAMPLE_RATE = 48_000
		const val AUDIO_CHANNEL_COUNT = 2
	}
}

