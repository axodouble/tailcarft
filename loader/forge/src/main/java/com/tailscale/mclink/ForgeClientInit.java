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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

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
        // Forge 1.20.1 posts one ClientTickEvent per tick with a START/END phase
        // and no client accessor; mirror the NeoForge Post phase with END.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                ClientMod.onTick(Minecraft.getInstance());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ScreenEvent.Init.Post.class, event -> {
            Screen screen = event.getScreen();
            ClientMod.onScreenInit(Minecraft.getInstance(), screen, screen.width, screen.height);
        });
    }
}
