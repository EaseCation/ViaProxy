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

import net.raphimc.viaproxy.plugins.events.types.ClientSessionVerifier;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

public class ClientSessionVerifyEvent {

    private final ProxyConnection proxyConnection;
    private final String username;
    private final String serverIdHash;
    private ClientSessionVerifier verifier;
    private String deniedMessage = "§cInvalid session! Please restart minecraft (and the launcher) and try again.";
    private String errorMessage = "§cFailed to authenticate with Mojang servers! Please try again later.";

    public ClientSessionVerifyEvent(final ProxyConnection proxyConnection, final String username, final String serverIdHash) {
        this.proxyConnection = proxyConnection;
        this.username = username;
        this.serverIdHash = serverIdHash;
    }

    public ProxyConnection getProxyConnection() {
        return this.proxyConnection;
    }

    public String getUsername() {
        return this.username;
    }

    public String getServerIdHash() {
        return this.serverIdHash;
    }

    public ClientSessionVerifier getVerifier() {
        return this.verifier;
    }

    public void setVerifier(final ClientSessionVerifier verifier) {
        this.verifier = verifier;
    }

    public String getDeniedMessage() {
        return this.deniedMessage;
    }

    public void setDeniedMessage(final String deniedMessage) {
        this.deniedMessage = deniedMessage;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

}
