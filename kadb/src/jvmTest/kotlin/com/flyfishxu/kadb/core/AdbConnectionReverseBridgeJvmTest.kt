package com.flyfishxu.kadb.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdbConnectionReverseBridgeJvmTest {

    @Test
    fun parseReverseTcpTargetSupportsPortOnly() {
        val target = parseReverseTcpTarget("tcp:8080")
        assertEquals("127.0.0.1", target?.host)
        assertEquals(8080, target?.port)
    }

    @Test
    fun parseReverseTcpTargetSupportsHostAndPort() {
        val target = parseReverseTcpTarget("tcp:localhost:9000")
        assertEquals("localhost", target?.host)
        assertEquals(9000, target?.port)
    }

    @Test
    fun parseReverseTcpTargetRejectsInvalidInput() {
        assertNull(parseReverseTcpTarget("udp:8080"))
        assertNull(parseReverseTcpTarget("tcp:"))
        assertNull(parseReverseTcpTarget("tcp:abc"))
        assertNull(parseReverseTcpTarget("tcp:localhost:abc"))
        assertNull(parseReverseTcpTarget("tcp:0"))
        assertNull(parseReverseTcpTarget("tcp:70000"))
        assertNull(parseReverseTcpTarget("tcp:a:b:c"))
    }
}
