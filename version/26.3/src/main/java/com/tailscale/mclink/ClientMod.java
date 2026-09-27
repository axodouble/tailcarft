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
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.InputWithModifiers;
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

    // Kept for the shared loader screen-init hook; 26.3 wires the connect
    // button through MultiplayerScreenMixin.repositionElements instead.
    public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
    }

    public static void repositionConnectButton(Minecraft client, Screen screen, int width, int height) {
        addConnectButton(client, screen, width, height);
    }

    public static void onTick(Minecraft client) {
        if (System.getenv("TMC_GUI_SMOKE") != null) {
            smokeTick(client);
            return;
        }
        state.tick(client);
        String error = state.takeError();
        if (error != null) {
            SystemToast.add(client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Remote LAN connection failed"), Component.literal(error));
        }
    }

    public static void onClientStopping(Minecraft client) {
        state.close();
    }

    public static boolean isGuiSmokeTest() {
        return System.getenv("TMC_GUI_SMOKE") != null;
    }

    private static void addConnectButton(Minecraft client, Screen screen, int width, int height) {
        Button direct = findButton(screen, "selectServer.direct");
        if (direct == null) {
            return;
        }
        removeOurs(screen, "mclink.join");
        int firstRowY = direct.getY();
        screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getY() == firstRowY)
                .forEach(button -> button.setY(button.getY() - 24));
        for (var child : screen.children()) {
            if (child instanceof ServerSelectionList list) {
                list.setRectangle(width, height - 120, 0, 32);
                break;
            }
        }
        ((ScreenAccessor) screen).mclink$addRenderableWidget(Button.builder(Component.translatable("mclink.join"),
                button -> client.gui.setScreen(new JoinRemoteScreen(screen)))
                .bounds(width / 2 - 102, firstRowY, 204, 20).build());
    }

    /**
     * Adds a "Copy Tailcarft Invite" button as its own section in the
     * scrollable world-options content, right under the multiplayer options,
     * when a host session has a ready invite. Adding it to the content (rather
     * than as a top-level screen widget) keeps it with the LAN section and
     * makes it scroll with the rest of the options.
     */
    public static void addCopyInviteButton(Screen screen, LinearLayout content) {
        String invite = state().currentInvite();
        if (invite == null) {
            return;
        }
        removeOurs(screen, "mclink.copy_invite");
        content.addChild(Button.builder(
                Component.translatable("mclink.copy_invite"),
                button -> {
                    button.setMessage(Component.translatable("mclink.copied"));
                    new ClipboardManager().setClipboard(invite);
                })
            .bounds(0, 0, 308, 20).build());
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

    // A minimal "no modifier keys" input, used to click the title screen's
    // Multiplayer button programmatically during the GUI smoke test.
    private static final InputWithModifiers NO_MODS = new InputWithModifiers() {
        @Override
        public int input() {
            return 0;
        }

        @Override
        public int modifiers() {
            return 0;
        }
    };

    private static void smokeTick(Minecraft client) {
        Screen cur = client.gui.screen();
        // Phase 0: wait until the title screen is ready (its "Multiplayer"
        // button exists), then click it to open the multiplayer safety screen.
        // (The first-launch accessibility onboarding screen is dismissed by
        // AccessibilityOnboardingScreenMixin before the title screen appears.)
        if (smokePhase == 0) {
            Button multiplayer = cur == null ? null : findButton(cur, "menu.multiplayer");
            if (multiplayer != null) {
                multiplayer.onPress(NO_MODS);
                smokePhase = 1;
                smokeFrame = 0;
            }
            return;
        }
        // Phase 1: wait until the multiplayer safety screen is ready (its
        // "Proceed" button exists), then click it to open the multiplayer
        // server-list screen (JoinMultiplayerScreen).
        if (smokePhase == 1) {
            Button proceed = cur == null ? null : findButtonByLabel(cur, "Proceed");
            if (proceed != null) {
                proceed.onPress(NO_MODS);
                smokePhase = 2;
                smokeFrame = 0;
            }
            return;
        }
        // Phase 2: let the multiplayer screen render, then report the verdict.
        // The client stays alive on this screen afterwards (no in-game
        // screenshot, no exit) so the host can capture the real X11 window.
        if (smokePhase == 2) {
            if (++smokeFrame >= 60) {
                Screen screen = client.gui.screen();
                boolean hasJoin = findButton(screen, "mclink.join") != null;
                System.out.println("TMC_GUI_SMOKE joinButton=" + hasJoin
                        + " screen=" + (screen == null ? "null" : screen.getClass().getSimpleName()));
                smokePhase = 3;
            }
            return;
        }
        // Phase 3: keep the client alive on the multiplayer screen.
    }

    // Finds a button by its literal (already-translated) message text, used to
    // click the multiplayer safety screen's "Proceed" button during the smoke
    // test. Distinct from findButton, which matches a translation key.
    private static Button findButtonByLabel(Screen screen, String label) {
        return screen.children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label))
                .findFirst().orElse(null);
    }
}
