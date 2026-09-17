/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.Invites;
import com.tailscale.mclink.JoinRemoteScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "connect(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;Lnet/minecraft/client/multiplayer/TransferState;)V",
            at = @At("HEAD"), cancellable = true)
    private static void mclink$joinViaInvite(Minecraft client, ServerAddress address, ServerData server, TransferState transferState, CallbackInfo ci) {
        mclink$maybeJoin(client, client.screen, address, ci);
    }

    @Inject(method = "startConnecting(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;ZLnet/minecraft/client/multiplayer/TransferState;)V",
            at = @At("HEAD"), cancellable = true)
    private static void mclink$joinViaInvite2(Screen parent, Minecraft client, ServerAddress address, ServerData server, boolean async, TransferState transferState, CallbackInfo ci) {
        mclink$maybeJoin(client, parent, address, ci);
    }

    private static void mclink$maybeJoin(Minecraft client, Screen parent, ServerAddress address, CallbackInfo ci) {
        String invite = Invites.normalize(address.getHost());
        if (invite == null) {
            return;
        }
        ci.cancel();
        client.setScreen(new JoinRemoteScreen(parent, invite));
    }
}
