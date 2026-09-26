/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(TailcarftMod.MODID)
public class TailcarftMod {
    public static final String MODID = "tailcarft";

    public TailcarftMod() {
        ForgeRuntimeEnv env = new ForgeRuntimeEnv();
        GameRuntime.init(env);

        net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .addListener((ServerStartedEvent event) -> ServerMod.onStarted(event.getServer()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS
                .addListener((ServerStoppingEvent event) -> ServerMod.onStopping());

        if (env.isClient()) {
            // Client wiring lives in ForgeClientInit, a client-only class loaded
            // reflectively so THIS class's bytecode (verified on a dedicated server,
            // where net.minecraft.client.* is absent) never references a client class.
            // The gate is false on a dedicated server, so the client class is never
            // loaded there.
            try {
                Class.forName("com.tailscale.mclink.ForgeClientInit").getMethod("init").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to initialize Tailcarft client", e);
            }
        }
    }
}
