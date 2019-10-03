/*
 * Copyright 2018-2019 SIP3.IO, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sip3.captain.ce.socket

import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.SdpSession
import io.vertx.core.AbstractVerticle
import io.vertx.core.datagram.DatagramSocket
import io.vertx.core.json.JsonObject
import mu.KotlinLogging
import java.net.URI

/**
 * Management socket
 */
class ManagementSocket : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    companion object {

        const val TYPE_SDP_SESSION = "sdp_session"
        const val TYPE_REGISTER = "register"
    }

    private var schema = "udp"
    private lateinit var localUri: URI
    private lateinit var remoteUri: URI
    private var registerDelay: Long = 60000

    lateinit var socket: DatagramSocket

    override fun start() {
        config().getJsonObject("management").let { config ->
            config.getString("schema")?.let { schemaValue ->
                schema = schemaValue
                // TODO: Let's use Kotlin `require()` - looks nice IMO
                if (schema != "udp") {
                    throw IllegalArgumentException("Unknown schema: '$schema'")
                }
            }

            config.getString("local-host")?.let { localHost ->
                localUri = URI("$schema://$localHost")
                // TODO: Let's use Kotlin `require()` - looks nice IMO
                if (localUri.port == -1 || localUri.host == null) {
                    throw IllegalArgumentException("local-host")
                }
            }

            config.getString("remote-host")?.let { remoteHost ->
                remoteUri = URI("$schema://$remoteHost")
                // TODO: Let's use Kotlin `require()` - looks nice IMO
                if (remoteUri.port == -1 || remoteUri.host == null) {
                    throw IllegalArgumentException("remote-host")
                }
            }

            config.getLong("register-delay")?.let { registerDelay = it }
        }

        startUdpServer()
        registerManagementSocket()
    }

    private fun startUdpServer() {
        socket = vertx.createDatagramSocket()
        socket.handler { packet ->
            val buffer = packet.data()
            try {
                val message = buffer.toJsonObject()
                handle(message)
            } catch (e: Exception) {
                logger.error("ManagementSocket 'handle()' failed.", e)
            }
        }

        socket.listen(localUri.port, localUri.host) { connection ->
            if (connection.failed()) {
                logger.error("UDP connection failed. URI: $localUri", connection.cause())
                throw connection.cause()
            }
            logger.info("Listening on $localUri")
        }
    }

    private fun registerManagementSocket() {
        vertx.setPeriodic(registerDelay) {
            val registerMessage = JsonObject().apply {
                put("type", TYPE_REGISTER)
                put("name", deploymentID())
            }

            socket.send(registerMessage.toBuffer(), remoteUri.port, remoteUri.host) {}
        }
    }

    private fun handle(message: JsonObject) {
        val type = message.getString("type")

        when (type) {
            TYPE_SDP_SESSION -> {
                val payload = message.getJsonObject("payload")
                val sdpSession: SdpSession = payload.mapTo(SdpSession::class.java)
                vertx.eventBus().publish(Routes.sdp, sdpSession, USE_LOCAL_CODEC)
            }
            else -> logger.error("Unknown message type. Message: ${message.encodePrettily()}")
        }
    }
}