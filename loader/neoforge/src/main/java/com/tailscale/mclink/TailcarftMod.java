/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(TailcarftMod.MODID)
public class TailcarftMod {
    public static final String MODID = "tailcarft";

    public TailcarftMod(IEventBus modEventBus, ModContainer modContainer) {
        GameRuntime.init(new NeoForgeRuntimeEnv());

        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerMod.onStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ServerMod.onStopping());

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientMod.onInitializeClient();
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                    event -> ClientMod.onTick(Minecraft.getInstance()));
            NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event -> {
                Screen screen = event.getScreen();
                ClientMod.onScreenInit(Minecraft.getInstance(), screen, screen.width, screen.height);
            });
        }
    }
}
