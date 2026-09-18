/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import net.minecraft.client.multiplayer.ServerData;

import java.util.List;

public interface ServerListAccessor {
    List<ServerData> mclink$servers();
}
