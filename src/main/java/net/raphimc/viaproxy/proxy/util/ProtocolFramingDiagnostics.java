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

import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.netminecraft.packet.registry.PacketRegistry;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.protocoltranslator.impl.ViaProxyViaCodec;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.util.logging.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, opt-in packet-boundary diagnostics for state and framing incidents.
 *
 * <p>No full payload is retained or logged. Each enabled connection captures at most the configured
 * number of packets and records only lengths plus a 16-byte prefix. The same sequence id follows a
 * transformed P2S packet into the final pre-encryption C2P frame. Direct raw C2P writes are claimed
 * at the earliest diagnostic handler they cross, so they cannot silently bypass final framing capture.</p>
 */
public final class ProtocolFramingDiagnostics {

    public static final String P2S_PRE_NETMINECRAFT_NAME = "protocol-diagnostics-p2s-pre-netminecraft";
    public static final String C2P_PACKET_ORIGIN_NAME = "protocol-diagnostics-c2p-origin";
    public static final String C2P_LOGICAL_NAME = "protocol-diagnostics-c2p-logical";
    public static final String C2P_FINAL_FRAME_NAME = "protocol-diagnostics-c2p-final-frame";

    private static final AttributeKey<Session> SESSION_KEY =
            AttributeKey.valueOf("viaproxy-protocol-framing-diagnostics");
    private static final int PREFIX_BYTES = 16;
    private static final AtomicInteger INSTALLED_CONNECTIONS = new AtomicInteger();

    private ProtocolFramingDiagnostics() {
    }

    public static void install(final ProxyConnection connection, final Channel p2s) {
        final int packetBudget = ViaProxy.getConfig().getProtocolBoundaryDiagnosticsPacketBudget();
        final int connectionBudget = ViaProxy.getConfig().getProtocolBoundaryDiagnosticsConnectionBudget();
        if (packetBudget <= 0 || connectionBudget <= 0) {
            return;
        }
        final int connectionSequence = INSTALLED_CONNECTIONS.incrementAndGet();
        if (connectionSequence > connectionBudget) {
            return;
        }

        final Session session = new Session(connection, packetBudget);
        p2s.attr(SESSION_KEY).set(session);
        connection.getC2P().attr(SESSION_KEY).set(session);

        p2s.pipeline().addAfter(ViaProxyViaCodec.NAME, P2S_PRE_NETMINECRAFT_NAME,
                new P2SPreNetMinecraftHandler(session));
        connection.getC2P().pipeline().addAfter(MCPipeline.PACKET_CODEC_HANDLER_NAME,
                C2P_PACKET_ORIGIN_NAME, new C2PPacketOriginHandler(session));
        connection.getC2P().pipeline().addAfter(MCPipeline.COMPRESSION_HANDLER_NAME,
                C2P_LOGICAL_NAME, new C2PLogicalHandler(session));
        connection.getC2P().pipeline().addAfter(MCPipeline.ENCRYPTION_HANDLER_NAME,
                C2P_FINAL_FRAME_NAME, new C2PFinalFrameHandler(session));

        Logger.LOGGER.error("event=protocol_boundary_capture_started correlationId="
                + session.correlationId + " connectionSequence=" + connectionSequence
                + " connectionBudget=" + connectionBudget + " packetBudget=" + packetBudget
                + " prefixBytes=" + PREFIX_BYTES);
    }

    public static void beginSyntheticPacket(final Channel p2s, final String origin,
                                            final PacketType packetType) {
        final Session session = p2s.attr(SESSION_KEY).get();
        if (session != null) {
            session.syntheticOrigin = new SyntheticOrigin(origin, packetType.toString(),
                    packetType.state().toString(), packetType.getId(), -1, "");
        }
    }

    public static void endSyntheticPacket(final Channel p2s) {
        final Session session = p2s.attr(SESSION_KEY).get();
        if (session != null) {
            session.syntheticOrigin = null;
        }
    }

    public static void beginNetworkPacket(final Channel p2s, final String packetType,
                                          final String state, final int packetId,
                                          final ByteBuf payload) {
        final Session session = p2s.attr(SESSION_KEY).get();
        if (session != null) {
            session.networkOrigin = new SyntheticOrigin("Bedrock/network", packetType, state,
                    packetId, payload.readableBytes(), prefix(payload));
        }
    }

    public static void endNetworkPacket(final Channel p2s) {
        final Session session = p2s.attr(SESSION_KEY).get();
        if (session != null) {
            session.networkOrigin = null;
        }
    }

