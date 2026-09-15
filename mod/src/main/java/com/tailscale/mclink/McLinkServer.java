/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class McLinkServer implements DedicatedServerModInitializer {
    private final ServerHost host = new ServerHost();

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> host.start(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> host.close());
    }
}
