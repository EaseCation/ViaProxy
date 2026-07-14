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

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.http.client.ClientConfiguration;

import java.util.ArrayList;
import java.util.List;

final class AliyunSlsTransport implements SlsTransport {

    private final SlsConfiguration configuration;
    private final Client client;

    AliyunSlsTransport(final SlsConfiguration configuration) {
        this.configuration = configuration;
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setConnectionTimeout(3_000);
        clientConfiguration.setSocketTimeout(5_000);
        clientConfiguration.setRequestTimeoutEnabled(true);
        clientConfiguration.setRequestTimeout(5_000);
        clientConfiguration.setMaxErrorRetry(1);
        this.client = new Client(configuration.endpoint(), configuration.accessKeyId(), configuration.accessKeySecret(), clientConfiguration);
    }

    @Override
    public void send(final List<SlsLogRecord> records) throws Exception {
        final List<LogItem> items = new ArrayList<>(records.size());
        for (SlsLogRecord record : records) {
            final long timestampMillis = record.timestampMillis();
            final LogItem item = new LogItem((int) (timestampMillis / 1_000L));
            item.SetTimeNsPart((int) ((timestampMillis % 1_000L) * 1_000_000L));
            record.fields().forEach(item::PushBack);
            items.add(item);
        }
        this.client.PutLogs(this.configuration.project(), this.configuration.logstore(), "", items, this.configuration.pod());
    }

    @Override
    public void close() {
        this.client.shutdown();
    }

}
