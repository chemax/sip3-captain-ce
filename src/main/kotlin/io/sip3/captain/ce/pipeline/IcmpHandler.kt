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

package io.sip3.captain.ce.pipeline

import io.netty.buffer.ByteBuf
import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.ByteArrayPayload
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx

/**
 * Handles ICMP packets
 */
class IcmpHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("icmp")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(buffer: ByteBuf, packet: Packet) {
        val offset = buffer.readerIndex()

        val type = buffer.getByte(offset).toInt()
        val code = buffer.getByte(offset + 1).toInt()

        // Destination Port Unreachable
        if (type == 3 && code == 3) {
            packet.protocolCode = Packet.TYPE_ICMP
            packet.payload = ByteArrayPayload().apply {
                val slice = buffer.slice()
                bytes = ByteArray(slice.capacity())
                slice.readBytes(bytes)
            }
            packets.add(packet)

            if (packets.size >= bulkSize) {
                vertx.eventBus().send(Routes.encoder, packets.toList(), USE_LOCAL_CODEC)
                packets.clear()
            }
        }
    }
}