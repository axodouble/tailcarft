/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.TailcatServerEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerList.class)
public abstract class ServerListMixin {
    @Unique private ServerInfo mclink$marker;

    @Inject(method = "swapEntries(II)V", at = @At("HEAD"), cancellable = true)
    private void mclink$blockMarkerMove(int a, int b, CallbackInfo ci) {
        List<ServerInfo> servers = ((ServerListAccessor) (Object) this).mclink$servers();
        if (TailcatServerEntry.MARKER.equals(servers.get(a).address)
                || TailcatServerEntry.MARKER.equals(servers.get(b).address)) {
            ci.cancel();
        }
    }

    @Inject(method = "saveFile()V", at = @At("HEAD"))
    private void mclink$stashMarkerBeforeSave(CallbackInfo ci) {
        List<ServerInfo> servers = ((ServerListAccessor) (Object) this).mclink$servers();
        for (int i = 0; i < servers.size(); i++) {
            if (TailcatServerEntry.MARKER.equals(servers.get(i).address)) {
                mclink$marker = servers.remove(i);
                break;
            }
        }
    }

    @Inject(method = "saveFile()V", at = @At("TAIL"))
    private void mclink$restoreMarkerAfterSave(CallbackInfo ci) {
        if (mclink$marker != null) {
            ((ServerListAccessor) (Object) this).mclink$servers().add(0, mclink$marker);
            mclink$marker = null;
        }
    }
}
