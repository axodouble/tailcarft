/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class QuiltRuntimeEnv implements RuntimeEnv {
    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }

    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    @Override public boolean isDevelopment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

    @Override public String modVersion() {
        return FabricLoader.getInstance().getModContainer("tailcarft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
    }
}
