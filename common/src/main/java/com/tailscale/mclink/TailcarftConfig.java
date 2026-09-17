/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.PngInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public record TailcarftConfig(String name, String invite, String icon) {
    public static final String DEFAULT_NAME = "Tailcarft Server";
    private static final Logger LOG = LoggerFactory.getLogger("mclink");

    public static TailcarftConfig load() {
        try {
            Path path = GameRuntime.get().configDir().resolve("mclink.json");
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return parse(Files.readString(path));
        } catch (Exception e) {
            LOG.warn("Ignoring unreadable mclink config: {}", e.getMessage());
            return null;
        }
    }

    public static TailcarftConfig parse(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            String invite = Invites.normalize(stringOrNull(o, "tailcat"));
            if (invite == null) {
                return null;
            }
            String name = stringOrNull(o, "name");
            if (name == null || name.isBlank()) {
                name = DEFAULT_NAME;
            }
            return new TailcarftConfig(name.trim(), invite, stringOrNull(o, "icon"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static byte[] loadIcon(String fileName) {
        return loadIcon(GameRuntime.get().configDir(), fileName);
    }

    public static byte[] loadIcon(Path configDir, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path dir = configDir.toAbsolutePath().normalize();
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            PngInfo metadata = PngInfo.fromBytes(bytes);
            if (metadata.width() != 64 || metadata.height() != 64) {
                LOG.warn("mclink icon {} is {}x{}, expected 64x64", fileName, metadata.width(), metadata.height());
                return null;
            }
            return bytes;
        } catch (Exception e) {
            LOG.warn("Ignoring invalid mclink icon {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    private static String stringOrNull(JsonObject o, String key) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString();
        }
        return null;
    }
}
