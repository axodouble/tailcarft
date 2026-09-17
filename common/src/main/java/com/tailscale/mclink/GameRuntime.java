/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

public final class GameRuntime {
    private static RuntimeEnv env;

    private GameRuntime() {}

    public static void init(RuntimeEnv value) {
        env = value;
    }

    public static RuntimeEnv get() {
        if (env == null) {
            throw new IllegalStateException("GameRuntime is not initialized");
        }
        return env;
    }
}
