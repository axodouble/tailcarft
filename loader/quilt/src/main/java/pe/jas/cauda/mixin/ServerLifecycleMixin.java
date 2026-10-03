/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package pe.jas.cauda.mixin;

import pe.jas.cauda.ServerMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class ServerLifecycleMixin {
    @Inject(method = "runServer()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;buildServerStatus()Lnet/minecraft/network/protocol/status/ServerStatus;"))
    private void cauda$onServerStarted(CallbackInfo ci) {
        ServerMod.onStarted((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void cauda$onServerStopping(CallbackInfo ci) {
        ServerMod.onStopping();
    }
}