    public static void linkCurrentP2sPacket(final ProxyConnection connection, final Packet packet) {
        final Session session = connection.getChannel().attr(SESSION_KEY).get();
        if (session == null || session.currentP2s == null) {
            return;
        }
        synchronized (session.linkedPackets) {
            session.linkedPackets.put(packet, session.currentP2s);
        }
    }

    static FrameDetails inspectFinalFrame(final ByteBuf frame, final int compressionThreshold) {
        final ByteBuf view = frame.duplicate();
        try {
            final int outerLength = PacketTypes.readVarInt(view);
            final int framedPayloadLength = view.readableBytes();
            int declaredUncompressedLength = -1;
            int compressedPayloadLength = -1;
            if (compressionThreshold >= 0 && view.isReadable()) {
                declaredUncompressedLength = PacketTypes.readVarInt(view);
                compressedPayloadLength = view.readableBytes();
            }
            return new FrameDetails(outerLength, framedPayloadLength,
                    declaredUncompressedLength, compressedPayloadLength,
                    outerLength == framedPayloadLength);
        } catch (Throwable error) {
            return new FrameDetails(-1, frame.readableBytes(), -1, -1, false);
        }
    }

    private static String prefix(final ByteBuf buffer) {
        final int length = Math.min(PREFIX_BYTES, buffer.readableBytes());
        return ByteBufUtil.hexDump(buffer, buffer.readerIndex(), length);
    }

    private static int packetId(final ByteBuf logicalPacket) {
        try {
            return PacketTypes.readVarInt(logicalPacket.duplicate());
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static final class P2SPreNetMinecraftHandler extends ChannelInboundHandlerAdapter {

        private final Session session;

        private P2SPreNetMinecraftHandler(final Session session) {
            this.session = session;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
            if (!(msg instanceof ByteBuf logicalPacket)) {
                super.channelRead(ctx, msg);
                return;
            }

            final Capture capture = this.session.claimCapture();
            if (capture == null) {
                super.channelRead(ctx, msg);
                return;
            }

            final SyntheticOrigin packetOrigin = this.session.syntheticOrigin != null
                    ? this.session.syntheticOrigin : this.session.networkOrigin;
            capture.origin = packetOrigin != null ? packetOrigin.origin() : "Bedrock/unknown";
            capture.originPacketType = packetOrigin != null ? packetOrigin.packetType() : "unknown";
            capture.originState = packetOrigin != null ? packetOrigin.state() : "unknown";
            capture.originPacketId = packetOrigin != null ? packetOrigin.packetId() : -1;
            capture.originPayloadLength = packetOrigin != null ? packetOrigin.payloadLength() : -1;
            capture.originPrefix = packetOrigin != null ? packetOrigin.prefix() : "";
            capture.packetId = packetId(logicalPacket);
            capture.logicalLength = logicalPacket.readableBytes();
            capture.logicalPrefix = prefix(logicalPacket);

            Logger.LOGGER.error(this.session.formatP2s(capture, logicalPacket));
            final Capture previous = this.session.currentP2s;
            this.session.currentP2s = capture;
            try {
                super.channelRead(ctx, msg);
            } finally {
                this.session.currentP2s = previous;
            }
        }
    }

    private static final class C2PPacketOriginHandler extends ChannelOutboundHandlerAdapter {

        private final Session session;

        private C2PPacketOriginHandler(final Session session) {
            this.session = session;
        }

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg,
                          final ChannelPromise promise) throws Exception {
            final Capture capture;
            if (msg instanceof Packet packet) {
                Capture linkedCapture;
                synchronized (this.session.linkedPackets) {
                    linkedCapture = this.session.linkedPackets.remove(packet);
                }
                if (linkedCapture == null) {
                    linkedCapture = this.session.claimCapture();
                    if (linkedCapture == null) {
                        super.write(ctx, msg, promise);
                        return;
                    }
                    linkedCapture.origin = "ViaProxy/direct";
                    linkedCapture.originPacketType = packet.getClass().getSimpleName();
                    linkedCapture.originState = "unknown";
                    linkedCapture.originPacketId = -1;
                }
                linkedCapture.packetClass = packet.getClass().getName();
                capture = linkedCapture;
            } else if (msg instanceof ByteBuf rawPacket) {
                capture = this.session.claimCapture();
                if (capture == null) {
                    super.write(ctx, msg, promise);
                    return;
                }
                capture.origin = "ViaProxy/raw-c2p";
                capture.originPacketType = rawPacket.getClass().getSimpleName();
                capture.originState = "unknown";
                capture.originPacketId = packetId(rawPacket);
                capture.originPayloadLength = rawPacket.readableBytes();
                capture.originPrefix = prefix(rawPacket);
                capture.packetClass = rawPacket.getClass().getName();
            } else {
                super.write(ctx, msg, promise);
                return;
            }

            final Capture activeCapture = capture;
            promise.addListener(future -> {
                if (!future.isSuccess()) {
                    Logger.LOGGER.error(this.session.formatEncodingFailure(activeCapture, future.cause()));
                }
            });
            final Capture previous = this.session.currentOutbound;
            this.session.currentOutbound = capture;
            try {
                super.write(ctx, msg, promise);
            } finally {
                this.session.currentOutbound = previous;
            }
        }
    }

    private static final class C2PLogicalHandler extends ChannelOutboundHandlerAdapter {

        private final Session session;

        private C2PLogicalHandler(final Session session) {
            this.session = session;
        }

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg,
                          final ChannelPromise promise) throws Exception {
            Capture capture = this.session.currentOutbound;
            boolean ownsCapture = false;
            if (capture == null && msg instanceof ByteBuf logicalPacket) {
                capture = this.session.claimCapture();
                if (capture != null) {
                    capture.origin = "Via/raw-c2p";
                    capture.originPacketType = logicalPacket.getClass().getSimpleName();
                    capture.originState = "unknown";
                    capture.originPacketId = packetId(logicalPacket);
                    capture.originPayloadLength = logicalPacket.readableBytes();
                    capture.originPrefix = prefix(logicalPacket);
                    capture.packetClass = logicalPacket.getClass().getName();
                    ownsCapture = true;
                }
            }
            if (capture != null && msg instanceof ByteBuf logicalPacket) {
                capture.packetId = packetId(logicalPacket);
                capture.logicalLength = logicalPacket.readableBytes();
                capture.logicalPrefix = prefix(logicalPacket);
                capture.logicalReaderIndex = logicalPacket.readerIndex();
                capture.logicalWriterIndex = logicalPacket.writerIndex();
            }
            if (ownsCapture) {
                this.session.currentOutbound = capture;
                try {
                    super.write(ctx, msg, promise);
                } finally {
                    this.session.currentOutbound = null;
                }
            } else {
                super.write(ctx, msg, promise);
            }
        }
    }

