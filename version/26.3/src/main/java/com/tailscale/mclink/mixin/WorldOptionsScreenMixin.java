// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.ClientMod;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOptionsScreen.class)
public abstract class WorldOptionsScreenMixin {
    @Inject(
            method = "multiplayerOptions(Lnet/minecraft/client/gui/layouts/LinearLayout;Lnet/minecraft/client/server/IntegratedServer;)V",
            at = @At("TAIL"))
    private void mclink$addCopyInviteSection(LinearLayout content, IntegratedServer server, CallbackInfo ci) {
        ClientMod.addCopyInviteButton((Screen) (Object) this, content);
    }
}
