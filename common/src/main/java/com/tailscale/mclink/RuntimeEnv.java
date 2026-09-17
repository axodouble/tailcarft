/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import java.nio.file.Path;

public interface RuntimeEnv {
    Path gameDir();

    Path configDir();

    boolean isDevelopment();

    String modVersion();
}
