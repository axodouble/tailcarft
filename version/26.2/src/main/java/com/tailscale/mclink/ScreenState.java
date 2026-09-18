/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.HttpUtil;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ScreenState implements AutoCloseable {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);

    private Session session;
    private volatile String lastError;

    public synchronized CompletableFuture<String> share(Minecraft client) {
        stop();
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No integrated server is running"));
        }
        boolean previousOnlineMode = server.usesAuthentication();
        enableDevelopmentOfflineAuth(server);
        int port = server.getPort();
        if (port <= 0) {
            port = HttpUtil.getAvailablePort();
            if (!server.publishServer(server.getMultiplayerScope(), server.getDefaultGameType(), false, port)) {
                server.setUsesAuthentication(previousOnlineMode);
                return CompletableFuture.failedFuture(new IllegalStateException("Minecraft could not publish this world"));
            }
        }
        try {
            Session started = start(SessionMode.HOST, List.of("host", "--target", "127.0.0.1:" + port),
                    server, previousOnlineMode);
            return awaitReady(started).thenApply(HelperEvent::invite);
        } catch (Exception e) {
            stop();
            server.setUsesAuthentication(previousOnlineMode);
            return CompletableFuture.failedFuture(e);
        }
    }

    public synchronized CompletableFuture<Void> join(Minecraft client, Screen parent, String invitation) {
        stop();
        try {
            Session started = start(SessionMode.JOIN, List.of("join", "--invite", invitation.trim()), null, false);
            return awaitReady(started).thenAccept(event -> client.execute(() -> {
                ServerData info = new ServerData("Tailcarft World", event.address(), ServerData.Type.OTHER);
                ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(event.address()), info, false, null);
            }));
        } catch (Exception e) {
            stop();
            return CompletableFuture.failedFuture(e);
        }
    }

    public synchronized void tick(Minecraft client) {
        if (session == null) {
            return;
        }
        if (!session.process.isAlive()) {
            stop();
            return;
        }
        if (session.mode == SessionMode.HOST
                && (!client.hasSingleplayerServer() || client.getSingleplayerServer() == null || !client.getSingleplayerServer().isRunning())) {
            stop();
        } else if (session.mode == SessionMode.JOIN && client.level == null
                && !(client.gui.screen() instanceof ConnectScreen)
                && !(client.gui.screen() instanceof JoinRemoteScreen)) {
            stop();
        }
    }

    public synchronized void stop() {
        if (session == null) {
            return;
        }
        Session stopped = session;
        session = null;
        stopped.process.close();
        if (stopped.offlineAuthServer != null) {
            stopped.offlineAuthServer.setUsesAuthentication(stopped.previousOnlineMode);
        }
    }

    public String takeError() {
        String value = lastError;
        lastError = null;
        return value;
    }

    @Override
    public synchronized void close() {
        stop();
    }

    private Session start(SessionMode mode, List<String> arguments, IntegratedServer offlineAuthServer,
                          boolean previousOnlineMode) throws Exception {
        HelperProcess process = HelperProcess.start(arguments, event -> onEvent(event));
        session = new Session(mode, process, offlineAuthServer, previousOnlineMode);
        return session;
    }

    private CompletableFuture<HelperEvent> awaitReady(Session expected) {
        return expected.process.ready(STARTUP_TIMEOUT).whenComplete((event, error) -> {
            if (error != null) {
                synchronized (this) {
                    if (session == expected) {
                        stop();
                    }
                }
            }
        });
    }

    private void onEvent(HelperEvent event) {
        if (event.type().equals("error")) {
            lastError = event.message();
            synchronized (this) {
                stop();
            }
        }
    }

    private static void enableDevelopmentOfflineAuth(IntegratedServer server) {
        if (GameRuntime.get().isDevelopment()
                && "1".equals(System.getenv("MCLINK_DEV_OFFLINE_AUTH"))) {
            server.setUsesAuthentication(false);
        }
    }

    private enum SessionMode { HOST, JOIN }

    private record Session(SessionMode mode, HelperProcess process, IntegratedServer offlineAuthServer,
                           boolean previousOnlineMode) {}
}
