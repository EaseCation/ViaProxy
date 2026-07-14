/*
 * This file is part of ViaProxy - https://github.com/RaphiMC/ViaProxy
 * Copyright (C) 2021-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viaproxy.util.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record SlsConfiguration(String endpoint, String project, String logstore, String accessKeyId,
                        String accessKeySecret, String environment, String pod, String node) {

    private static final List<String> REQUIRED_VARIABLES = List.of(
            "VIAPROXY_SLS_ENDPOINT",
            "VIAPROXY_SLS_PROJECT",
            "VIAPROXY_SLS_LOGSTORE",
            "VIAPROXY_SLS_ACCESS_KEY_ID",
            "VIAPROXY_SLS_ACCESS_KEY_SECRET",
            "VIAPROXY_ENVIRONMENT",
            "VIAPROXY_POD_NAME",
            "VIAPROXY_NODE_NAME"
    );

    static Optional<SlsConfiguration> fromEnvironment(final Map<String, String> environment) {
        if (!missingVariables(environment).isEmpty()) return Optional.empty();
        return Optional.of(new SlsConfiguration(
                environment.get("VIAPROXY_SLS_ENDPOINT"),
                environment.get("VIAPROXY_SLS_PROJECT"),
                environment.get("VIAPROXY_SLS_LOGSTORE"),
                environment.get("VIAPROXY_SLS_ACCESS_KEY_ID"),
                environment.get("VIAPROXY_SLS_ACCESS_KEY_SECRET"),
                environment.get("VIAPROXY_ENVIRONMENT"),
                environment.get("VIAPROXY_POD_NAME"),
                environment.get("VIAPROXY_NODE_NAME")
        ));
    }

    static List<String> missingVariables(final Map<String, String> environment) {
        final List<String> missing = new ArrayList<>();
        for (String variable : REQUIRED_VARIABLES) {
            final String value = environment.get(variable);
            if (value == null || value.isBlank()) missing.add(variable);
        }
        return List.copyOf(missing);
    }

}
