/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgeRuntimeEnv implements RuntimeEnv {
    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }

    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override public boolean isDevelopment() { return !FMLEnvironment.production; }

    public boolean isClient() { return FMLEnvironment.dist == Dist.CLIENT; }

    @Override public String modVersion() {
        // Forge 26.3 dropped the ModList.get() instance accessor in favor of
        // static lookups.
        return ModList.getModContainerById("tailcarft").orElseThrow()
                .getModInfo().getVersion().toString();
    }
}
