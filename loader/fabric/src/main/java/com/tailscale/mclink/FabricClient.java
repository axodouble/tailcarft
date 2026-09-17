/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public final class FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GameRuntime.init(new FabricRuntimeEnv());
        ClientMod.onInitializeClient();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ClientMod.onScreenInit(client, screen, width, height));
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientMod.onTick(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientMod.onClientStopping(client));
    }
}
