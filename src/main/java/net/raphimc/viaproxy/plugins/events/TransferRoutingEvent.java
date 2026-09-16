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
package net.raphimc.viaproxy.plugins.events;

import net.raphimc.viaproxy.proxy.packethandler.TransferRoutingMode;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

import java.net.InetSocketAddress;
import java.util.Objects;

public final class TransferRoutingEvent {

    private final ProxyConnection proxyConnection;
    private final InetSocketAddress originalTarget;
    private TransferRoutingMode mode = TransferRoutingMode.RECONNECT_THROUGH_VIAPROXY;
    private InetSocketAddress targetOverride;
    private InetSocketAddress reconnectAddress;

    public TransferRoutingEvent(final ProxyConnection proxyConnection, final InetSocketAddress originalTarget) {
        this.proxyConnection = proxyConnection;
        this.originalTarget = Objects.requireNonNull(originalTarget, "originalTarget");
    }

    public ProxyConnection getProxyConnection() {
        return this.proxyConnection;
    }

    public InetSocketAddress getOriginalTarget() {
        return this.originalTarget;
    }

    public TransferRoutingMode getMode() {
        return this.mode;
    }

    public void setMode(final TransferRoutingMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public InetSocketAddress getTarget() {
        return this.targetOverride != null ? this.targetOverride : this.originalTarget;
    }

    public boolean hasTargetOverride() {
        return this.targetOverride != null;
    }

    public void setTarget(final InetSocketAddress target) {
        this.targetOverride = validateAddress(target);
    }

    public void setTargetHost(final String host) {
        this.setTarget(InetSocketAddress.createUnresolved(host, this.getTarget().getPort()));
    }

    public void setTargetPort(final int port) {
        this.setTarget(InetSocketAddress.createUnresolved(this.getTarget().getHostString(), port));
    }

    public InetSocketAddress getReconnectAddress() {
        return this.reconnectAddress;
    }

    public void setReconnectAddress(final InetSocketAddress address) {
        this.reconnectAddress = address != null ? validateAddress(address) : null;
    }

    /** 在全部参数校验成功后一次应用决策；null 目标表示沿用原始目标。 */
    public void setRouting(final TransferRoutingMode mode, final InetSocketAddress target, final InetSocketAddress reconnectAddress) {
        Objects.requireNonNull(mode, "mode");
        if (target != null) validateAddress(target);
        if (reconnectAddress != null) validateAddress(reconnectAddress);
        this.mode = mode;
        this.targetOverride = target;
        this.reconnectAddress = reconnectAddress;
    }

    private static InetSocketAddress validateAddress(final InetSocketAddress address) {
        Objects.requireNonNull(address, "address");
        if (address.getHostString().isBlank() || address.getPort() < 1 || address.getPort() > 65535) {
            throw new IllegalArgumentException("Routing address must have a host and a port between 1 and 65535");
        }
        return address;
    }
}
