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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AsyncSlsReporter {

    private static final int MAX_BATCH_SIZE = 256;

    private final BlockingQueue<SlsLogRecord> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final SlsTransport transport;
    private final PrintStream diagnostics;
    private final Thread worker;

    AsyncSlsReporter(final SlsTransport transport, final PrintStream diagnostics) {
        this.transport = transport;
        this.diagnostics = diagnostics;
        this.worker = new Thread(this::run, "ViaProxy-SLS-Reporter");
        this.worker.setDaemon(true);
    }

    void start() {
        if (this.accepting.compareAndSet(false, true)) this.worker.start();
    }

    void submit(final SlsLogRecord record) {
        if (this.accepting.get()) this.queue.offer(record);
    }

    void stop(final long timeout, final TimeUnit timeUnit) {
        if (!this.accepting.getAndSet(false)) return;
        this.worker.interrupt();
        try {
            final long waitMillis = timeout > 0L ? Math.max(1L, timeUnit.toMillis(timeout)) : 5_000L;
            this.worker.join(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        try {
            while (this.accepting.get() || !this.queue.isEmpty()) {
                try {
                    final SlsLogRecord first = this.queue.poll(1, TimeUnit.SECONDS);
                    if (first == null) continue;

                    final List<SlsLogRecord> batch = new ArrayList<>(MAX_BATCH_SIZE);
                    batch.add(first);
                    this.queue.drainTo(batch, MAX_BATCH_SIZE - 1);
                    this.transport.send(batch);
                } catch (InterruptedException ignored) {
                    // Stopping interrupts the wait so pending records can be flushed immediately.
                } catch (Throwable throwable) {
                    this.diagnostics.println("[ViaProxy SLS] Upload failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        } finally {
            try {
                this.transport.close();
            } catch (Throwable throwable) {
                this.diagnostics.println("[ViaProxy SLS] Client shutdown failed: " + throwable.getClass().getSimpleName());
            }
        }
    }

}
