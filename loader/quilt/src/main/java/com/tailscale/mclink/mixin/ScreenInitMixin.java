/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.ClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenInitMixin {
    @Inject(method = "init()V", at = @At("TAIL"))
    private void mclink$onScreenInit(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = (Screen) (Object) this;
        ClientMod.onScreenInit(client, screen, screen.width, screen.height);
    }
}
