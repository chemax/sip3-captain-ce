package io.sip3.captain.ce.pipeline

import io.sip3.captain.ce.Routes
import io.sip3.captain.ce.USE_LOCAL_CODEC
import io.sip3.captain.ce.domain.ByteArrayPayload
import io.sip3.captain.ce.domain.Packet
import io.sip3.captain.ce.util.SmppUtil
import io.sip3.captain.ce.util.getBytes
import io.sip3.captain.ce.util.remainingCapacity
import io.vertx.core.Vertx

/**
 * Handles SMPP packets
 */
class SmppHandler(vertx: Vertx, bulkOperationsEnabled: Boolean) : Handler(vertx, bulkOperationsEnabled) {

    private val packets = mutableListOf<Packet>()
    private var bulkSize = 1

    init {
        if (bulkOperationsEnabled) {
            vertx.orCreateContext.config().getJsonObject("smpp")?.let { config ->
                config.getInteger("bulk-size")?.let { bulkSize = it }
            }
        }
    }

    override fun onPacket(packet: Packet) {
        val buffer = packet.payload.encode()

        if (SmppUtil.checkMinPduLength(buffer)) {
            val offset = buffer.readerIndex()

            val length = buffer.getUnsignedInt(offset).toInt()
            if (buffer.remainingCapacity() >= length) {

                val command = buffer.getUnsignedInt(offset + 4)
                if (SmppUtil.isPduCommand(command)) {
                    val p = Packet().apply {
                        timestamp = packet.timestamp
                        srcAddr = packet.srcAddr
                        dstAddr = packet.dstAddr
                        srcPort = packet.srcPort
                        dstPort = packet.dstPort
                        protocolCode = Packet.TYPE_SMPP
                        payload = run {
                            val bytes = buffer.getBytes(offset, length)
                            return@run ByteArrayPayload(bytes)
                        }
                    }
                    packets.add(p)

                    if (packets.size >= bulkSize) {
                        vertx.eventBus().send(Routes.encoder, packets.toList(), USE_LOCAL_CODEC)
                        packets.clear()
                    }

                    buffer.readerIndex(offset + length)
                    if (buffer.remainingCapacity() > 0) {
                        onPacket(packet)
                    }
                }
            }
        }
    }
}