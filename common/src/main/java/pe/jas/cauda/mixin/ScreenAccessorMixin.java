/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package pe.jas.cauda.mixin;

import pe.jas.cauda.ScreenAccessor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Screen.class)
public abstract class ScreenAccessorMixin implements ScreenAccessor {
    @Shadow
    protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

    @Shadow
    protected abstract void removeWidget(GuiEventListener widget);

    @Override
    public <T extends GuiEventListener & Renderable & NarratableEntry> T cauda$addRenderableWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    @Override
    public void cauda$removeWidget(GuiEventListener widget) {
        this.removeWidget(widget);
    }
}
