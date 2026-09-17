/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class HelperEventTest {
    @Test void parsesHostReady() throws Exception { assertEquals("mcl1_x", HelperEvent.parse("{\"type\":\"ready\",\"mode\":\"host\",\"invite\":\"mcl1_x\"}").invite()); }
    @Test void rejectsUnknownFields() { assertThrows(IOException.class, () -> HelperEvent.parse("{\"type\":\"stopped\",\"extra\":1}")); }
    @Test void rejectsNonLoopbackJoin() { assertThrows(IOException.class, () -> HelperEvent.parse("{\"type\":\"ready\",\"mode\":\"join\",\"address\":\"0.0.0.0:1\"}")); }
    @Test void rejectsMalformed() { assertThrows(IOException.class, () -> HelperEvent.parse("not json")); }
}
