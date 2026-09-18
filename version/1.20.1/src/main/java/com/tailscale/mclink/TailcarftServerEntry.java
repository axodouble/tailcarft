/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public final class TailcarftServerEntry {
    public static final String MARKER = "mclink:tailcarft";

    private TailcarftServerEntry() {}

    public static ServerData create(TailcarftConfig config) {
        ServerData info = new ServerData(config.name(), MARKER, false);
        info.ping = 1;
        info.motd = Component.translatable("mclink.server.label");
        info.players = null;
        byte[] icon = TailcarftConfig.loadIcon(config.icon());
        if (icon != null) {
            info.setIconBytes(icon);
        }
        return info;
    }
}
