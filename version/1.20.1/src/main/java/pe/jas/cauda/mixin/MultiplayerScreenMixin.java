/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package pe.jas.cauda.mixin;

import pe.jas.cauda.ClientMod;
import pe.jas.cauda.JoinRemoteScreen;
import pe.jas.cauda.ServerListAccessor;
import pe.jas.cauda.CaudaConfig;
import pe.jas.cauda.CaudaServerEntry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("cauda");

    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private ServerList servers;
    @Shadow private Button editButton;
    @Shadow private Button deleteButton;

    private MultiplayerScreenMixin() {
        super(null);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void cauda$ensureMarkerEntry(CallbackInfo ci) {
        ClientMod.onScreenInit(this.minecraft, (net.minecraft.client.gui.screens.Screen) (Object) this, this.width, this.height);
        CaudaConfig config = CaudaConfig.load();
        if (config == null) {
            return;
        }
        ServerList list = this.servers;
        if (list.get(CaudaServerEntry.MARKER) != null) {
            return;
        }
        ((ServerListAccessor) (Object) list).cauda$servers().add(0, CaudaServerEntry.create(config));
        this.serverSelectionList.updateOnlineServers(list);
    }

    @Inject(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At("HEAD"), cancellable = true)
    private void cauda$joinViaCauda(ServerData entry, CallbackInfo ci) {
        if (!CaudaServerEntry.MARKER.equals(entry.ip)) {
            return;
        }
        CaudaConfig config = CaudaConfig.load();
        if (config == null) {
            LOG.warn("Cauda server entry clicked but config is missing; ignoring");
            ci.cancel();
            return;
        }
        this.minecraft.setScreen(new JoinRemoteScreen(this, config.invite()));
        ci.cancel();
    }

    @Inject(method = "onSelectedChange()V", at = @At("TAIL"))
    private void cauda$disableMarkerButtons(CallbackInfo ci) {
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (entry instanceof ServerSelectionList.OnlineServerEntry serverEntry
                && CaudaServerEntry.MARKER.equals(serverEntry.getServerData().ip)) {
            this.editButton.active = false;
            this.deleteButton.active = false;
        }
    }
}
