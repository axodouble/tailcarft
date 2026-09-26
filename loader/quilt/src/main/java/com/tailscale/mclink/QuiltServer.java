/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.api.DedicatedServerModInitializer;

public final class QuiltServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        GameRuntime.init(new QuiltRuntimeEnv());
    }
}
