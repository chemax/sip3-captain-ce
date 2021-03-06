/*
 * Copyright 2018-2020 SIP3.IO, Inc.
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
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.RoutesCE
import io.sip3.captain.ce.domain.Ipv4Header
import io.sip3.captain.ce.domain.Packet
import io.sip3.commons.domain.payload.ByteBufPayload
import io.sip3.commons.domain.payload.Encodable
import io.sip3.commons.vertx.annotations.Instance
import io.vertx.core.AbstractVerticle
import mu.KotlinLogging
import org.apache.commons.collections4.map.PassiveExpiringMap
import java.net.InetAddress
import java.sql.Timestamp
import java.util.*

@Instance(singleton = true)
class Ipv4FragmentHandler : AbstractVerticle() {

    private val logger = KotlinLogging.logger {}

    private lateinit var ipv4Handler: Ipv4Handler

    private lateinit var defragmentators: PassiveExpiringMap<String, Defragmentator>
    private var ttl: Long = 60000

    override fun start() {
        config().getJsonObject("ipv4")?.let { config ->
            config.getLong("fragment-ttl")?.let { ttl = it }
        }

        defragmentators = PassiveExpiringMap(ttl)
        vertx.setPeriodic(ttl) { defragmentators.size }

        ipv4Handler = Ipv4Handler(vertx.orCreateContext, false)

        vertx.eventBus().localConsumer<List<Pair<Ipv4Header, Packet>>>(RoutesCE.fragment) { event ->
            val packets = event.body()
            packets.forEach { (header, packet) ->
                try {
                    onPacket(header, packet)
                } catch (e: Exception) {
                    logger.error("Ipv4FragmentHandler 'onPacket()' failed.", e)
                }
            }
        }
    }

    fun onPacket(header: Ipv4Header, packet: Packet) {
        val srcAddr = InetAddress.getByAddress(header.srcAddr)
        val dstAddr = InetAddress.getByAddress(header.dstAddr)
        val key = "${srcAddr.hostAddress}:${dstAddr.hostAddress}:${header.identification}"

        var defragmentator = defragmentators.computeIfAbsent(key) { Defragmentator(packet.timestamp) }
        defragmentator.onPacket(header, (packet.payload as Encodable).encode())?.let { buffer ->
            val p = Packet().apply {
                this.timestamp = defragmentator.timestamp
                this.srcAddr = header.srcAddr
                this.dstAddr = header.dstAddr
                this.protocolNumber = header.protocolNumber
                this.payload = ByteBufPayload(buffer)
            }
            ipv4Handler.routePacket(p)

            defragmentators.remove(key)
        }
    }

    class Defragmentator(val timestamp: Timestamp) {

        private val headers = TreeMap<Int, Ipv4Header>()
        private val buffers = TreeMap<Int, ByteBuf>()
        private var lastFragmentReceived = false

        fun onPacket(header: Ipv4Header, buffer: ByteBuf): ByteBuf? {
            headers[8 * header.fragmentOffset] = header
            buffers[8 * header.fragmentOffset] = buffer
            lastFragmentReceived = lastFragmentReceived || !header.moreFragments
            // Check that last fragment received
            if (!lastFragmentReceived) {
                return null
            }
            // Check that all fragments received
            var expectedOffset = 0
            headers.forEach { (offset, header) ->
                if (offset != expectedOffset) {
                    return null
                }
                expectedOffset = offset + header.totalLength - header.headerLength
            }
            // Concat all fragments
            return Unpooled.buffer(expectedOffset).apply {
                buffers.forEach { (_, b) -> writeBytes(b) }
            }
        }
    }
}