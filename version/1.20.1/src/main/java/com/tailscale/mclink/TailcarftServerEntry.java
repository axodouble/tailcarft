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
    // The entry's "ip" is a sentinel, not a real host. It is a reserved,
    // non-routable address (RFC 5737 TEST-NET-1) chosen so that, on loaders
    // where the join-intercept Mixin is not woven (Forge/NeoForge, ADR-005),
    // the vanilla join attempt opens a ConnectScreen that hangs on the TCP
    // connect instead of failing instantly. That hang gives the always-woven
    // per-tick hook a window to detect the join and re-route it to the
    // Tailcarft join flow. On Fabric/Quilt the Mixin intercepts the click
    // before the connect is even attempted, so the address is never dialed.
    public static final String MARKER = "192.0.2.1";

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
