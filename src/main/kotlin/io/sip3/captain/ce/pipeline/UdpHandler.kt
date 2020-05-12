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

import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.util.SipUtil
import io.sip3.commons.domain.payload.Encodable
import io.vertx.core.Context

/**
 * Handles UDP packets
 */
class UdpHandler(context: Context, bulkOperationsEnabled: Boolean) : Handler(context, bulkOperationsEnabled) {

    private val rtcpHandler: RtcpHandler by lazy {
        RtcpHandler(context, bulkOperationsEnabled)
    }
    private val rtpHandler: RtpHandler by lazy {
        RtpHandler(context, bulkOperationsEnabled)
    }
    private val sipHandler: SipHandler by lazy {
        SipHandler(context, bulkOperationsEnabled)
    }

    private var sipEnabled = true
    private var rtpEnabled = false
    private var rtcpEnabled = false

    init {
        context.config().getJsonObject("sip")?.getBoolean("enabled")?.let {
            sipEnabled = it
        }
        context.config().getJsonObject("rtp")?.getBoolean("enabled")?.let {
            rtpEnabled = it
        }
        context.config().getJsonObject("rtcp")?.getBoolean("enabled")?.let {
            rtcpEnabled = it
        }
    }

    override fun onPacket(packet: Packet) {
        val buffer = (packet.payload as Encodable).encode()

        // Source Port
        packet.srcPort = buffer.readUnsignedShort()
        // Destination Port
        packet.dstPort = buffer.readUnsignedShort()
        // Length
        buffer.skipBytes(2)
        // Checksum
        buffer.skipBytes(2)

        val offset = buffer.readerIndex()

        // Filter packets with the size smaller than minimal RTP/RTCP or SIP
        if (buffer.capacity() - offset < 8) {
            return
        }

        when {
            // RTP or RTCP packet
            buffer.getUnsignedByte(offset).toInt().shr(6) == 2 -> {
                val packetType = buffer.getUnsignedByte(offset + 1).toInt()
                if (packetType in 200..211) {
                    // Skip ICMP(RTCP) packet
                    if (rtcpEnabled && !packet.rejected) {
                        rtcpHandler.handle(packet)
                    }
                } else if (rtpEnabled) {
                    rtpHandler.handle(packet)
                }
            }
            // SIP packet
            SipUtil.startsWithSipWord(buffer) -> {
                // Skip ICMP(SIP) packet
                if (sipEnabled && !packet.rejected) {
                    sipHandler.handle(packet)
                }
            }
        }
    }
}
