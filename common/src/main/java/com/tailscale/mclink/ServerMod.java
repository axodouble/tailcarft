/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.server.MinecraftServer;

public final class ServerMod {
    private static final ServerHost HOST = new ServerHost();

    private ServerMod() {}

    public static void onStarted(MinecraftServer server) {
        HOST.start(server);
    }

    public static void onStopping() {
        HOST.close();
    }
}
