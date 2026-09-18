/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgeRuntimeEnv implements RuntimeEnv {
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }

    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override public boolean isDevelopment() { return !FMLEnvironment.production; }

    public boolean isClient() { return FMLEnvironment.dist == Dist.CLIENT; }

    @Override public String modVersion() {
        return ModList.get().getModContainerById("tailcarft").orElseThrow()
                .getModInfo().getVersion().toString();
    }
}
