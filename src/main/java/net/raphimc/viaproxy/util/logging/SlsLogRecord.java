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

import net.raphimc.viaproxy.ViaProxy;
import org.apache.logging.log4j.core.LogEvent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

record SlsLogRecord(long timestampMillis, Map<String, String> fields) {

    private static final String JAVA_VERSION = System.getProperty("java.vm.name") + " (" + System.getProperty("java.runtime.version") + ")";
    private static final String OS_VERSION = System.getProperty("os.name") + " " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")";

    SlsLogRecord {
        fields = Map.copyOf(fields);
    }

    static SlsLogRecord from(final LogEvent event, final SlsConfiguration configuration, final boolean redactIps) {
        final Throwable thrown = event.getThrown();
        String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
        String exception = thrown != null ? stackTrace(thrown) : "";
        if (redactIps) {
            message = IpAddressRedactor.redact(message);
            exception = IpAddressRedactor.redact(exception);
        }

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put("app", "viaproxy");
        fields.put("environment", configuration.environment());
        fields.put("level", event.getLevel().name());
        fields.put("logger", nullToEmpty(event.getLoggerName()));
        fields.put("thread", nullToEmpty(event.getThreadName()));
        fields.put("message", message);
        fields.put("exceptionClass", thrown != null ? thrown.getClass().getName() : "");
        fields.put("exception", exception);
        fields.put("error", String.valueOf(thrown instanceof Error));
        fields.put("java", JAVA_VERSION);
        fields.put("os", OS_VERSION);
        fields.put("viaproxyVersion", ViaProxy.IMPL_VERSION);
        fields.put("pod", configuration.pod());
        fields.put("node", configuration.node());
        return new SlsLogRecord(event.getTimeMillis(), fields);
    }

    private static String stackTrace(final Throwable throwable) {
        final StringWriter output = new StringWriter();
        throwable.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    private static String nullToEmpty(final String value) {
        return value != null ? value : "";
    }

}
