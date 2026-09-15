/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public final class Invites {
    private static final Pattern TOKEN = Pattern.compile("tc[A-Za-z0-9_-]+");

    private Invites() {}

    public static String wrap(String token) {
        String tc = token.trim();
        if (!TOKEN.matcher(tc).matches()) {
            throw new IllegalArgumentException("not a Tailcat token");
        }
        String json = "{\"version\":1,\"tailcat\":\"" + tc + "\"}";
        return "mcl1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("mcl1_")) {
            return v;
        }
        if (v.startsWith("tc")) {
            try {
                return wrap(v);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
