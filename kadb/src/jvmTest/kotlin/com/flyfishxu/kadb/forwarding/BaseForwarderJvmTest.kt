package com.flyfishxu.kadb.forwarding

import com.flyfishxu.kadb.Kadb
import okio.Buffer
import okio.BufferedSink
import okio.Source
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaseForwarderJvmTest {

    @Test
    fun startAndCloseWithoutClientsDoesNotTimeout() {
        val server = QueueForwardingServer()
        val forwarder = TestForwarder(server) { InMemoryDuplex("") }

        forwarder.start()
        forwarder.close()

        assertTrue(server.closed, "Server should be closed")
    }

    @Test
    fun forwardsDataInBothDirectionsAndClosesResources() {
        val server = QueueForwardingServer()
        val remote = InMemoryDuplex("from-remote")
        val client = InMemoryClient("from-client")
        val forwarder = TestForwarder(server) { remote }

        server.enqueue(client)
        forwarder.start()

        assertTrue(client.closed.await(3, TimeUnit.SECONDS), "Client was not closed in time")
        forwarder.close()

        assertEquals("from-client", remote.writtenData())
        assertEquals("from-remote", client.writtenData())
        assertTrue(remote.closed.await(3, TimeUnit.SECONDS), "Remote channel was not closed in time")
    }

    private class TestForwarder(
        private val server: ForwardingServer,
        remoteChannelFactory: (String) -> ForwardingDuplex,
    ) : BaseForwarder(
        kadb = Kadb("127.0.0.1", 65535),
        remoteDestination = "test:destination",
        endpointDescription = "test-endpoint",
        forwardingType = "test",
        remoteChannelFactory = remoteChannelFactory,
    ) {
        override fun createServer(): ForwardingServer = server
    }

    private class QueueForwardingServer : ForwardingServer {
        private val queue = LinkedBlockingQueue<ForwardingClient>()
        @Volatile
        var closed = false

        override fun accept(): ForwardingClient {
            val client = queue.take()
            if (client === CLOSE_SIGNAL) {
                throw SocketException("server closed")
            }
            return client
        }

        fun enqueue(client: ForwardingClient) {
            queue.put(client)
        }

        override fun close() {
            closed = true
            queue.offer(CLOSE_SIGNAL)
        }

        private companion object {
            val CLOSE_SIGNAL = object : ForwardingClient {
                override val source: Source = Buffer()
                override val sink: BufferedSink = Buffer()
                override fun close() = Unit
            }
        }
    }

    private class InMemoryClient(input: String) : ForwardingClient {
        override val source: Source = Buffer().writeUtf8(input)
        override val sink: BufferedSink = Buffer()
        val closed = CountDownLatch(1)

        override fun close() {
            closed.countDown()
        }

        fun writtenData(): String = (sink as Buffer).readUtf8()
    }

    private class InMemoryDuplex(output: String) : ForwardingDuplex {
        override val source: Source = Buffer().writeUtf8(output)
        override val sink: BufferedSink = Buffer()
        val closed = CountDownLatch(1)

        override fun close() {
            closed.countDown()
        }

        fun writtenData(): String = (sink as Buffer).readUtf8()
    }
}
