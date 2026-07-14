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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Plugin(name = "Sls", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class SlsAppender extends AbstractAppender {

    private static AsyncSlsReporter sharedReporter;
    private static int sharedReferences;

    private final SlsConfiguration configuration;
    private final PrintStream diagnostics;
    private final boolean shared;
    private AsyncSlsReporter reporter;

    private SlsAppender(final String name, final Filter filter, final SlsConfiguration configuration,
                        final PrintStream diagnostics, final AsyncSlsReporter reporter, final boolean shared) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
        this.configuration = configuration;
        this.diagnostics = diagnostics;
        this.reporter = reporter;
        this.shared = shared;
    }

    @PluginFactory
    public static SlsAppender createAppender(@PluginAttribute("name") final String name,
                                             @PluginElement("Filter") final Filter filter) {
        final PrintStream diagnostics = System.err;
        if (name == null || name.isBlank()) {
            diagnostics.println("[ViaProxy SLS] Appender disabled: name is required");
            return null;
        }

        final Map<String, String> environment = System.getenv();
        final Optional<SlsConfiguration> configuration = SlsConfiguration.fromEnvironment(environment);
        if (configuration.isEmpty()) {
            final List<String> missing = SlsConfiguration.missingVariables(environment);
            diagnostics.println("[ViaProxy SLS] Appender disabled; missing environment variables: " + String.join(", ", missing));
            return new SlsAppender(name, filter, null, diagnostics, null, true);
        }

        return new SlsAppender(name, filter, configuration.get(), diagnostics, null, true);
    }

    static SlsAppender createForTesting(final String name, final AsyncSlsReporter reporter, final SlsConfiguration configuration) {
        return new SlsAppender(name, null, configuration, System.err, reporter, false);
    }

    @Override
    public void start() {
        super.start();
        if (this.configuration == null) return;
        if (this.shared) {
            synchronized (SlsAppender.class) {
                if (sharedReporter == null) {
                    sharedReporter = new AsyncSlsReporter(new AliyunSlsTransport(this.configuration), this.diagnostics);
                    sharedReporter.start();
                }
                sharedReferences++;
                this.reporter = sharedReporter;
            }
        } else if (this.reporter != null) {
            this.reporter.start();
        }
    }

    @Override
    public void append(final LogEvent event) {
        if (this.reporter == null || !event.getLevel().isMoreSpecificThan(Level.ERROR)) return;
        final boolean redactIps = ViaProxy.getConfig() == null || !ViaProxy.getConfig().shouldLogIps();
        this.reporter.submit(SlsLogRecord.from(event.toImmutable(), this.configuration, redactIps));
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        this.setStopping();
        if (this.shared) {
            synchronized (SlsAppender.class) {
                if (this.reporter != null && --sharedReferences == 0) {
                    sharedReporter.stop(timeout, timeUnit);
                    sharedReporter = null;
                }
                this.reporter = null;
            }
        } else if (this.reporter != null) {
            this.reporter.stop(timeout, timeUnit);
        }
        this.setStopped();
        return true;
    }

}
