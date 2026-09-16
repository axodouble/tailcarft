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

class TailcarftConfigTest {
    @Test
    void parsesFullInvitation() {
        TailcarftConfig c = TailcarftConfig.parse("{\"name\":\"My Server\",\"tailcat\":\"mcl1_abc\"}");
        assertEquals("My Server", c.name());
        assertEquals("mcl1_abc", c.invite());
        assertNull(c.icon());
    }

    @Test
    void wrapsBareTokenAndFillsDefaults() {
        TailcarftConfig c = TailcarftConfig.parse("{\"tailcat\":\"tc_test\"}");
        assertEquals("Tailcarft Server", c.name());
        assertEquals("mcl1_eyJ2ZXJzaW9uIjoxLCJ0YWlsY2F0IjoidGNfdGVzdCJ9", c.invite());
    }

    @Test
    void blankNameFallsBackToDefault() {
        assertEquals("Tailcarft Server", TailcarftConfig.parse("{\"name\":\"  \",\"tailcat\":\"mcl1_a\"}").name());
    }

    @Test
    void carriesIconFileName() {
        assertEquals("icon.png", TailcarftConfig.parse("{\"tailcat\":\"mcl1_a\",\"icon\":\"icon.png\"}").icon());
    }

    @Test
    void rejectsInvalidConfigs() {
        assertNull(TailcarftConfig.parse("{}"));
        assertNull(TailcarftConfig.parse("{\"tailcat\":\"nope\"}"));
        assertNull(TailcarftConfig.parse("{\"tailcat\":42}"));
        assertNull(TailcarftConfig.parse("{\"tailcat\":null}"));
        assertNull(TailcarftConfig.parse("{\"tailcat\":{\"x\":1}}"));
        assertNull(TailcarftConfig.parse("not json"));
        assertNull(TailcarftConfig.parse("[1,2]"));
        assertNull(TailcarftConfig.parse("null"));
    }

    @Test
    void iconLoading(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("good.png"), png(64, 64));
        assertNotNull(TailcarftConfig.loadIcon(dir, "good.png"));

        Files.write(dir.resolve("small.png"), png(32, 32));
        assertNull(TailcarftConfig.loadIcon(dir, "small.png"));

        Files.writeString(dir.resolve("notes.txt"), "hello");
        assertNull(TailcarftConfig.loadIcon(dir, "notes.txt"));

        Files.write(dir.resolve("outside.png"), png(64, 64));
        assertNull(TailcarftConfig.loadIcon(dir, "../" + "outside.png"));
        assertNull(TailcarftConfig.loadIcon(dir, "sub/../../outside.png"));
        assertNull(TailcarftConfig.loadIcon(dir, "missing.png"));
        assertNull(TailcarftConfig.loadIcon(dir, ""));
        assertNull(TailcarftConfig.loadIcon(dir, null));
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
