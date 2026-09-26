/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.api.ClientModInitializer;

public final class QuiltClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GameRuntime.init(new QuiltRuntimeEnv());
        ClientMod.onInitializeClient();
    }
}
