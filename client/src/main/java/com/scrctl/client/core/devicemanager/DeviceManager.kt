package com.scrctl.client.core.devicemanager

interface DeviceManager {
    fun start()
    fun stop()

    /**
     * Manually trigger reconnect for a device id.
     */
    fun reconnect(deviceId: Long)

    /**
     * Disconnect a device if connected.
     */
    fun disconnect(deviceId: Long)

    /**
     * Execute an adb shell command on an already-connected device.
     * Returns failure if the device is not connected or the command fails.
     */
    suspend fun shell(deviceId: Long, command: String): Result<String>
}
