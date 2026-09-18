/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
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
            // Client wiring lives in NeoForgeClientInit, a client-only class loaded
            // reflectively so THIS class's bytecode (verified on a dedicated server,
            // where net.minecraft.client.* is absent) never references a client class.
            // The gate is false on a dedicated server, so the client class is never
            // loaded there.
            try {
                Class.forName("com.tailscale.mclink.NeoForgeClientInit").getMethod("init").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to initialize Tailcarft client", e);
            }
        }
    }
}
