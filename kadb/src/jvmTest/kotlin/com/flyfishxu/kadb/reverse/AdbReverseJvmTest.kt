package com.flyfishxu.kadb.reverse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdbReverseJvmTest {

    @Test
    fun buildForwardDestinationFormatsCorrectly() {
        val destination = buildReverseForwardDestination("tcp:8080", "localabstract:scrcpy")
        assertEquals("reverse:forward:tcp:8080;localabstract:scrcpy", destination)
    }

    @Test
    fun buildForwardDestinationWithNoRebindFormatsCorrectly() {
        val destination = buildReverseForwardDestination("tcp:8080", "tcp:9000", noRebind = true)
        assertEquals("reverse:forward:norebind:tcp:8080;tcp:9000", destination)
    }

    @Test
    fun buildKillDestinationFormatsCorrectly() {
        val destination = buildReverseKillDestination("tcp:8080")
        assertEquals("reverse:killforward:tcp:8080", destination)
    }

    @Test
    fun parseListOutputSupportsStandardFormats() {
        val output = """
            tcp:8080 localabstract:scrcpy
            emulator-5554 tcp:7001 tcp:9001
            malformed-line
        """.trimIndent()

        val result = parseReverseListOutput(output)

        assertEquals(
            listOf(
                AdbReverseRule(device = "tcp:8080", host = "localabstract:scrcpy"),
                AdbReverseRule(device = "tcp:7001", host = "tcp:9001"),
            ),
            result,
        )
    }

    @Test
    fun buildForwardDestinationRejectsBlankArgs() {
        assertFailsWith<IllegalArgumentException> {
            buildReverseForwardDestination("", "tcp:7001")
        }
        assertFailsWith<IllegalArgumentException> {
            buildReverseForwardDestination("tcp:7001", "")
        }
    }

    @Test
    fun parseSmartSocketResponseHandlesOkayWithProtocolString() {
        val response = parseSmartSocketResponse("OKAY0012tcp:27183 tcp:8080")
        assertEquals("tcp:27183 tcp:8080", response.payload)
    }

    @Test
    fun parseSmartSocketResponseHandlesFail() {
        val response = parseSmartSocketResponse("FAIL0012listener not found")
        assertTrue(response.payload.contains("listener not found"))
    }

    @Test
    fun parseSmartSocketResponseHandlesRawProtocolStringWithoutStatus() {
        val response = parseSmartSocketResponse("0012tcp:27183 tcp:8080")
        assertEquals("tcp:27183 tcp:8080", response.payload)
    }
}
