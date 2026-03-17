package com.flyfishxu.kadb.reverse

import com.flyfishxu.kadb.Kadb
import kotlin.test.Test
import kotlin.test.assertTrue

class AdbReverseDeviceIntegrationTest {

    @Test
    fun reverseForwardListAndKillOnRealDevice() {
        if (System.getenv("KADB_RUN_REAL_DEVICE_TESTS") != "1") {
            return
        }

        val host = System.getenv("KADB_DEVICE_HOST") ?: "10.86.6.4"
        val port = (System.getenv("KADB_DEVICE_PORT") ?: "5555").toInt()

        val device = "tcp:27183"
        val hostSide = "tcp:8080"

        Kadb.create(host, port, connectTimeout = 5000, socketTimeout = 5000).use { kadb ->
            runCatching { kadb.reverseKillForward(device) }

            kadb.reverseForward(device, hostSide)
            val rulesAfterCreate = kadb.reverseListForwards()
            println("[reverse] rulesAfterCreate=$rulesAfterCreate")
            assertTrue(
                rulesAfterCreate.any { it.device == device && it.host == hostSide },
                "Reverse rule was not found after create. Rules: $rulesAfterCreate"
            )

            kadb.reverseKillForward(device)
            val rulesAfterKill = kadb.reverseListForwards()
            println("[reverse] rulesAfterKill=$rulesAfterKill")
            assertTrue(
                rulesAfterKill.none { it.device == device && it.host == hostSide },
                "Reverse rule still exists after kill. Rules: $rulesAfterKill"
            )
        }
    }
}
