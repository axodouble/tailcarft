/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
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

    public static void onScreenInit(Minecraft client, Screen screen, int width, int height) {
        if (screen instanceof PauseScreen && client.hasSingleplayerServer()) {
            addShareButton(client, screen);
        }
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

    private static void addShareButton(Minecraft client, Screen screen) {
        Button anchor = ((PauseScreenAccessor) screen).mclink$disconnectButton();
        if (anchor == null) {
            return;
        }
        removeOurs(screen, "mclink.share");
        ((ScreenAccessor) screen).mclink$addRenderableWidget(Button.builder(Component.translatable("mclink.share"),
                button -> client.gui.setScreen(new ShareScreen(screen)))
                .bounds(anchor.getX(), anchor.getY() + anchor.getHeight() + 4, anchor.getWidth(), anchor.getHeight()).build());
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
