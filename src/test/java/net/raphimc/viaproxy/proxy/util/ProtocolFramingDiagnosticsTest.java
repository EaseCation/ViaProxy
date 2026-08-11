/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viaproxy.proxy.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.netminecraft.packet.PacketTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolFramingDiagnosticsTest {

    @Test
    void inspectsAnUncompressedFinalFrameWithoutAdvancingTheBuffer() {
        final ByteBuf frame = Unpooled.buffer();
        try {
            PacketTypes.writeVarInt(frame, 3);
            frame.writeBytes(new byte[]{0x04, 0x01, 0x02});
            final int readerIndex = frame.readerIndex();

            final ProtocolFramingDiagnostics.FrameDetails details =
                    ProtocolFramingDiagnostics.inspectFinalFrame(frame, -1);

            assertEquals(3, details.outerLength());
            assertEquals(3, details.framedPayloadLength());
            assertEquals(-1, details.declaredUncompressedLength());
            assertEquals(-1, details.compressedPayloadLength());
            assertTrue(details.outerLengthValid());
            assertEquals(readerIndex, frame.readerIndex());
        } finally {
            frame.release();
        }
    }

    @Test
    void separatesCompressionHeaderAndPayloadLengths() {
        final ByteBuf frame = Unpooled.buffer();
        try {
            final ByteBuf payload = Unpooled.buffer();
            try {
                PacketTypes.writeVarInt(payload, 300);
                payload.writeBytes(new byte[]{0x78, (byte) 0x9c, 0x01, 0x02});
                PacketTypes.writeVarInt(frame, payload.readableBytes());
                frame.writeBytes(payload);
            } finally {
                payload.release();
            }

            final ProtocolFramingDiagnostics.FrameDetails details =
                    ProtocolFramingDiagnostics.inspectFinalFrame(frame, 256);

            assertEquals(frame.readableBytes() - 1, details.outerLength());
            assertEquals(details.outerLength(), details.framedPayloadLength());
            assertEquals(300, details.declaredUncompressedLength());
            assertEquals(4, details.compressedPayloadLength());
            assertTrue(details.outerLengthValid());
        } finally {
            frame.release();
        }
    }

    @Test
    void flagsAnOuterLengthThatDoesNotMatchTheFinalFrameBoundary() {
        final ByteBuf frame = Unpooled.buffer();
        try {
            PacketTypes.writeVarInt(frame, 8);
            frame.writeBytes(new byte[]{0x00, 0x04, 0x01});

            final ProtocolFramingDiagnostics.FrameDetails details =
                    ProtocolFramingDiagnostics.inspectFinalFrame(frame, 256);

            assertFalse(details.outerLengthValid());
            assertEquals(8, details.outerLength());
            assertEquals(3, details.framedPayloadLength());
            assertEquals(0, details.declaredUncompressedLength());
            assertEquals(2, details.compressedPayloadLength());
        } finally {
            frame.release();
        }
    }
}
