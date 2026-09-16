/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class ServerHost implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("mclink/server");
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);

    private HelperProcess process;

    public synchronized void start(MinecraftServer server) {
        if (process != null) {
            return;
        }
        Path stateDir = server.getRunDirectory().resolve("tailcarft");
        try {
            Files.createDirectories(stateDir);
            HelperProcess helper = HelperProcess.start(List.of(
                    "host", "--target", "127.0.0.1:" + server.getServerPort(),
                    "--state-file", stateDir.resolve("state.json").toString()),
                    event -> {
                        if (!event.type().equals("ready")) {
                            LOG.warn("Tailcarft helper event [{}]: {}", event.code(), event.message());
                        }
                    });
            process = helper;
            helper.ready(STARTUP_TIMEOUT).whenComplete((event, error) -> {
                if (error != null) {
                    close();
                    LOG.warn("Tailcarft hosting failed: {}", error.getMessage());
                    return;
                }
                LOG.info("[mclink] Tailcarft invitation: {}", event.invite());
            });
        } catch (Exception e) {
            LOG.warn("Could not start Tailcarft host: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }
        process.close();
        process = null;
    }
}