    private static final class C2PFinalFrameHandler extends ChannelOutboundHandlerAdapter {

        private final Session session;

        private C2PFinalFrameHandler(final Session session) {
            this.session = session;
        }

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg,
                          final ChannelPromise promise) throws Exception {
            Capture capture = this.session.currentOutbound;
            if (capture == null && msg instanceof ByteBuf frame) {
                capture = this.session.claimCapture();
                if (capture != null) {
                    capture.origin = "ViaProxy/unlinked-final-c2p";
                    capture.originPacketType = frame.getClass().getSimpleName();
                    capture.originState = "unknown";
                    capture.packetClass = frame.getClass().getName();
                }
            }
            if (capture != null && msg instanceof ByteBuf frame) {
                final Integer threshold = ctx.channel()
                        .attr(MCPipeline.COMPRESSION_THRESHOLD_ATTRIBUTE_KEY).get();
                capture.compressionThreshold = threshold != null ? threshold : -1;
                capture.frameDetails = inspectFinalFrame(frame, capture.compressionThreshold);
                capture.framedReaderIndex = frame.readerIndex();
                capture.framedWriterIndex = frame.writerIndex();
                capture.framedPrefix = prefix(frame);
                Logger.LOGGER.error(this.session.formatC2p(capture));
            }
            super.write(ctx, msg, promise);
        }
    }

    private static final class Session {

        private final ProxyConnection connection;
        private final String correlationId = UUID.randomUUID().toString();
        private final AtomicInteger remainingPackets;
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Packet, Capture> linkedPackets = new IdentityHashMap<>();
        private volatile SyntheticOrigin syntheticOrigin;
        private volatile SyntheticOrigin networkOrigin;
        private Capture currentP2s;
        private Capture currentOutbound;

        private Session(final ProxyConnection connection, final int packetBudget) {
            this.connection = connection;
            this.remainingPackets = new AtomicInteger(packetBudget);
        }

        private Capture claimCapture() {
            int remaining;
            do {
                remaining = this.remainingPackets.get();
                if (remaining <= 0) {
                    return null;
                }
            } while (!this.remainingPackets.compareAndSet(remaining, remaining - 1));
            return new Capture(this.sequence.incrementAndGet());
        }

        private String formatP2s(final Capture capture, final ByteBuf logicalPacket) {
            return "event=protocol_boundary correlationId=" + this.correlationId
                    + " sequence=" + capture.sequence
                    + " direction=P2S stage=pre_netminecraft handler=" + P2S_PRE_NETMINECRAFT_NAME
                    + " origin=" + capture.origin
                    + " originState=" + capture.originState
                    + " originPacketType=" + capture.originPacketType
                    + " originPacketId=" + hexId(capture.originPacketId)
                    + " originPayloadLength=" + capture.originPayloadLength
                    + " originFirst16=" + capture.originPrefix
                    + " transformedPacketId=" + hexId(capture.packetId)
                    + " payloadLength=" + capture.logicalLength
                    + " readerIndex=" + logicalPacket.readerIndex()
                    + " writerIndex=" + logicalPacket.writerIndex()
                    + " first16=" + capture.logicalPrefix + " " + this.stateSummary();
        }

        private String formatC2p(final Capture capture) {
            final FrameDetails frame = capture.frameDetails;
            return "event=protocol_boundary correlationId=" + this.correlationId
                    + " sequence=" + capture.sequence
                    + " direction=C2P stage=final_framing handler=" + C2P_FINAL_FRAME_NAME
                    + " origin=" + capture.origin
                    + " originPacketType=" + capture.originPacketType
                    + " packetClass=" + capture.packetClass
                    + " packetId=" + hexId(capture.packetId)
                    + " logicalLength=" + capture.logicalLength
                    + " logicalReaderIndex=" + capture.logicalReaderIndex
                    + " logicalWriterIndex=" + capture.logicalWriterIndex
                    + " logicalFirst16=" + capture.logicalPrefix
                    + " compressionThreshold=" + capture.compressionThreshold
                    + " outerLength=" + frame.outerLength()
                    + " framedPayloadLength=" + frame.framedPayloadLength()
                    + " declaredUncompressedLength=" + frame.declaredUncompressedLength()
                    + " compressedPayloadLength=" + frame.compressedPayloadLength()
                    + " outerLengthValid=" + frame.outerLengthValid()
                    + " framedReaderIndex=" + capture.framedReaderIndex
                    + " framedWriterIndex=" + capture.framedWriterIndex
                    + " framedFirst16=" + capture.framedPrefix
                    + " " + this.stateSummary();
        }

        private String formatEncodingFailure(final Capture capture, final Throwable error) {
            return "event=protocol_boundary_encoding_failure correlationId=" + this.correlationId
                    + " sequence=" + capture.sequence
                    + " direction=C2P packetClass=" + capture.packetClass
                    + " origin=" + capture.origin
                    + " error=" + (error != null ? error.getClass().getSimpleName() : "unknown")
                    + " " + this.stateSummary();
        }

        private String stateSummary() {
            final ProtocolInfo via = this.connection.getUserConnection() != null
                    ? this.connection.getUserConnection().getProtocolInfo() : null;
            return "protocolVersion=" + (this.connection.getClientVersion() != null
                    ? this.connection.getClientVersion().getVersion() : -1)
                    + " c2pState=" + this.connection.getC2pConnectionState()
                    + " p2sState=" + this.connection.getP2sConnectionState()
                    + " c2pRegistryState=" + registryState(this.connection.getC2P())
                    + " p2sRegistryState=" + registryState(this.connection.getChannel())
                    + " viaClientState=" + (via != null ? via.getClientState() : "missing")
                    + " viaServerState=" + (via != null ? via.getServerState() : "missing");
        }
    }

    private static String registryState(final Channel channel) {
        if (channel == null) {
            return "missing";
        }
        final PacketRegistry registry = channel.attr(MCPipeline.PACKET_REGISTRY_ATTRIBUTE_KEY).get();
        return registry != null ? registry.getConnectionState().toString() : "missing";
    }

    private static String hexId(final int packetId) {
        return packetId >= 0 ? "0x" + Integer.toHexString(packetId) : "unknown";
    }

    private record SyntheticOrigin(String origin, String packetType, String state, int packetId,
                                   int payloadLength, String prefix) {
    }

    static record FrameDetails(int outerLength, int framedPayloadLength,
                               int declaredUncompressedLength, int compressedPayloadLength,
                               boolean outerLengthValid) {
    }

    private static final class Capture {
        private final long sequence;
        private String origin;
        private String originPacketType;
        private String originState;
        private int originPacketId = -1;
        private int originPayloadLength = -1;
        private String originPrefix = "";
        private String packetClass = "unknown";
        private int packetId = -1;
        private int logicalLength = -1;
        private String logicalPrefix = "";
        private int logicalReaderIndex = -1;
        private int logicalWriterIndex = -1;
        private int compressionThreshold = -1;
        private FrameDetails frameDetails = new FrameDetails(-1, -1, -1, -1, false);
        private int framedReaderIndex = -1;
        private int framedWriterIndex = -1;
        private String framedPrefix = "";

        private Capture(final long sequence) {
            this.sequence = sequence;
        }
    }
}
