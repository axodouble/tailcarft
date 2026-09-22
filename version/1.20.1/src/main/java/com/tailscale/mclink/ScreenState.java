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
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ScreenState implements AutoCloseable {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);

    private Session session;
    private volatile String lastError;
    private boolean suppressPublishHook;

    public synchronized CompletableFuture<String> share(Minecraft client) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No integrated server is running"));
        }
        int port = server.getPort();
        Session existing = session;
        if (existing != null) {
            if (existing.mode == SessionMode.HOST && existing.process.isAlive()
                    && port > 0 && existing.targetPort == port) {
                return existing.invite;
            }
            stop();
        }
        if (port > 0) {
            return startHosting(server, port, null, false);
        }
        boolean previousOnlineMode = server.usesAuthentication();
        enableDevelopmentOfflineAuth(server);
        port = HttpUtil.getAvailablePort();
        suppressPublishHook = true;
        try {
            if (!server.publishServer(server.getDefaultGameType(), false, port)) {
                server.setUsesAuthentication(previousOnlineMode);
                return CompletableFuture.failedFuture(new IllegalStateException("Minecraft could not publish this world"));
            }
        } finally {
            suppressPublishHook = false;
        }
        return startHosting(server, port, server, previousOnlineMode);
    }

    /**
     * Called by the {@code IntegratedServer.publishServer} mixin when vanilla
     * opens the world to LAN, so a plain "Open to LAN" world also becomes a
     * Tailcarft share. A host session already targeting the same port is left
     * running so re-sharing never drops existing connections; a different port
     * restarts the helper on the new one.
     */
    public synchronized void onPublished(IntegratedServer server, int port) {
        if (suppressPublishHook) {
            return;
        }
        Session existing = session;
        if (existing != null) {
            if (existing.mode == SessionMode.HOST && existing.process.isAlive()
                    && existing.targetPort == port) {
                announceInvite(existing.invite);
                return;
            }
            stop();
        }
        CompletableFuture<String> invite = startHosting(server, port, null, false);
        invite.whenComplete((code, error) -> {
            if (error != null && lastError == null) {
                lastError = error.getMessage() == null ? error.toString() : error.getMessage();
            }
        });
        announceInvite(invite);
    }

    /**
     * Prints the invite as a client system message once the helper is ready,
     * so the player can select and copy it from the chat, mirroring how
     * vanilla announces that the world was opened to LAN.
     */
    private void announceInvite(CompletableFuture<String> invite) {
        Minecraft client = Minecraft.getInstance();
        invite.whenComplete((code, error) -> client.execute(() -> {
            if (error == null && code != null && client.player != null) {
                client.gui.getChat().addMessage(
                        Component.literal("Tailcarft invite: " + code));
            }
        }));
    }

    public synchronized CompletableFuture<Void> join(Minecraft client, Screen parent, String invitation) {
        stop();
        try {
            Session started = start(SessionMode.JOIN, List.of("join", "--invite", invitation.trim()),
                    null, false, -1);
            return awaitReady(started).thenAccept(event -> client.execute(() -> {
                ServerData info = new ServerData("Tailcarft World", event.address(), false);
                ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(event.address()), info, false);
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
                && !(client.screen instanceof ConnectScreen)
                && !(client.screen instanceof JoinRemoteScreen)) {
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
                          boolean previousOnlineMode, int targetPort) throws Exception {
        HelperProcess process = HelperProcess.start(arguments, event -> onEvent(event));
        session = new Session(mode, process, offlineAuthServer, previousOnlineMode, targetPort);
        return session;
    }

    private CompletableFuture<String> startHosting(IntegratedServer server, int port,
            IntegratedServer offlineAuthServer, boolean previousOnlineMode) {
        try {
            Path stateDir = Paths.get(server.getServerDirectory().toString(), "tailcarft");
            Files.createDirectories(stateDir);
            List<String> arguments = new ArrayList<>(List.of("host", "--target", "127.0.0.1:" + port));
            arguments.add("--state-file");
            arguments.add(stateDir.resolve("state.json").toString());
            Session started = start(SessionMode.HOST, arguments, offlineAuthServer, previousOnlineMode, port);
            started.invite = awaitReady(started).thenApply(HelperEvent::invite);
            return started.invite;
        } catch (Exception e) {
            stop();
            if (offlineAuthServer != null) {
                offlineAuthServer.setUsesAuthentication(previousOnlineMode);
            }
            return CompletableFuture.failedFuture(e);
        }
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

    private static final class Session {
        final SessionMode mode;
        final HelperProcess process;
        final IntegratedServer offlineAuthServer;
        final boolean previousOnlineMode;
        final int targetPort;
        CompletableFuture<String> invite;

        Session(SessionMode mode, HelperProcess process, IntegratedServer offlineAuthServer,
                boolean previousOnlineMode, int targetPort) {
            this.mode = mode;
            this.process = process;
            this.offlineAuthServer = offlineAuthServer;
            this.previousOnlineMode = previousOnlineMode;
            this.targetPort = targetPort;
        }
    }
}
