package com.flyfishxu.kadb.forwarding

import com.flyfishxu.kadb.Kadb
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class ScrcpyAgentForwardingIntegrationTest {

    @Ignore("Requires real device 10.86.6.4:5555 with scrcpy-agent listening on localabstract:scrcpy")
    @Test
    fun mapScrcpyAgentToLocal8080() {
        Kadb.create(
            host = "10.86.6.4",
            port = 5555,
            connectTimeout = 3000,
            socketTimeout = 3000,
        ).use { kadb ->
            kadb.tcpForward(
                hostPort = 8080,
                remote = "localabstract:scrcpy",
            ).use {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", 8080), 2000)
                    assertTrue(socket.isConnected)
                }
            }
        }
    }
}
