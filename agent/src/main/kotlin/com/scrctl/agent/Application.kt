package com.scrctl.agent

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.time.Duration.Companion.seconds

/**
 * WebRTC signaling server.
 *
 * Originally written by Artem Bagritsevich.
 * https://github.com/artem-bagritsevich/WebRTCKtorSignalingServerExample
 */
fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        get("/") {
            call.respondText("Hello from WebRTC signaling server")
        }

        webSocket("/rtc") {
            val sessionID = java.util.UUID.randomUUID()
            try {
                SessionManager.onSessionStarted(sessionID, this)

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            SessionManager.onMessage(sessionID, frame.readText())
                        }
                        else -> Unit
                    }
                }
                println("Exiting incoming loop, closing session: $sessionID")
                SessionManager.onSessionClose(sessionID)
            } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                println("onClose $sessionID")
                SessionManager.onSessionClose(sessionID)
            } catch (e: Throwable) {
                println("onError $sessionID $e")
                SessionManager.onSessionClose(sessionID)
            }
        }
    }
}
