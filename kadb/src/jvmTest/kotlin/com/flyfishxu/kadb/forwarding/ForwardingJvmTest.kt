package com.flyfishxu.kadb.forwarding

import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import okio.sink
import okio.source
import org.newsclub.net.unix.AFUNIXSocket
import org.newsclub.net.unix.AFUNIXSocketAddress
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForwardingJvmTest {

    @Test
    fun streamForwardStrategyTransfersDataUntilEof() {
        val source = Buffer().writeUtf8("hello-forwarding")
        val sink = Buffer()

        StreamForwardStrategy.transfer(source, sink)

        assertEquals("hello-forwarding", sink.readUtf8())
    }

    @Test
    fun streamForwardStrategyReturnsWhenSourceThrowsIOException() {
        val sink = Buffer()

        StreamForwardStrategy.transfer(AlwaysFailingSource(), sink)

        assertEquals(0, sink.size)
    }

    @Test
    fun localAbstractServerAcceptsConnectionAndTransfersData() {
        val socketName = "kadb-forwarding-test-${System.nanoTime()}"
        val server = LocalAbstractServer(socketName)
        val accepted = CountDownLatch(1)

        var serverReceived: String? = null

        val serverThread = thread {
            val client = server.accept()
            try {
                serverReceived = client.source.buffer().readUtf8LineStrict()
                client.sink.writeUtf8("pong\n")
                client.sink.flush()
            } finally {
                runCatching { client.close() }
                accepted.countDown()
            }
        }

        val socket = AFUNIXSocket.newInstance()
        try {
            socket.connect(AFUNIXSocketAddress.inAbstractNamespace(socketName))

            val source = socket.inputStream.source().buffer()
            val sink = socket.outputStream.sink().buffer()

            sink.writeUtf8("ping\n")
            sink.flush()

            val response = source.readUtf8LineStrict()
            assertEquals("pong", response)
            assertEquals("ping", serverReceived)
            assertTrue(accepted.await(3, TimeUnit.SECONDS), "Server thread did not finish in time")
        } finally {
            runCatching { socket.close() }
            runCatching { server.close() }
            serverThread.join(3000)
        }
    }

    private class AlwaysFailingSource : Source {
        override fun read(sink: Buffer, byteCount: Long): Long {
            throw IOException("boom")
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }
}
