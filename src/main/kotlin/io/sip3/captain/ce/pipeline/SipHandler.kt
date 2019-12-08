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

import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.util.SipUtil
import io.sip3.commons.PacketTypes
import io.sip3.commons.domain.payload.ByteArrayPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.util.getBytes
import io.vertx.core.Vertx

/**
 * Handles SIP packets
 */
class SipHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("sip")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        var offset = 0
        var mark = -1

        while (offset + buffer.readerIndex() < buffer.capacity()) {
            if (SipUtil.isNewLine(buffer, offset) && SipUtil.startsWithSipWord(buffer, offset)) {
                if (mark > -1) {
                    val p = Packet().apply {
                        timestamp = packet.timestamp
                        srcAddr = packet.srcAddr
                        dstAddr = packet.dstAddr
                        srcPort = packet.srcPort
                        dstPort = packet.dstPort
                        protocolCode = PacketTypes.SIP
                        payload = run {
                            val bytes = buffer.getBytes(buffer.readerIndex() + mark, offset - mark)
                            return@run ByteArrayPayload(bytes)
                        }
                    }
                    packets.add(p)
                }
                mark = offset
            }
            offset++
        }

        if (mark > -1) {
            val p = Packet().apply {
                timestamp = packet.timestamp
                srcAddr = packet.srcAddr
                dstAddr = packet.dstAddr
                srcPort = packet.srcPort
                dstPort = packet.dstPort
                protocolCode = PacketTypes.SIP
                payload = run {
                    val bytes = buffer.getBytes(buffer.readerIndex() + mark, offset - mark)
                    return@run ByteArrayPayload(bytes)
                }
            }
            packets.add(p)
        }

        if (packets.size >= bulkSize) {
            vertx.eventBus().send(Routes.encoder, packets.toList(), USE_LOCAL_CODEC)
            packets.clear()
        }
    }
}