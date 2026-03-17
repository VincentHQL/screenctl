package com.scrctl.client.core.scrcpy

import java.io.InputStream
import java.io.OutputStream

data class AgentChannel(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val closeable: AutoCloseable,
) : AutoCloseable {
    override fun close() {
        closeable.close()
    }
}
