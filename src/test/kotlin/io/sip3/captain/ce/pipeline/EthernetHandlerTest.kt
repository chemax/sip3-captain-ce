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

import io.mockk.*
import io.mockk.junit5.MockKExtension
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.sip3.captain.ce.domain.Packet
import io.vertx.core.Vertx
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class EthernetHandlerTest {

    companion object {

        // Header: Ethernet II
        val PACKET_1 = byteArrayOf(
                0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
                0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x08.toByte(), 0x00.toByte()
        )

        // Header: Ethernet 802-1Q
        val PACKET_2 = byteArrayOf(
                0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
                0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x81.toByte(), 0x00.toByte(),
                0x01.toByte(), 0x55.toByte(), 0x08.toByte(), 0x00.toByte()
        )

        // Header: Ethernet 802-1AD
        val PACKET_3 = byteArrayOf(
                0x00.toByte(), 0x00.toByte(), 0x5e.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4b.toByte(), 0x00.toByte(),
                0x08.toByte(), 0x25.toByte(), 0x20.toByte(), 0x1a.toByte(), 0xe0.toByte(), 0x88.toByte(), 0xa8.toByte(),
                0x01.toByte(), 0x55.toByte(), 0x81.toByte(), 0x00.toByte(), 0x01.toByte(), 0x55.toByte(), 0x08.toByte(),
                0x00.toByte()
        )
    }

    @Test
    fun `Parse Ethernet - IPv4`() {
        // Init
        mockkConstructor(Ipv4Handler::class)
        val bufferSlot = slot<ByteBuf>()
        every {
            anyConstructed<Ipv4Handler>().handle(capture(bufferSlot), any())
        } just Runs
        // Execute
        val ethernetHandler = EthernetHandler(Vertx.vertx(), false)
        ethernetHandler.handle(Unpooled.wrappedBuffer(PACKET_1), Packet())
        // Assert
        verify { anyConstructed<Ipv4Handler>().handle(any(), any()) }
        val buffer = bufferSlot.captured
        assertEquals(14, buffer.readerIndex())
    }

    @Test
    fun `Parse 802-1Q - IPv4`() {
        // Init
        mockkConstructor(Ipv4Handler::class)
        val slot = slot<ByteBuf>()
        every {
            anyConstructed<Ipv4Handler>().handle(capture(slot), any())
        } just Runs
        // Execute
        val ethernetHandler = EthernetHandler(Vertx.vertx(), false)
        ethernetHandler.handle(Unpooled.wrappedBuffer(PACKET_2), Packet())
        // Assert
        verify { anyConstructed<Ipv4Handler>().handle(any(), any()) }
        val buffer = slot.captured
        assertEquals(18, buffer.readerIndex())
    }

    @Test
    fun `Parse 802-1AD - IPv4`() {
        // Init
        mockkConstructor(Ipv4Handler::class)
        val slot = slot<ByteBuf>()
        every {
            anyConstructed<Ipv4Handler>().handle(capture(slot), any())
        } just Runs
        // Execute
        val ethernetHandler = EthernetHandler(Vertx.vertx(), false)
        ethernetHandler.handle(Unpooled.wrappedBuffer(PACKET_3), Packet())
        // Assert
        verify { anyConstructed<Ipv4Handler>().handle(any(), any()) }
        val buffer = slot.captured
        assertEquals(22, buffer.readerIndex())
    }

    @AfterEach
    fun `Unmock all`() {
        unmockkAll()
    }
}