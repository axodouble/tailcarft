/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TailcatConfigTest {
    @Test
    void parsesFullInvitation() {
        TailcatConfig c = TailcatConfig.parse("{\"name\":\"My Server\",\"tailcat\":\"mcl1_abc\"}");
        assertEquals("My Server", c.name());
        assertEquals("mcl1_abc", c.invite());
        assertNull(c.icon());
    }

    @Test
    void wrapsBareTokenAndFillsDefaults() {
        TailcatConfig c = TailcatConfig.parse("{\"tailcat\":\"tc_test\"}");
        assertEquals("Tailcat Server", c.name());
        assertEquals("mcl1_eyJ2ZXJzaW9uIjoxLCJ0YWlsY2F0IjoidGNfdGVzdCJ9", c.invite());
    }

    @Test
    void blankNameFallsBackToDefault() {
        assertEquals("Tailcat Server", TailcatConfig.parse("{\"name\":\"  \",\"tailcat\":\"mcl1_a\"}").name());
    }

    @Test
    void carriesIconFileName() {
        assertEquals("icon.png", TailcatConfig.parse("{\"tailcat\":\"mcl1_a\",\"icon\":\"icon.png\"}").icon());
    }

    @Test
    void rejectsInvalidConfigs() {
        assertNull(TailcatConfig.parse("{}"));
        assertNull(TailcatConfig.parse("{\"tailcat\":\"nope\"}"));
        assertNull(TailcatConfig.parse("{\"tailcat\":42}"));
        assertNull(TailcatConfig.parse("{\"tailcat\":null}"));
        assertNull(TailcatConfig.parse("{\"tailcat\":{\"x\":1}}"));
        assertNull(TailcatConfig.parse("not json"));
        assertNull(TailcatConfig.parse("[1,2]"));
        assertNull(TailcatConfig.parse("null"));
    }

    @Test
    void iconLoading(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("good.png"), png(64, 64));
        assertNotNull(TailcatConfig.loadIcon(dir, "good.png"));

        Files.write(dir.resolve("small.png"), png(32, 32));
        assertNull(TailcatConfig.loadIcon(dir, "small.png"));

        Files.writeString(dir.resolve("notes.txt"), "hello");
        assertNull(TailcatConfig.loadIcon(dir, "notes.txt"));

        Files.write(dir.resolve("outside.png"), png(64, 64));
        assertNull(TailcatConfig.loadIcon(dir, "../" + "outside.png"));
        assertNull(TailcatConfig.loadIcon(dir, "sub/../../outside.png"));
        assertNull(TailcatConfig.loadIcon(dir, "missing.png"));
        assertNull(TailcatConfig.loadIcon(dir, ""));
        assertNull(TailcatConfig.loadIcon(dir, null));
    }

    static byte[] png(int w, int h) {
        byte[] b = new byte[33];
        b[0] = (byte) 0x89;
        b[1] = 'P';
        b[2] = 'N';
        b[3] = 'G';
        b[4] = 13;
        b[5] = 10;
        b[6] = 26;
        b[7] = 10;
        putInt(b, 8, 13);
        System.arraycopy("IHDR".getBytes(), 0, b, 12, 4);
        putInt(b, 16, w);
        putInt(b, 20, h);
        b[24] = 8;
        b[25] = 2;
        CRC32 crc = new CRC32();
        crc.update(b, 12, 17);
        putInt(b, 29, (int) crc.getValue());
        return b;
    }

    static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }
}
