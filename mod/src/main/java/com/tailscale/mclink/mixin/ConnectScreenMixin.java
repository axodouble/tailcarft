/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.Invites;
import com.tailscale.mclink.JoinRemoteScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.gui.screen.multiplayer.ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "connect(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;Lnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"), cancellable = true)
    private static void mclink$joinViaInvite(MinecraftClient client, ServerAddress address, ServerInfo server, CookieStorage cookieStorage, CallbackInfo ci) {
        mclink$maybeJoin(client, client.currentScreen, address, ci);
    }

    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"), cancellable = true)
    private static void mclink$joinViaInvite2(Screen parent, MinecraftClient client, ServerAddress address, ServerInfo server, boolean async, CookieStorage cookieStorage, CallbackInfo ci) {
        mclink$maybeJoin(client, parent, address, ci);
    }

    private static void mclink$maybeJoin(MinecraftClient client, Screen parent, ServerAddress address, CallbackInfo ci) {
        String invite = Invites.normalize(address.getAddress());
        if (invite == null) {
            return;
        }
        ci.cancel();
        client.setScreen(new JoinRemoteScreen(parent, invite));
    }
}
