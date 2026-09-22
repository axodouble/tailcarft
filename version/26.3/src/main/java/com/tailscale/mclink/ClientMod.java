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
import net.minecraft.client.gui.screens.Screen;
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

    // Kept for the shared loader screen-init hook; 26.3 wires the connect
    // button through MultiplayerScreenMixin.repositionElements instead.
    public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
    }

    public static void repositionConnectButton(Minecraft client, Screen screen, int width, int height) {
        addConnectButton(client, screen, width, height);
    }

    public static void onTick(Minecraft client) {
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
     * Adds a "Copy Tailcarft Invite" button next to the anchor button when a
     * host session has a ready invite, so the invite can be copied to the
     * clipboard without selecting it in the chat.
     */
    public static void addCopyInviteButton(Screen screen, Button anchor) {
        String invite = state().currentInvite();
        if (invite == null) {
            return;
        }
        removeOurs(screen, "mclink.copy_invite");
        int width = 204;
        ((ScreenAccessor) screen).mclink$addRenderableWidget(Button.builder(
                Component.translatable("mclink.copy_invite"),
                button -> {
                    button.setMessage(Component.translatable("mclink.copied"));
                    new ClipboardManager().setClipboard(invite);
                })
            .bounds(anchor.getX() - 4 - width, anchor.getY(), width, 20).build());
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
}
