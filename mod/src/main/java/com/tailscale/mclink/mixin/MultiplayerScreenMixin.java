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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("mclink");

    @Shadow protected MultiplayerServerListWidget serverListWidget;
    @Shadow private ServerList serverList;
    @Shadow private ButtonWidget buttonEdit;
    @Shadow private ButtonWidget buttonDelete;

    private MultiplayerScreenMixin() {
        super(null);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void mclink$ensureMarkerEntry(CallbackInfo ci) {
        TailcarftConfig config = TailcarftConfig.load();
        if (config == null) {
            return;
        }
        ServerList list = this.serverList;
        if (list.get(TailcarftServerEntry.MARKER) != null) {
            return;
        }
        ((ServerListAccessor) (Object) list).mclink$servers().add(0, TailcarftServerEntry.create(config));
        this.serverListWidget.setServers(list);
    }

    @Inject(method = "connect(Lnet/minecraft/client/network/ServerInfo;)V", at = @At("HEAD"), cancellable = true)
    private void mclink$joinViaTailcarft(ServerInfo entry, CallbackInfo ci) {
        if (!TailcarftServerEntry.MARKER.equals(entry.address)) {
            return;
        }
        TailcarftConfig config = TailcarftConfig.load();
        if (config == null) {
            LOG.warn("Tailcarft server entry clicked but config is missing; ignoring");
            ci.cancel();
            return;
        }
        this.client.setScreen(new JoinRemoteScreen(this, config.invite()));
        ci.cancel();
    }

    @Inject(method = "updateButtonActivationStates()V", at = @At("TAIL"))
    private void mclink$disableMarkerButtons(CallbackInfo ci) {
        MultiplayerServerListWidget.Entry entry = this.serverListWidget.getSelectedOrNull();
        if (entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry
                && TailcarftServerEntry.MARKER.equals(serverEntry.getServer().address)) {
            this.buttonEdit.active = false;
            this.buttonDelete.active = false;
        }
    }
}
