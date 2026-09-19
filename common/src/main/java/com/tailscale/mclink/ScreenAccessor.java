/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

public interface ScreenAccessor {
    <T extends GuiEventListener & Renderable & NarratableEntry> T mclink$addRenderableWidget(T widget);

    void mclink$removeWidget(GuiEventListener widget);
}
