/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.TailcarftServerEntry;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerList.class)
public abstract class ServerListMixin implements ServerListAccessor {
    @Shadow private List<ServerData> serverList;
    @Unique private ServerData mclink$marker;

    @Override
    public List<ServerData> mclink$servers() {
        return this.serverList;
    }

    @Inject(method = "swap(II)V", at = @At("HEAD"), cancellable = true)
    private void mclink$blockMarkerMove(int a, int b, CallbackInfo ci) {
        List<ServerData> servers = ((ServerListAccessor) (Object) this).mclink$servers();
        if (TailcarftServerEntry.MARKER.equals(servers.get(a).ip)
                || TailcarftServerEntry.MARKER.equals(servers.get(b).ip)) {
            ci.cancel();
        }
    }

    @Inject(method = "save()V", at = @At("HEAD"))
    private void mclink$stashMarkerBeforeSave(CallbackInfo ci) {
        List<ServerData> servers = ((ServerListAccessor) (Object) this).mclink$servers();
        for (int i = 0; i < servers.size(); i++) {
            if (TailcarftServerEntry.MARKER.equals(servers.get(i).ip)) {
                mclink$marker = servers.remove(i);
                break;
            }
        }
    }

    @Inject(method = "save()V", at = @At("TAIL"))
    private void mclink$restoreMarkerAfterSave(CallbackInfo ci) {
        if (mclink$marker != null) {
            ((ServerListAccessor) (Object) this).mclink$servers().add(0, mclink$marker);
            mclink$marker = null;
        }
    }
}
