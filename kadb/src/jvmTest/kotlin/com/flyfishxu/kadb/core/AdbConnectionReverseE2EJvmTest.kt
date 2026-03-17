package com.flyfishxu.kadb.core

import okio.sink
import okio.source
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdbConnectionReverseE2EJvmTest {

    @Test
    fun deviceOpenIsBridgedToLocalTcpAndEchoedBack() {
        val serverSocket = ServerSocket(0)
        val localPort = serverSocket.localPort

        val echoThread = thread(name = "reverse-e2e-echo") {
            serverSocket.accept().use { socket ->
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) return@use
                    output.write(buffer, 0, read)
                    output.flush()
                }
            }
        }

        val deviceToHostInput = PipedInputStream(64 * 1024)
        val deviceToHostOutput = PipedOutputStream(deviceToHostInput)

        val hostToDeviceInput = PipedInputStream(64 * 1024)
        val hostToDeviceOutput = PipedOutputStream(hostToDeviceInput)

        val parserQueue = LinkedBlockingQueue<AdbMessage>()
        val parserThread = thread(name = "reverse-e2e-parser") {
            val input = DataInputStream(hostToDeviceInput)
            while (!Thread.currentThread().isInterrupted) {
                val message = runCatching { readAdbMessage(input) }.getOrNull() ?: return@thread
                parserQueue.offer(message)
            }
        }

        val connectionCloseable = Closeable {
            runCatching { deviceToHostOutput.close() }
            runCatching { deviceToHostInput.close() }
            runCatching { hostToDeviceOutput.close() }
            runCatching { hostToDeviceInput.close() }
            runCatching { serverSocket.close() }
        }

        val adbConnection = AdbConnection(
            adbReader = AdbReader(deviceToHostInput.source()),
            adbWriter = AdbWriter(hostToDeviceOutput.sink()),
            closeable = connectionCloseable,
            supportedFeatures = emptySet(),
            version = AdbProtocol.CONNECT_VERSION,
            maxPayloadSize = 1024,
        )

        try {
            val remoteId = 0x1234
            val openPayload = "tcp:$localPort\u0000".encodeToByteArray()
            writeMessage(deviceToHostOutput, AdbProtocol.CMD_OPEN, remoteId, 0, openPayload)

            val okay = waitFor(parserQueue) {
                it.command == AdbProtocol.CMD_OKAY && it.arg1 == remoteId
            }
            assertNotNull(okay, "Did not receive OKAY for incoming OPEN")
            val localId = okay.arg0

            val ping = "ping-from-device".encodeToByteArray()
            writeMessage(deviceToHostOutput, AdbProtocol.CMD_WRTE, remoteId, localId, ping)

            val echoed = waitFor(parserQueue) {
                it.command == AdbProtocol.CMD_WRTE &&
                    it.arg0 == localId &&
                    it.arg1 == remoteId &&
                    String(it.payload, 0, it.payloadLength) == "ping-from-device"
            }
            assertNotNull(echoed, "Did not receive echoed WRTE from host bridge")

            writeMessage(deviceToHostOutput, AdbProtocol.CMD_CLSE, remoteId, localId, ByteArray(0))

            val close = waitFor(parserQueue) {
                it.command == AdbProtocol.CMD_CLSE && it.arg0 == localId && it.arg1 == remoteId
            }
            assertNotNull(close, "Did not receive CLSE after remote close")
        } finally {
            adbConnection.close()
            parserThread.interrupt()
            echoThread.interrupt()
            parserThread.join(2000)
            echoThread.join(2000)
        }
    }

    private fun waitFor(
        queue: LinkedBlockingQueue<AdbMessage>,
        timeoutMs: Long = 5_000,
        predicate: (AdbMessage) -> Boolean,
    ): AdbMessage? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val item = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (predicate(item)) return item
        }
        return null
    }

    private fun readAdbMessage(input: DataInputStream): AdbMessage {
        val command = readIntLe(input)
        val arg0 = readIntLe(input)
        val arg1 = readIntLe(input)
        val payloadLength = readIntLe(input)
        val checksum = readIntLe(input)
        val magic = readIntLe(input)
        val payload = ByteArray(payloadLength)
        if (payloadLength > 0) {
            input.readFully(payload)
        }
        return AdbMessage(command, arg0, arg1, payloadLength, checksum, magic, payload)
    }

    private fun readIntLe(input: DataInputStream): Int {
        val bytes = ByteArray(4)
        try {
            input.readFully(bytes)
        } catch (_: EOFException) {
            throw EOFException()
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun writeMessage(
        output: PipedOutputStream,
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray,
    ) {
        val checksum = payload.sumOf { it.toUByte().toInt() }
        val buffer = ByteBuffer
            .allocate(24 + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(payload.size)
            .putInt(checksum)
            .putInt(command.inv())
            .put(payload)
            .array()

        output.write(buffer)
        output.flush()
    }
}
