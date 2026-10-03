// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.tailscale.mclink.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Quilt does not register mod asset directories as resource packs (unlike
 * Fabric/Forge), and the vanilla {@link ClientLanguage} is loaded too early to
 * be mixed, so the mod's lang files are invisible to the translation loader.
 * On the first tick after the resource reload, copy the active translation
 * map, merge the mod's lang files into it, and replace the instance with a
 * fresh {@link ClientLanguage} built from the merged map.
 *
 * <p>Reflection is resolved by type and constructor descriptor rather than by
 * member name, so it is independent of the intermediary mapping used at
 * runtime.
 */
@Mixin(Minecraft.class)
public abstract class ModLanguageMixin {
    @Unique
    private boolean mclink$modLangMerged;

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void mclink$mergeModLang(CallbackInfo ci) {
        if (mclink$modLangMerged) {
            return;
        }
        Language current = Language.getInstance();
        if (!(current instanceof ClientLanguage)) {
            return;
        }
        mclink$modLangMerged = true;
        try {
            Field storage = mapField(ClientLanguage.class);
            storage.setAccessible(true);
            Map<String, String> merged =
                    new HashMap<>((Map<String, String>) storage.get(current));
            loadLangFile(merged, "en_us");
            Constructor<? extends Language> ctor =
                    ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
            ctor.setAccessible(true);
            Language.inject((Language) ctor.newInstance(merged, false));
        } catch (Throwable t) {
            // Never break the client over a translation failure; the button
            // degrades to the raw key rather than crashing.
        }
    }

    private static Field mapField(Class<?> cls) throws NoSuchFieldException {
        for (Field f : cls.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        throw new NoSuchFieldException("no Map field in " + cls.getName());
    }

    private static void loadLangFile(Map<String, String> map, String lang) {
        try (InputStream in = ModLanguageMixin.class.getResourceAsStream(
                "/assets/mclink/lang/" + lang + ".json")) {
            if (in != null) {
                Language.loadFromJson(in, map::put);
            }
        } catch (IOException ignored) {
        }
    }
}
