/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
            PngSize size = pngSize(bytes);
            if (size.width() != 64 || size.height() != 64) {
                LOG.warn("mclink icon {} is {}x{}, expected 64x64", fileName, size.width(), size.height());
                return null;
            }
            return bytes;
        } catch (Exception e) {
            LOG.warn("Ignoring invalid mclink icon {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    private record PngSize(int width, int height) {}

    private static PngSize pngSize(byte[] bytes) {
        if (bytes.length < 24
                || (bytes[0] & 0xFF) != 0x89
                || bytes[1] != (byte) 'P' || bytes[2] != (byte) 'N' || bytes[3] != (byte) 'G'
                || bytes[4] != (byte) 0x0D || bytes[5] != (byte) 0x0A || bytes[6] != (byte) 0x1A || bytes[7] != (byte) 0x0A
                || bytes[12] != (byte) 'I' || bytes[13] != (byte) 'H' || bytes[14] != (byte) 'D' || bytes[15] != (byte) 'R') {
            throw new IllegalArgumentException("not a PNG image");
        }
        int width = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
        int height = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
        return new PngSize(width, height);
    }

    private static String stringOrNull(JsonObject o, String key) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString();
        }
        return null;
    }
}
