/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvitesTest {
    static final String WRAPPED_TC_TEST = "mcl1_eyJ2ZXJzaW9uIjoxLCJ0YWlsY2F0IjoidGNfdGVzdCJ9";

    @Test
    void wrapsBareTokenByteExact() {
        assertEquals(WRAPPED_TC_TEST, Invites.wrap("tc_test"));
    }

    @Test
    void normalizeWrapsBareToken() {
        assertEquals(WRAPPED_TC_TEST, Invites.normalize("tc_test"));
    }

    @Test
    void normalizePassesThroughEnvelope() {
        assertEquals("mcl1_abc", Invites.normalize("mcl1_abc"));
        assertEquals("mcl1_abc", Invites.normalize("  mcl1_abc  "));
    }

    @Test
    void normalizeRejectsGarbage() {
        assertNull(Invites.normalize(null));
        assertNull(Invites.normalize(""));
        assertNull(Invites.normalize("   "));
        assertNull(Invites.normalize("mcl2_abc"));
        assertNull(Invites.normalize("tc"));
        assertNull(Invites.normalize("tc_bad char"));
        assertNull(Invites.normalize("tc\"quote"));
    }

    @Test
    void normalizeRejectsServerHosts() {
        assertNull(Invites.normalize("localhost"));
        assertNull(Invites.normalize("127.0.0.1"));
        assertNull(Invites.normalize("play.example.com"));
        assertNull(Invites.normalize("[::1]"));
        assertNull(Invites.normalize("tc.example.com"));
    }

    @Test
    void wrapRejectsNonTokens() {
        assertThrows(IllegalArgumentException.class, () -> Invites.wrap("mcl1_abc"));
        assertThrows(IllegalArgumentException.class, () -> Invites.wrap("x"));
        assertThrows(IllegalArgumentException.class, () -> Invites.wrap("tc\"quote"));
    }
}
