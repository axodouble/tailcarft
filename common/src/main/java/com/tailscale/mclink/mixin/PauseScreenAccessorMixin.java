/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.PauseScreenAccessor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PauseScreen.class)
public abstract class PauseScreenAccessorMixin implements PauseScreenAccessor {
    @Shadow
    private Button disconnectButton;

    @Override
    public Button mclink$disconnectButton() {
        return this.disconnectButton;
    }
}
