/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public final class TailcarftServerEntry {
    public static final String MARKER = "mclink:tailcarft";

    private TailcarftServerEntry() {}

    public static ServerInfo create(TailcarftConfig config) {
        ServerInfo info = new ServerInfo(config.name(), MARKER, ServerInfo.ServerType.OTHER);
        info.setStatus(ServerInfo.Status.SUCCESSFUL);
        info.ping = 1;
        info.label = Text.translatable("mclink.server.label");
        info.playerCountLabel = Text.empty();
        byte[] icon = TailcarftConfig.loadIcon(config.icon());
        if (icon != null) {
            info.setFavicon(icon);
        }
        return info;
    }
}
