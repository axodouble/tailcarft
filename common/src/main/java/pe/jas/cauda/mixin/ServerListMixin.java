/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package pe.jas.cauda.mixin;

import pe.jas.cauda.ServerListAccessor;
import pe.jas.cauda.CaudaServerEntry;
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
    @Unique private ServerData cauda$marker;

    @Override
    public List<ServerData> cauda$servers() {
        return this.serverList;
    }

    @Inject(method = "swap(II)V", at = @At("HEAD"), cancellable = true)
    private void cauda$blockMarkerMove(int a, int b, CallbackInfo ci) {
        List<ServerData> servers = ((ServerListAccessor) (Object) this).cauda$servers();
        if (CaudaServerEntry.MARKER.equals(servers.get(a).ip)
                || CaudaServerEntry.MARKER.equals(servers.get(b).ip)) {
            ci.cancel();
        }
    }

    @Inject(method = "save()V", at = @At("HEAD"))
    private void cauda$stashMarkerBeforeSave(CallbackInfo ci) {
        List<ServerData> servers = ((ServerListAccessor) (Object) this).cauda$servers();
        for (int i = 0; i < servers.size(); i++) {
            if (CaudaServerEntry.MARKER.equals(servers.get(i).ip)) {
                cauda$marker = servers.remove(i);
                break;
            }
        }
    }

    @Inject(method = "save()V", at = @At("TAIL"))
    private void cauda$restoreMarkerAfterSave(CallbackInfo ci) {
        if (cauda$marker != null) {
            ((ServerListAccessor) (Object) this).cauda$servers().add(0, cauda$marker);
            cauda$marker = null;
        }
    }
}
