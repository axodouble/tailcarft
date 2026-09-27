/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import com.mojang.blaze3d.platform.ClipboardManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;

public final class ClientMod {
    private static ScreenState state;

    private ClientMod() {}

    public static void onInitializeClient() {
        state = new ScreenState();
    }

    public static ScreenState state() {
        return state;
    }

    public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
        if (screen instanceof JoinMultiplayerScreen) {
            addConnectButton(client, screen, width, height);
        }
    }

    public static void onTick(Minecraft client) {
        if (System.getenv("TMC_GUI_SMOKE") != null) {
            smokeTick(client);
            return;
        }
        state.tick(client);
        String error = state.takeError();
        if (error != null) {
            SystemToast.add(client.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal("Remote LAN connection failed"), Component.literal(error));
        }
    }

    public static void onClientStopping(Minecraft client) {
        state.close();
    }

    private static void addConnectButton(Minecraft client, Screen screen, int width, int height) {
        Button direct = findButton(screen, "selectServer.direct");
        if (direct == null) {
            return;
        }
        int firstRowY = direct.getY();
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() == firstRowY)
                .forEach(button -> button.setY(button.getY() - 24));
        for (var child : screen.children()) {
            if (child instanceof ServerSelectionList list) {
                list.updateSize(width, height - 120, 32, 32 + (height - 120));
                break;
            }
        }
        ((ScreenAccessor) screen).mclink$addRenderableWidget(Button.builder(Component.translatable("mclink.join"),
                button -> client.setScreen(new JoinRemoteScreen(screen)))
                .bounds(width / 2 - 102, firstRowY, 204, 20).build());
    }

    /**
     * Adds a "Copy Tailcarft Invite" button above the port field when a host
     * session has a ready invite, so the invite can be copied to the
     * clipboard without selecting it in the chat.
     */
    public static void addCopyInviteButton(Screen screen, Minecraft client) {
        String invite = state().currentInvite();
        if (invite == null) {
            return;
        }
        removeOurs(screen, "mclink.copy_invite");
        for (var child : screen.children()) {
            if (child instanceof EditBox edit) {
                ((ScreenAccessor) screen).mclink$addRenderableWidget(Button.builder(
                        Component.translatable("mclink.copy_invite"),
                        button -> {
                            button.setMessage(Component.translatable("mclink.copied"));
                            new ClipboardManager().setClipboard(client.getWindow().getWindow(), invite);
                        })
                    .bounds(edit.getX() - 75, edit.getY() - 24, 300, 20).build());
                return;
            }
        }
    }

    private static void removeOurs(Screen screen, String translationKey) {
        Button ours;
        while ((ours = findButton(screen, translationKey)) != null) {
            ((ScreenAccessor) screen).mclink$removeWidget(ours);
        }
    }

    private static Button findButton(Screen screen, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElse(null);
    }

    // GUI smoke hook: when TMC_GUI_SMOKE is set, the client waits for the
    // startup resource load to finish (the game settles on a stable screen),
    // opens the Multiplayer screen, waits for it to render, then prints a
    // verdict line (TMC_GUI_SMOKE joinButton=true|false) and stays alive so
    // scripts/gui-smoke.sh can capture the real X11 window. Inert in normal
    // play. See ADR-004.
    private static int smokePhase;
    private static int smokeFrame;
    private static Class<?> smokeSettledScreen;

    private static void smokeTick(Minecraft client) {
        // Phase 0: wait until the game settles on a stable, non-null screen,
        // which means the startup resource load is done and the render
        // pipeline is in normal mode. A screen opened now will actually
        // render. (Waiting for a specific class such as TitleScreen is
        // fragile: the game may instead settle on an onboarding screen.)
        if (smokePhase == 0) {
            Class<?> now = client.screen == null ? null : client.screen.getClass();
            if (now != null && now == smokeSettledScreen) {
                smokeFrame++;
            } else {
                smokeSettledScreen = now;
                smokeFrame = 0;
            }
            if (smokeFrame >= 20) {
                client.setScreen(new JoinMultiplayerScreen(client.screen));
                smokePhase = 1;
                smokeFrame = 0;
            }
            return;
        }
        // Phase 1: let the multiplayer screen render, then report the verdict.
        // The client stays alive on this screen afterwards (no in-game
        // screenshot, no exit) so the host can capture the real X11 window.
        if (smokePhase == 1) {
            if (++smokeFrame >= 60) {
                Screen screen = client.screen;
                boolean hasJoin = findButton(screen, "mclink.join") != null;
                System.out.println("TMC_GUI_SMOKE joinButton=" + hasJoin
                        + " screen=" + (screen == null ? "null" : screen.getClass().getSimpleName()));
                smokePhase = 2;
            }
            return;
        }
        // Phase 2: keep the client alive on the multiplayer screen.
    }
}
