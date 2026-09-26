/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Client-only wiring for the Forge loader. Loaded reflectively by
 * {@link TailcarftMod} only on the client, so the {@code @Mod} class's bytecode
 * (verified on a dedicated server, where client classes are absent) never
 * references a client class.
 */
public final class ForgeClientInit {
    private ForgeClientInit() {}

    public static void init() {
        ClientMod.onInitializeClient();
        // Forge 26.3 replaced the single phased ClientTickEvent with dedicated
        // Pre/Post record buses; the Post bus fires once per completed tick.
        TickEvent.ClientTickEvent.Post.BUS.addListener(event ->
                ClientMod.onTick(Minecraft.getInstance()));
        ScreenEvent.Init.Post.BUS.addListener(event -> {
            Screen screen = event.getScreen();
            ClientMod.onScreenInit(Minecraft.getInstance(), screen, screen.width, screen.height);
        });
    }
}
