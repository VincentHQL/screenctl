package com.scrctl.desktop.system

import com.scrctl.desktop.model.SystemStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import oshi.SystemInfo
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class HostSystemStatsSampler(
	private val versionLabel: String = "v4.8.5-stable",
	private val healthLabel: String = "系统运行正常",
) {
	private val systemInfo: SystemInfo = SystemInfo()
	private val hardware = systemInfo.hardware
	private val processor = hardware.processor
	private val networkIfs = hardware.networkIFs

	private var prevCpuTicks: LongArray = processor.systemCpuLoadTicks
	private var prevSentBytes: Long = 0L
	private var prevRecvBytes: Long = 0L
	private var prevSampleTimeMs: Long = 0L
	private var netInitialized: Boolean = false

	fun statsFlow(pollIntervalMs: Long = 1000L): Flow<SystemStats> = flow {
		while (kotlinx.coroutines.currentCoroutineContext().isActive) {
			val nowMs = System.currentTimeMillis()

			val cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks)
			prevCpuTicks = processor.systemCpuLoadTicks
			val cpuPercent = (cpuLoad * 100.0).coerceIn(0.0, 100.0).roundToInt()

			// Sum bytes across non-loopback interfaces.
			var sentBytes = 0L
			var recvBytes = 0L
			for (net in networkIfs) {
				net.updateAttributes()
				val name = (net.name ?: "").lowercase()
				val display = (net.displayName ?: "").lowercase()
				if (name == "lo" || name.startsWith("lo") || display.contains("loopback")) continue
				sentBytes += net.bytesSent
				recvBytes += net.bytesRecv
			}

			val (uploadMBps, downloadMBps) = if (!netInitialized) {
				netInitialized = true
				prevSentBytes = sentBytes
				prevRecvBytes = recvBytes
				prevSampleTimeMs = nowMs
				0.0 to 0.0
			} else {
				val elapsedMs = (nowMs - prevSampleTimeMs).coerceAtLeast(1L)
				val elapsedSeconds = elapsedMs / 1000.0

				val upBytesDelta = (sentBytes - prevSentBytes).coerceAtLeast(0L)
				val downBytesDelta = (recvBytes - prevRecvBytes).coerceAtLeast(0L)

				prevSentBytes = sentBytes
				prevRecvBytes = recvBytes
				prevSampleTimeMs = nowMs

				val upload = upBytesDelta / elapsedSeconds / (1024.0 * 1024.0)
				val download = downBytesDelta / elapsedSeconds / (1024.0 * 1024.0)
				upload to download
			}

			emit(
				SystemStats(
					cpuPercent = cpuPercent,
					uploadMbps = uploadMBps,
					downloadMbps = downloadMBps,
					version = versionLabel,
					healthLabel = healthLabel,
				),
			)

			delay(pollIntervalMs.milliseconds)
		}
	}
		.flowOn(Dispatchers.Default)
}
