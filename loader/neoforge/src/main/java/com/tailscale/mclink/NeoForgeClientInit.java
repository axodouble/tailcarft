/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only wiring for the NeoForge loader. Loaded reflectively by
 * {@link TailcarftMod} only on the client, so the {@code @Mod} class's bytecode
 * (verified on a dedicated server, where client classes are absent) never
 * references a client class.
 */
public final class NeoForgeClientInit {
    private NeoForgeClientInit() {}

    public static void init() {
        ClientMod.onInitializeClient();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> ClientMod.onTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
            Screen screen = event.getScreen();
            ClientMod.onScreenInit(Minecraft.getInstance(), screen, screen.width, screen.height);
        });
    }
}
