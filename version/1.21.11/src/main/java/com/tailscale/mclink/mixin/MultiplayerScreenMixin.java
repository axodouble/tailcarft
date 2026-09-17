/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.JoinRemoteScreen;
import com.tailscale.mclink.TailcarftConfig;
import com.tailscale.mclink.TailcarftServerEntry;
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
    private static final Logger LOG = LoggerFactory.getLogger("mclink");

    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private ServerList servers;
    @Shadow private Button editButton;
    @Shadow private Button deleteButton;

    private MultiplayerScreenMixin() {
        super(null);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void mclink$ensureMarkerEntry(CallbackInfo ci) {
        TailcarftConfig config = TailcarftConfig.load();
        if (config == null) {
            return;
        }
        ServerList list = this.servers;
        if (list.get(TailcarftServerEntry.MARKER) != null) {
            return;
        }
        ((ServerListAccessor) (Object) list).mclink$servers().add(0, TailcarftServerEntry.create(config));
        this.serverSelectionList.updateOnlineServers(list);
    }

    @Inject(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At("HEAD"), cancellable = true)
    private void mclink$joinViaTailcarft(ServerData entry, CallbackInfo ci) {
        if (!TailcarftServerEntry.MARKER.equals(entry.ip)) {
            return;
        }
        TailcarftConfig config = TailcarftConfig.load();
        if (config == null) {
            LOG.warn("Tailcarft server entry clicked but config is missing; ignoring");
            ci.cancel();
            return;
        }
        this.minecraft.setScreen(new JoinRemoteScreen(this, config.invite()));
        ci.cancel();
    }

    @Inject(method = "onSelectedChange()V", at = @At("TAIL"))
    private void mclink$disableMarkerButtons(CallbackInfo ci) {
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (entry instanceof ServerSelectionList.OnlineServerEntry serverEntry
                && TailcarftServerEntry.MARKER.equals(serverEntry.getServerData().ip)) {
            this.editButton.active = false;
            this.deleteButton.active = false;
        }
    }
}
