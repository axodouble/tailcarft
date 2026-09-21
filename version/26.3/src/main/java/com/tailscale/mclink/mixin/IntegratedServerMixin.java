/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.ClientMod;
import com.tailscale.mclink.ScreenState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("TAIL"))
    private void mclink$onPublished(MinecraftServer.MultiplayerScope scope, int port,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueI() == 0) {
            return;
        }
        ScreenState state = ClientMod.state();
        if (state != null) {
            state.onPublished((IntegratedServer) (Object) this, port);
        }
    }

    @Inject(method = "unpublishServer()Z", at = @At("TAIL"))
    private void mclink$onUnpublished(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueI() == 0) {
            return;
        }
        ScreenState state = ClientMod.state();
        if (state != null) {
            state.onUnpublished();
        }
    }
}
