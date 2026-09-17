/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class FabricServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        GameRuntime.init(new FabricRuntimeEnv());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ServerMod.onStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerMod.onStopping());
    }
}
