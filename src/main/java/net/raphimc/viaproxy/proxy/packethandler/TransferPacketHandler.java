/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viaproxy.proxy.packethandler;

import io.netty.channel.ChannelFutureListener;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.impl.common.S2CTransferPacket;
import net.raphimc.netminecraft.util.MinecraftServerAddress;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.events.TransferRoutingEvent;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.TransferDataHolder;
import net.raphimc.viaproxy.util.logging.Logger;

import java.net.InetSocketAddress;
import java.util.List;

public class TransferPacketHandler extends PacketHandler {

    public TransferPacketHandler(ProxyConnection proxyConnection) {
        super(proxyConnection);
    }

    @Override
    public boolean handleP2S(Packet packet, List<ChannelFutureListener> listeners) {
        if (packet instanceof S2CTransferPacket transferPacket) {
            final InetSocketAddress originalTarget = InetSocketAddress.createUnresolved(transferPacket.host, transferPacket.port);
            final TransferRoutingEvent routingEvent = new TransferRoutingEvent(this.proxyConnection, originalTarget);
            try {
                ViaProxy.EVENT_MANAGER.call(routingEvent);
            } catch (Throwable throwable) {
                Logger.LOGGER.error("Transfer routing event dispatch failed", throwable);
            }
            final InetSocketAddress target = routingEvent.getTarget();
            if (!shouldReconnectThroughViaProxy(routingEvent.getMode())) {
                transferPacket.host = target.getHostString();
                transferPacket.port = target.getPort();
                return true;
            }

            final InetSocketAddress reconnectAddress;
            if (routingEvent.getReconnectAddress() != null) {
                reconnectAddress = routingEvent.getReconnectAddress();
            } else if (this.proxyConnection.getClientHandshakeAddress() != null) {
                reconnectAddress = InetSocketAddress.createUnresolved(this.proxyConnection.getClientHandshakeAddress().getHost(), this.proxyConnection.getClientHandshakeAddress().getPort());
            } else {
                Logger.u_warn("transfer", this.proxyConnection, "Client handshake address is invalid, using ViaProxy bind address instead");
                if (!(ViaProxy.getCurrentProxyServer().getChannel().localAddress() instanceof InetSocketAddress bindAddress)) {
                    throw new IllegalArgumentException("ViaProxy bind address must be an InetSocketAddress");
                }
                if (!(this.proxyConnection.getC2P().localAddress() instanceof InetSocketAddress clientAddress)) {
                    throw new IllegalArgumentException("Client address must be an InetSocketAddress");
                }
                reconnectAddress = InetSocketAddress.createUnresolved(clientAddress.getHostString(), bindAddress.getPort());
            }

            // 显式目标按精确端点连接；未改写目标仍保留原 SRV 解析行为。
            final InetSocketAddress newAddress = routingEvent.hasTargetOverride()
                    ? new InetSocketAddress(target.getHostString(), target.getPort())
                    : MinecraftServerAddress.ofResolved(target.getHostString(), target.getPort());
            TransferDataHolder.addTempRedirect(this.proxyConnection.getC2P(), newAddress);
            transferPacket.host = reconnectAddress.getHostString();
            transferPacket.port = reconnectAddress.getPort();
        }

        return true;
    }

    static boolean shouldReconnectThroughViaProxy(final TransferRoutingMode mode) {
        return mode != TransferRoutingMode.DIRECT_TO_TARGET;
    }

}
