/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ShareScreen extends Screen {
    private final Screen parent;
    private String invite;
    private boolean started;
    private Component status = Component.translatable("mclink.starting");
    public ShareScreen(Screen parent) {
        super(Component.translatable("mclink.share"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("mclink.copy"), b -> {
            if (invite != null) {
                minecraft.keyboardHandler.setClipboard(invite);
            }
        }).bounds(width / 2 - 102, height / 2 + 34, 100, 20).build()).active = invite != null;
        addRenderableWidget(Button.builder(Component.translatable("mclink.stop"), b -> {
            ClientMod.state().stop();
            onClose();
        }).bounds(width / 2 + 2, height / 2 + 34, 100, 20).build());
        if (!started) {
            started = true;
            ClientMod.state().share(minecraft).whenComplete((value, error) -> minecraft.execute(() -> {
                if (error != null) {
                    status = Component.literal("Could not share: " + rootMessage(error));
                } else {
                    invite = value;
                    status = Component.translatable("mclink.sharing");
                    rebuildWidgets();
                }
            }));
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, title, width / 2, height / 2 - 62, 0xffffff);
        context.drawCenteredString(font, status, width / 2, height / 2 - 34, 0xdddddd);
        if (invite != null) {
            context.drawCenteredString(font, Component.literal(shorten(invite)),
                    width / 2, height / 2 - 8, 0xaaaaaa);
        }
    }

    private static String shorten(String value) {
        return value.length() <= 48 ? value : value.substring(0, 22) + "…" + value.substring(value.length() - 22);
    }

    static String rootMessage(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
