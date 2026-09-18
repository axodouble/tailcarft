/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class JoinRemoteScreen extends Screen {
    private final Screen parent;
    private final String invitation;
    private boolean autoStarted;
    private EditBox invite;
    private Button connect;
    private Component status = Component.empty();

    public JoinRemoteScreen(Screen parent) {
        this(parent, null);
    }

    public JoinRemoteScreen(Screen parent, String invitation) {
        super(Component.translatable("mclink.join"));
        this.parent = parent;
        this.invitation = invitation;
    }

    @Override
    protected void init() {
        invite = new EditBox(font, width / 2 - 150, height / 2 - 22, 300, 20,
                Component.translatable("mclink.invite"));
        invite.setMaxLength(8192);
        invite.setHint(Component.translatable("mclink.invite_hint"));
        addRenderableWidget(invite);
        connect = addRenderableWidget(Button.builder(Component.translatable("mclink.connect"), b -> begin())
                .bounds(width / 2 - 102, height / 2 + 12, 100, 20).build());
        connect.active = false;
        invite.setResponder(value -> connect.active = value.trim().startsWith("mcl1_"));
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width / 2 + 2, height / 2 + 12, 100, 20).build());
        setInitialFocus(invite);
        if (invitation != null && !autoStarted) {
            autoStarted = true;
            invite.setValue(invitation);
            begin();
        }
    }

    private void begin() {
        connect.active = false;
        invite.setEditable(false);
        status = Component.translatable("mclink.starting");
        ClientMod.state().join(minecraft, parent, invite.getValue()).whenComplete((ignored, error) -> {
            if (error != null) {
                minecraft.execute(() -> {
                    status = Component.literal("Could not connect: " + ShareScreen.rootMessage(error));
                    invite.setEditable(true);
                    connect.active = true;
                });
            }
        });
    }

    @Override
    public void onClose() {
        ClientMod.state().stop();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, height / 2 - 58, 0xffffff);
        context.centeredText(font, status, width / 2, height / 2 + 46, 0xffaaaa);
    }
}
