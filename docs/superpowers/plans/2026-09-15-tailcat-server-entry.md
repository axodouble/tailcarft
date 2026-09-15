# Implementation Plan: Persistent Tailcat server entry + dedicated-server hosting

Date: 2026-09-15
Spec: `docs/superpowers/specs/2026-09-15-tailcat-server-entry-design.md` (approved)

## Goal

Config-gated, pinned, non-deletable Tailcat server entry at the top of the
1.21.1 multiplayer list with one-click join; dedicated servers auto-host the
Tailcat tunnel and keep the same invitation string across restarts (stable
node key + DERP region), so a modpack can ship a pre-filled client config.

## Global constraints

- Minecraft `~1.21.1`, yarn `1.21.1+build.3`, loader `>=0.16.10`,
  fabric-api `0.115.6+1.21.1`, Java 21 (pins in `gradle.properties`).
- Mod id: `tailcat-for-minecraft`. Jar:
  `mod/build/libs/tailcat-for-minecraft-0.1.0.jar`.
- Marker address (never a real host): `mclink:tailcat`.
- Client config: `<gamedir>/config/mclink.json` →
  `{"name":"...","tailcat":"mcl1_...|tc...","icon":"file.png"}`.
- Server state: `<serverroot>/tailcat-for-minecraft/state.json`, mode 0600,
  written atomically. Shape (no version key):
  `{"key":"node:<hex>","region":<int>,"invite":"mcl1_..."}`.
- License headers (AGENTS.md): every **new** source file in this plan starts
  with the copyright header; the two **existing** source files modified here
  (`helper/cmd/mclink-helper/main.go`, `JoinRemoteScreen.java`) get the header
  in the same edit. No headers on JSON/resources.
- Java style: no comments (matches existing code). Go: one-line package doc
  comment where a package is created (matches `invite.go`).
- Commit style: `scope: summary` (lowercase).

### Go toolchain + test command

The helper builds with a pinned Tailscale Go (`go1.26.5`, rev
`63ae404c8203317fd3c82d972e5dc8f0fcb425cb`) that `scripts/build-natives.sh`
downloads on first use. Use the same toolchain for `go test`:

```bash
cd /home/jasper/Projects/jaspercraft
export TSGO="${XDG_CACHE_HOME:-$HOME/.cache}/mclink/tsgo/63ae404c8203317fd3c82d972e5dc8f0fcb425cb/bin/go"
[[ -x "$TSGO" ]] || bash scripts/build-natives.sh   # downloads toolchain (also builds natives)
export GOROOT="${TSGO%/bin/go}"
export GOCACHE="${XDG_CACHE_HOME:-$HOME/.cache}/mclink/go-build/63ae404c8203317fd3c82d972e5dc8f0fcb425cb"
export CGO_ENABLED=0
(cd helper && "$TSGO" test ./...)
```

Java tests: `./gradlew :mod:test`. Mixin targets are validated by
fabric-loom's annotation processor during `:mod:compileJava` — a wrong method
or field name in a mixin fails the build.

## File map

| File | Action | Task |
|---|---|---|
| `helper/internal/state/state.go` | new | 1 |
| `helper/internal/state/state_test.go` | new | 1 |
| `helper/cmd/mclink-helper/derp.go` | new | 2 |
| `helper/cmd/mclink-helper/derp_test.go` | new | 2 |
| `helper/cmd/mclink-helper/main.go` | edit (+header) | 2 |
| `mod/src/main/java/com/tailscale/mclink/Invites.java` | new | 3 |
| `mod/src/test/java/com/tailscale/mclink/InvitesTest.java` | new | 3 |
| `mod/src/main/java/com/tailscale/mclink/TailcatConfig.java` | new | 4 |
| `mod/src/test/java/com/tailscale/mclink/TailcatConfigTest.java` | new | 4 |
| `mod/src/main/java/com/tailscale/mclink/TailcatServerEntry.java` | new | 5 |
| `mod/src/main/java/com/tailscale/mclink/mixin/ServerListAccessor.java` | new | 5 |
| `mod/src/main/java/com/tailscale/mclink/mixin/MultiplayerScreenMixin.java` | new | 5 |
| `mod/src/main/java/com/tailscale/mclink/mixin/ServerListMixin.java` | new | 5 |
| `mod/src/main/java/com/tailscale/mclink/JoinRemoteScreen.java` | edit (+header, prefill ctor) | 5 |
| `mod/src/main/resources/mclink.mixins.json` | edit | 5 |
| `mod/src/main/resources/assets/mclink/lang/en_us.json` | edit | 5 |
| `mod/src/main/java/com/tailscale/mclink/McLinkServer.java` | new | 6 |
| `mod/src/main/java/com/tailscale/mclink/ServerHost.java` | new | 6 |
| `mod/src/main/resources/fabric.mod.json` | edit | 6 |
| `mod/src/main/resources/assets/mclink/native/**` + `checksums.json` | regenerated | 7 |

---

## Task 1: Go — `state` package (host identity persistence)

**Files:** `helper/internal/state/state.go` (new), `helper/internal/state/state_test.go` (new)

### Step 1.1 — Write the failing tests

`helper/internal/state/state_test.go`:

```go
// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package state

import (
	"os"
	"path/filepath"
	"testing"

	"tailscale.com/types/key"
)

func TestSaveLoadRoundTrip(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")
	priv := key.NewNode()
	in := &File{Key: priv, Region: 7, Invite: "mcl1_test"}
	if err := Save(path, in); err != nil {
		t.Fatal(err)
	}
	got, err := Load(path)
	if err != nil {
		t.Fatal(err)
	}
	if !got.Key.Equal(priv) {
		t.Fatal("round-tripped key differs")
	}
	if got.Key.Public() != priv.Public() {
		t.Fatal("round-tripped public key differs")
	}
	if got.Region != 7 || got.Invite != "mcl1_test" {
		t.Fatalf("unexpected fields: %+v", got)
	}
}

func TestSavePermissions(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")
	if err := Save(path, &File{Key: key.NewNode(), Region: 3, Invite: "mcl1_x"}); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if p := info.Mode().Perm(); p != 0o600 {
		t.Fatalf("mode = %o, want 600", p)
	}
}

func TestLoadMissing(t *testing.T) {
	if _, err := Load(filepath.Join(t.TempDir(), "absent.json")); err == nil {
		t.Fatal("want error for missing file")
	}
}

func TestLoadCorrupt(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")
	if err := os.WriteFile(path, []byte("{nope"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Load(path); err == nil {
		t.Fatal("want error for corrupt file")
	}
}

func TestLoadMissingKey(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")
	if err := os.WriteFile(path, []byte(`{"region":3,"invite":"mcl1_x"}`), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Load(path); err == nil {
		t.Fatal("want error for file without a node key")
	}
}
```

### Step 1.2 — Run and confirm failure

Go test command from Global constraints. Expect compile failure
(`undefined: File/Load/Save`).

### Step 1.3 — Implement

`helper/internal/state/state.go`:

```go
// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Package state persists the Tailcat host identity (node key and DERP
// region) so a server keeps the same invitation across restarts.
package state

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"tailscale.com/types/key"
)

// File is the persisted host identity. Key is the source of truth; Invite is
// a convenience cache recomputed from Key and the chosen region on load.
type File struct {
	Key    key.NodePrivate `json:"key"`
	Region int             `json:"region"`
	Invite string          `json:"invite"`
}

// Load reads the state file at path. A missing file returns the raw
// *fs.PathError (errors.Is(err, os.ErrNotExist) is true); a corrupt file
// returns a parse error.
func Load(path string) (*File, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var f File
	if err := json.Unmarshal(b, &f); err != nil {
		return nil, fmt.Errorf("parse state file %s: %w", path, err)
	}
	if f.Key.IsZero() {
		return nil, fmt.Errorf("state file %s has no node key", path)
	}
	return &f, nil
}

// Save writes f to path atomically (temp file + rename) with 0600
// permissions, because the file contains a private key.
func Save(path string, f *File) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), ".mclink-state-*.tmp")
	if err != nil {
		return err
	}
	name := tmp.Name()
	defer os.Remove(name)
	if err := tmp.Chmod(0o600); err != nil {
		tmp.Close()
		return err
	}
	if err := json.NewEncoder(tmp).Encode(f); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(name, path)
}
```

Notes:
- `key.NodePrivate` implements `encoding.TextMarshaler`/`TextUnmarshaler`
  (`"node:" + hex`), so JSON (de)serialization of the key field is built in.
- The `errors` import is used implicitly via `errors.Is` on the caller side;
  this package only returns wrapped errors.

### Step 1.4 — Run and confirm pass

Go test command → `ok ... state`. Also `gofmt -l helper/` → empty.

### Step 1.5 — Commit

```
git add helper/internal/state/
git commit -m "helper: add state package for persistent host identity"
```

---

## Task 2: Go — `host --state-file` (stable invitations)

**Files:** `helper/cmd/mclink-helper/derp.go` (new),
`helper/cmd/mclink-helper/derp_test.go` (new),
`helper/cmd/mclink-helper/main.go` (edit, +license header)

### Step 2.1 — Write the failing tests

`helper/cmd/mclink-helper/derp_test.go`:

```go
// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package main

import (
	"testing"

	"tailscale.com/tailcfg"
)

func testRegionMap() *tailcfg.DERPMap {
	dm := new(tailcfg.DERPMap)
	for _, id := range []int{3, 7, 11} {
		dm.Regions[id] = &tailcfg.DERPRegion{RegionID: id, RegionCode: string(rune('a' + id))}
	}
	return dm
}

func TestSelectRegionPrefersBest(t *testing.T) {
	r := selectRegion(testRegionMap(), 7)
	if r == nil || r.RegionID != 7 {
		t.Fatalf("selectRegion = %v, want region 7", r)
	}
}

func TestSelectRegionUnknownBestFallsBackToMap(t *testing.T) {
	dm := testRegionMap()
	r := selectRegion(dm, 99)
	if r == nil {
		t.Fatal("selectRegion returned nil")
	}
	if _, ok := dm.Regions[r.RegionID]; !ok {
		t.Fatalf("region %d is not in the map", r.RegionID)
	}
}

func TestSelectRegionZeroBestPicksFromMap(t *testing.T) {
	dm := testRegionMap()
	r := selectRegion(dm, 0)
	if r == nil {
		t.Fatal("selectRegion returned nil for best=0")
	}
	if _, ok := dm.Regions[r.RegionID]; !ok {
		t.Fatalf("region %d is not in the map", r.RegionID)
	}
}
```

Run → compile failure (`undefined: selectRegion`).

### Step 2.2 — Implement `derp.go`

`helper/cmd/mclink-helper/derp.go`:

```go
// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"maps"
	"math/rand"
	"net/http"
	"slices"
	"time"

	"github.com/tailscale/tailcat"
	"tailscale.com/tailcfg"
)

// fetchDerpMap downloads the DERP region map the same way
// tailcat.ConnInfo.Expand does (default URL, Tailcat-Mode header).
func fetchDerpMap(ctx context.Context) (*tailcfg.DERPMap, error) {
	ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, tailcat.DefaultDERPMapURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Tailcat-Mode", "server")
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetching DERP map from %s: %w", tailcat.DefaultDERPMapURL, err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("fetching DERP map from %s: %s", tailcat.DefaultDERPMapURL, res.Status)
	}
	dm := new(tailcfg.DERPMap)
	if err := json.NewDecoder(io.LimitReader(res.Body, 10<<20)).Decode(dm); err != nil {
		return nil, fmt.Errorf("fetching DERP map from %s: invalid JSON: %w", tailcat.DefaultDERPMapURL, err)
	}
	if len(dm.Regions) == 0 {
		return nil, errors.New("DERP map has no regions")
	}
	return dm, nil
}

// selectRegion returns the region to host in: best if present in the map,
// otherwise a random region from the map. dm must have at least one region.
func selectRegion(dm *tailcfg.DERPMap, best int) *tailcfg.DERPRegion {
	if best != 0 {
		if r, ok := dm.Regions[best]; ok {
			return r
		}
	}
	ids := slices.Sorted(maps.Keys(dm.Regions))
	return dm.Regions[ids[rand.Intn(len(ids))]]
}
```

### Step 2.3 — Edit `main.go`

Apply exactly these four hunks (the file currently has **no** license header —
hunk 1 adds it per AGENTS.md):

**Hunk 1 — header + imports** (file start):

```go
// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/tailscale/mclink/helper/internal/invite"
	"github.com/tailscale/mclink/helper/internal/state"
	"github.com/tailscale/tailcat"
	"tailscale.com/types/key"
	"tailscale.com/wgengine/filter"
)
```

**Hunk 2 — new flag** (in `runHost`, after the `-target` declaration):

```go
	target := fs.String("target", "", "loopback Minecraft address")
	stateFile := fs.String("state-file", "", "file to persist the host identity (node key and DERP region)")
	if err := fs.Parse(args); err != nil {
```

**Hunk 3 — state-aware region selection** (in `runHost`, replace the block
from `priv := key.NewNode()` through `region := ci.Region[0]`):

```go
	dm, err := fetchDerpMap(ctx)
	if err != nil {
		return "derp_unreachable", err
	}

	priv := key.NewNode()
	savedRegion := 0
	if *stateFile != "" {
		st, lerr := state.Load(*stateFile)
		switch {
		case lerr == nil:
			priv, savedRegion = st.Key, st.Region
		case errors.Is(lerr, os.ErrNotExist):
			log.Printf("no Tailcat state at %s; generating a new identity", *stateFile)
		default:
			log.Printf("ignoring unreadable Tailcat state at %s: %v", *stateFile, lerr)
		}
	}

	region := dm.Regions[savedRegion]
	if region == nil {
		if savedRegion != 0 {
			log.Printf("Tailcat region %d is no longer in the DERP map; selecting a new one", savedRegion)
		}
		best, perr := tailcat.PickBestRegion(ctx, dm)
		if perr != nil {
			log.Printf("Tailcat netcheck failed: %v; picking a random region", perr)
			best = 0
		}
		region = selectRegion(dm, best)
	}
	server, err := tailcat.NewServer(priv, log.Printf, region)
```

**Hunk 4 — persist state** (in `runHost`, right after the `encoded` invite is
computed):

```go
	encoded, err := invite.New(string(publicCI.ConnBlob())).Encode()
	if err != nil {
		return "helper_failed", err
	}
	if *stateFile != "" {
		if serr := state.Save(*stateFile, &state.File{Key: priv, Region: region.RegionID, Invite: encoded}); serr != nil {
			log.Printf("could not persist Tailcat state: %v", serr)
		}
	}
	sem := make(chan struct{}, maxConnections)
```

Everything else in `main.go` (join path, event protocol, signal handling) is
unchanged. Behavior with `--state-file` unset: fresh key + netcheck region,
no file touched — identical to before.

### Step 2.4 — Run and confirm pass

Go test command → all packages `ok`. `gofmt -l helper/` → empty.

### Step 2.5 — Manual E2E (needs network; run once)

```bash
cd helper && "$TSGO" build -tags=ts_omit_ssh -o /tmp/mclink-helper ./cmd/mclink-helper
cd /tmp && rm -f tc-state.json
/tmp/mclink-helper host --target 127.0.0.1:25565 --state-file /tmp/tc-state.json > /tmp/tc1.log &
sleep 10; kill %1
/tmp/mclink-helper host --target 127.0.0.1:25565 --state-file /tmp/tc-state.json > /tmp/tc2.log &
sleep 10; kill %1
grep -o '"invite":"mcl1[^"]*"' /tmp/tc1.log /tmp/tc2.log   # both lines identical
stat -c '%a' /tmp/tc-state.json                            # 600
cat /tmp/tc-state.json                                     # key, region, invite
```

Expectation: both runs print the **same** `mcl1_` invite; the state file is
0600. Corrupt-file path: `echo junk > /tmp/tc-state.json`, run again → warning
on stderr, new identity generated, file rewritten.

### Step 2.6 — Commit

```
git add helper/cmd/mclink-helper/
git commit -m "helper: host with --state-file for stable invitations"
```

---

## Task 3: Java — `Invites` (byte-exact envelope)

**Files:** `mod/src/main/java/com/tailscale/mclink/Invites.java` (new),
`mod/src/test/java/com/tailscale/mclink/InvitesTest.java` (new)

### Step 3.1 — Write the failing tests

`mod/src/test/java/com/tailscale/mclink/InvitesTest.java`:

```java
// (license header per AGENTS.md)

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
    void wrapRejectsNonTokens() {
        assertThrows(IllegalArgumentException.class, () -> Invites.wrap("mcl1_abc"));
        assertThrows(IllegalArgumentException.class, () -> Invites.wrap("x"));
        assertThrows(IllegalArgumentException.class, () -> Invites.wrap("tc\"quote"));
    }
}
```

The `WRAPPED_TC_TEST` fixture is the byte-exact output of the Go encoder
(`helper/internal/invite`): Base64url-no-padding of the exact JSON string
`{"version":1,"tailcat":"tc_test"}`. Run `./gradlew :mod:test` → compile
failure (`cannot find symbol: Invites`).

### Step 3.2 — Implement

`mod/src/main/java/com/tailscale/mclink/Invites.java`:

```java
// (license header per AGENTS.md)

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
```

The token charset restriction is what guarantees the hand-built JSON string is
byte-identical to Go's `json.Marshal` output (Go would HTML-escape or quote
anything else).

### Step 3.3 — Run and confirm pass

`./gradlew :mod:test` → all green.

### Step 3.4 — Commit

```
git add mod/src/main/java/com/tailscale/mclink/Invites.java mod/src/test/java/com/tailscale/mclink/InvitesTest.java
git commit -m "mod: add invite wrapping/validation (Invites)"
```

---

## Task 4: Java — `TailcatConfig` (config load + icon)

**Files:** `mod/src/main/java/com/tailscale/mclink/TailcatConfig.java` (new),
`mod/src/test/java/com/tailscale/mclink/TailcatConfigTest.java` (new)

### Step 4.1 — Write the failing tests

`mod/src/test/java/com/tailscale/mclink/TailcatConfigTest.java`:

```java
// (license header per AGENTS.md)

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
```

Run `./gradlew :mod:test` → compile failure (`cannot find symbol:
TailcatConfig`).

### Step 4.2 — Implement

`mod/src/main/java/com/tailscale/mclink/TailcatConfig.java`:

```java
// (license header per AGENTS.md)

package com.tailscale.mclink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.PngMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public record TailcatConfig(String name, String invite, String icon) {
    public static final String DEFAULT_NAME = "Tailcat Server";
    private static final Logger LOG = LoggerFactory.getLogger("mclink");

    public static TailcatConfig load() {
        try {
            Path path = FabricLoader.getInstance().getGameDir().resolve("config").resolve("mclink.json");
            if (!Files.isRegularFile(path)) {
                return null;
            }
            return parse(Files.readString(path));
        } catch (Exception e) {
            LOG.warn("Ignoring unreadable mclink config: {}", e.getMessage());
            return null;
        }
    }

    public static TailcatConfig parse(String json) {
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
            return new TailcatConfig(name.trim(), invite, stringOrNull(o, "icon"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static byte[] loadIcon(String fileName) {
        return loadIcon(FabricLoader.getInstance().getGameDir().resolve("config"), fileName);
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
            PngMetadata metadata = PngMetadata.fromBytes(bytes);
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
```

Design notes:
- `parse(String)` is pure (Gson + `Invites` only) — the unit-testable seam.
  `load()` wraps it with the game-dir path; both are re-read on every
  `MultiplayerScreen` init (task 5), so config edits apply on F5.
- `loadIcon(Path, String)` is the testable seam (temp dirs); `loadIcon(String)`
  resolves against `<gamedir>/config/`. Path is normalized and must stay
  inside the config dir. Validity = readable + valid PNG metadata + exactly
  64x64 (same requirement vanilla's `ServerInfo.validateFavicon` uses, via the
  same `PngMetadata` utility). Any failure → null + warning, never an
  exception.
- `PngMetadata` is a pure byte-parsing utility with no static state; if it
  somehow fails to load in the plain JUnit environment (NoClassDefFoundError),
  replace `PngMetadata.fromBytes` in `loadIcon(Path, String)` with a minimal
  local IHDR reader (signature check + big-endian width/height at offsets
  16/20) and keep the same tests.

### Step 4.3 — Run and confirm pass

`./gradlew :mod:test` → all green.

### Step 4.4 — Commit

```
git add mod/src/main/java/com/tailscale/mclink/TailcatConfig.java mod/src/test/java/com/tailscale/mclink/TailcatConfigTest.java
git commit -m "mod: add client config with icon loading (TailcatConfig)"
```

---

## Task 5: Java — client marker entry (mixins + join screen)

**Files:** `TailcatServerEntry.java` (new), `mixin/ServerListAccessor.java`
(new), `mixin/MultiplayerScreenMixin.java` (new), `mixin/ServerListMixin.java`
(new), `JoinRemoteScreen.java` (edit, +header), `mclink.mixins.json` (edit),
`en_us.json` (edit)

Verified 1.21.1/yarn facts this task relies on:
- `ServerInfo`: public fields `name`, `address`, `label`, `playerCountLabel`,
  `long ping`; `setStatus(Status)`, `setFavicon(@Nullable byte[])`;
  `ServerType.OTHER`. Pinging only starts from `Status.INITIAL`, so a
  pre-set entry is never pinged.
- `ServerList`: private `List<ServerInfo> servers`; `saveFile()` is
  **synchronous** (builds the NBT from `this.servers` inside the method);
  `get(String address)` is public.
- `MultiplayerScreen`: protected `serverListWidget`, private
  `buttonEdit`/`buttonDelete`/`serverList`; private
  `connect(ServerInfo)` is the funnel for Join button, Enter/Space, and
  double-click; protected `updateButtonActivationStates()`; F5 refresh
  creates a **new** `MultiplayerScreen` (so `init` runs again);
  `getServerList()` is public.
- `Screen.client` is protected. `ServerEntry.getServer()` is public.
- `ServerAddress.parse("mclink:tailcat")` yields `INVALID` (non-numeric
  port), so the join intercept **must** cancel even when the config is
  missing (a no-op click is safe; falling through to vanilla is not).
- A mixin class extending a class with no no-arg constructor needs an
  explicit `private XMixin() { super(null); }` constructor (never invoked by
  the weaver).

### Step 5.1 — `TailcatServerEntry.java` (new)

```java
// (license header per AGENTS.md)

package com.tailscale.mclink;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public final class TailcatServerEntry {
    public static final String MARKER = "mclink:tailcat";

    private TailcatServerEntry() {}

    public static ServerInfo create(TailcatConfig config) {
        ServerInfo info = new ServerInfo(config.name(), MARKER, ServerInfo.ServerType.OTHER);
        info.setStatus(ServerInfo.Status.SUCCESSFUL);
        info.ping = 1;
        info.label = Text.translatable("mclink.server.label");
        info.playerCountLabel = Text.empty();
        byte[] icon = TailcatConfig.loadIcon(config.icon());
        if (icon != null) {
            info.setFavicon(icon);
        }
        return info;
    }
}
```

### Step 5.2 — `mixin/ServerListAccessor.java` (new)

```java
// (license header per AGENTS.md)

package com.tailscale.mclink.mixin;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ServerList.class)
public interface ServerListAccessor {
    @Accessor("servers") List<ServerInfo> mclink$servers();
}
```

### Step 5.3 — `mixin/MultiplayerScreenMixin.java` (new)

```java
// (license header per AGENTS.md)

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.JoinRemoteScreen;
import com.tailscale.mclink.TailcatConfig;
import com.tailscale.mclink.TailcatServerEntry;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends MultiplayerScreen {
    private static final Logger LOG = LoggerFactory.getLogger("mclink");

    @Shadow private ButtonWidget buttonEdit;
    @Shadow private ButtonWidget buttonDelete;

    private MultiplayerScreenMixin() {
        super(null);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void mclink$ensureMarkerEntry(CallbackInfo ci) {
        TailcatConfig config = TailcatConfig.load();
        if (config == null) {
            return;
        }
        ServerList list = this.getServerList();
        if (list.get(TailcatServerEntry.MARKER) != null) {
            return;
        }
        ((ServerListAccessor) (Object) list).mclink$servers().add(0, TailcatServerEntry.create(config));
        this.serverListWidget.setServers(list);
    }

    @Inject(method = "connect(Lnet/minecraft/client/network/ServerInfo;)V", at = @At("HEAD"), cancellable = true)
    private void mclink$joinViaTailcat(ServerInfo entry, CallbackInfo ci) {
        if (!TailcatServerEntry.MARKER.equals(entry.address)) {
            return;
        }
        TailcatConfig config = TailcatConfig.load();
        if (config == null) {
            LOG.warn("Tailcat server entry clicked but config is missing; ignoring");
            ci.cancel();
            return;
        }
        this.client.setScreen(new JoinRemoteScreen(this, config.invite()));
        ci.cancel();
    }

    @Inject(method = "updateButtonActivationStates()V", at = @At("TAIL"))
    private void mclink$disableMarkerButtons(CallbackInfo ci) {
        MultiplayerServerListWidget.Entry entry = this.serverListWidget.getSelectedOrNull();
        if (entry instanceof MultiplayerServerListWidget.ServerEntry serverEntry
                && TailcatServerEntry.MARKER.equals(serverEntry.getServer().address)) {
            this.buttonEdit.active = false;
            this.buttonDelete.active = false;
        }
    }
}
```

Notes:
- `extends MultiplayerScreen` gives compile-time access to `client`
  (protected on `Screen`) and `serverListWidget` (protected). The private
  buttons come in via `@Shadow`. `ServerList` is reached through the public
  `getServerList()` + accessor cast.
- The `init` TAIL dedupe (`list.get(MARKER) != null`) covers first open, F5
  refresh (new screen → new `ServerList` from `servers.dat`, which never
  contains the marker), and resizes (entries intact, marker already present).
- The `(Object)` double-cast is required because the compiler doesn't know
  `ServerList` will implement `ServerListAccessor` at weave time.

### Step 5.4 — `mixin/ServerListMixin.java` (new)

```java
// (license header per AGENTS.md)

package com.tailscale.mclink.mixin;

import com.tailscale.mclink.TailcatServerEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerList.class)
public abstract class ServerListMixin {
    @Unique private ServerInfo mclink$marker;

    @Inject(method = "swapEntries(II)V", at = @At("HEAD"), cancellable = true)
    private void mclink$blockMarkerMove(int a, int b, CallbackInfo ci) {
        List<ServerInfo> servers = ((ServerListAccessor) (Object) this).mclink$servers();
        if (TailcatServerEntry.MARKER.equals(servers.get(a).address)
                || TailcatServerEntry.MARKER.equals(servers.get(b).address)) {
            ci.cancel();
        }
    }

    @Inject(method = "saveFile()V", at = @At("HEAD"))
    private void mclink$stashMarkerBeforeSave(CallbackInfo ci) {
        List<ServerInfo> servers = ((ServerListAccessor) (Object) this).mclink$servers();
        for (int i = 0; i < servers.size(); i++) {
            if (TailcatServerEntry.MARKER.equals(servers.get(i).address)) {
                mclink$marker = servers.remove(i);
                break;
            }
        }
    }

    @Inject(method = "saveFile()V", at = @At("TAIL"))
    private void mclink$restoreMarkerAfterSave(CallbackInfo ci) {
        if (mclink$marker != null) {
            ((ServerListAccessor) (Object) this).mclink$servers().add(0, mclink$marker);
            mclink$marker = null;
        }
    }
}
```

`saveFile()` is synchronous (verified: it builds the NBT list from
`this.servers` inside the method body and writes inline), so stash-at-HEAD /
restore-at-TAIL guarantees the marker never reaches `servers.dat`.

### Step 5.5 — `JoinRemoteScreen.java` (edit)

Full new content (adds the license header per AGENTS.md and the prefilled
auto-joining constructor; `begin()`/`close()`/`render()` are unchanged):

```java
/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class JoinRemoteScreen extends Screen {
    private final Screen parent;
    private final String invitation;
    private boolean autoStarted;
    private TextFieldWidget invite;
    private ButtonWidget connect;
    private Text status = Text.empty();

    public JoinRemoteScreen(Screen parent) {
        this(parent, null);
    }

    public JoinRemoteScreen(Screen parent, String invitation) {
        super(Text.translatable("mclink.join"));
        this.parent = parent;
        this.invitation = invitation;
    }

    @Override
    protected void init() {
        invite = new TextFieldWidget(textRenderer, width / 2 - 150, height / 2 - 22, 300, 20,
                Text.translatable("mclink.invite"));
        invite.setMaxLength(8192);
        invite.setPlaceholder(Text.translatable("mclink.invite_hint"));
        addDrawableChild(invite);
        connect = addDrawableChild(ButtonWidget.builder(Text.translatable("mclink.connect"), b -> begin())
                .dimensions(width / 2 - 102, height / 2 + 12, 100, 20).build());
        connect.active = false;
        invite.setChangedListener(value -> connect.active = value.trim().startsWith("mcl1_"));
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(width / 2 + 2, height / 2 + 12, 100, 20).build());
        setInitialFocus(invite);
        if (invitation != null && !autoStarted) {
            autoStarted = true;
            invite.setText(invitation);
            begin();
        }
    }

    private void begin() {
        connect.active = false;
        invite.setEditable(false);
        status = Text.translatable("mclink.starting");
        McLinkClient.state().join(client, parent, invite.getText()).whenComplete((ignored, error) -> {
            if (error != null) {
                client.execute(() -> {
                    status = Text.literal("Could not connect: " + ShareScreen.rootMessage(error));
                    invite.setEditable(true);
                    connect.active = true;
                });
            }
        });
    }

    @Override
    public void close() {
        McLinkClient.state().stop();
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 58, 0xffffff);
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height / 2 + 46, 0xffaaaa);
    }
}
```

Ordering matters in `init`: the `connect` button is created **before**
`invite.setText(invitation)` because `setText` fires the changed listener,
which touches `connect`. The one-shot `autoStarted` flag means resizes
(re-`init`) never re-trigger `begin()`. Routing the join through this screen
(instead of calling `ScreenState.join` from the mixin) keeps
`ScreenState.tick`'s session-lifetime guard correct and reuses the screen's
existing error display + retry.

### Step 5.6 — `mclink.mixins.json` (edit)

```json
{
  "required": true,
  "package": "com.tailscale.mclink.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "IntegratedServerAccessor",
    "ServerListAccessor",
    "ServerListMixin",
    "MultiplayerScreenMixin"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

### Step 5.7 — `en_us.json` (edit)

Add one key:

```json
    "mclink.server.label": "Connected via Tailcat tunnel"
```

### Step 5.8 — Verify

1. `./gradlew :mod:build` — fabric-loom's mixin annotation processor validates
   every `@Inject` target (method name, descriptor, cancellability) and every
   `@Accessor`/`@Shadow` field at compile time; a mismatch fails the build.
   All existing tests must still pass.
2. Manual client check (run once):

```bash
mkdir -p mod/run/config
printf '{"name":"Tailcat World","tailcat":"mcl1_invalid_for_ui_check"}' > mod/run/config/mclink.json
./gradlew :mod:runClient
```

   - Multiplayer list shows **Tailcat World** pinned at the very top, green
     ping icon, "Connected via Tailcat tunnel" as the MOTD line, no player
     count.
   - Select it: Join enabled, **Edit and Delete grayed out**.
   - Shift+ArrowUp/Down: entry does not move. F5: entry survives.
   - Click Join: "Starting secure tunnel…" appears, then the (invalid) invite
     fails with "Could not connect: …" on the screen with a retryable
     Connect button. Cancel returns to the multiplayer list with the entry
     still there.
   - Quit, then: `grep -c mclink:tailcat mod/run/servers.dat` → no match
     (0). With no `mclink.json`, the entry is absent and the mod is inert.

   For a real end-to-end tunnel join, point the config `tailcat` at a live
   `mcl1_` invite from task 2.5 or a running dedicated server (task 6).

### Step 5.9 — Commit

```
git add mod/src/main mod/src/test
git commit -m "mod: pinned tailcat server entry in multiplayer list"
```

---

## Task 6: Java — dedicated-server auto-hosting

**Files:** `mod/src/main/java/com/tailscale/mclink/McLinkServer.java` (new),
`mod/src/main/java/com/tailscale/mclink/ServerHost.java` (new),
`mod/src/main/resources/fabric.mod.json` (edit)

### Step 6.1 — `ServerHost.java` (new)

```java
// (license header per AGENTS.md)

package com.tailscale.mclink;

import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class ServerHost implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("mclink/server");
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);

    private HelperProcess process;

    public synchronized void start(DedicatedServer server) {
        if (process != null) {
            return;
        }
        Path stateDir = server.runDirectory().resolve("tailcat-for-minecraft");
        try {
            Files.createDirectories(stateDir);
            HelperProcess helper = HelperProcess.start(List.of(
                    "host", "--target", "127.0.0.1:" + server.getPort(),
                    "--state-file", stateDir.resolve("state.json").toString()),
                    event -> {
                    });
            process = helper;
            helper.ready(STARTUP_TIMEOUT).whenComplete((event, error) -> {
                if (error != null) {
                    close();
                    LOG.warn("Tailcat hosting failed: {}", error.getMessage());
                    return;
                }
                LOG.info("[mclink] Tailcat invitation: {}", event.invite());
            });
        } catch (Exception e) {
            LOG.warn("Could not start Tailcat host: {}", e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (process == null) {
            return;
        }
        process.close();
        process = null;
    }
}
```

Design notes:
- `DedicatedServer.getPort()` and `runDirectory()` are public interface /
  superclass methods — no mixin needed.
- The helper is spawned before the server loop accepts players; a Tailcat
  outage (DERP unreachable, helper crash, timeout) only logs a warning and the
  Minecraft server runs normally without tailcat — per the spec's error
  table.
- `close()` is idempotent and clears the handle, so a later restart of the
  server can start a fresh helper.

### Step 6.2 — `McLinkServer.java` (new)

```java
// (license header per AGENTS.md)

package com.tailscale.mclink;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.dedicated.DedicatedServer;

public final class McLinkServer implements DedicatedServerModInitializer {
    private final ServerHost host = new ServerHost();

    @Override
    public void onInitializeServer(DedicatedServer server) {
        host.start(server);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> host.close());
    }
}
```

`onInitializeServer` only fires for `DedicatedServer` (the parameter type is
the guard — integrated/LAN hosting keeps its existing Share-button flow
untouched). `ServerHost` is deliberately separate from the client-side
`ScreenState`.

### Step 6.3 — `fabric.mod.json` (edit)

```json
{
  "schemaVersion": 1,
  "id": "tailcat-for-minecraft",
  "version": "${version}",
  "name": "Tailcat for Minecraft",
  "description": "Share Minecraft worlds through Tailcat's userspace WireGuard transport.",
  "authors": ["Tailscale"],
  "license": "BSD-3-Clause",
  "environment": "*",
  "entrypoints": {
    "client": ["com.tailscale.mclink.McLinkClient"],
    "main": ["com.tailscale.mclink.McLinkServer"]
  },
  "mixins": ["mclink.mixins.json"],
  "depends": {
    "fabricloader": ">=0.16.10",
    "minecraft": "~1.21.1",
    "java": ">=21",
    "fabric-api": "*"
  }
}
```

(Only `environment` and the new `main` entrypoint change. Client mixins in
`mclink.mixins.json` are simply not applied on a server; no server-side
references exist to any client-only class — `ServerHost`/`McLinkServer` use
only environment-neutral helpers.)

### Step 6.4 — Verify

1. `./gradlew :mod:build` → compiles, all tests pass.
2. Manual server check (needs network; run once). Build a dev helper first
   (task 2.5 command) so the packaged-but-stale native binary isn't used:

```bash
cd helper && "$TSGO" build -tags=ts_omit_ssh -o /tmp/mclink-helper ./cmd/mclink-helper
MCLINK_HELPER=/tmp/mclink-helper ./gradlew :mod:runServer
```

   - Console shows `[mclink] Tailcat invitation: mcl1_...` within ~20 s.
   - `<server root>/tailcat-for-minecraft/state.json` exists, mode 600.
   - `stop` the server; restart (same command) → **identical** invitation.
   - Kill the network (or use an unresolvable DERP map URL via a proxy) →
     warning logged, server still boots and plays.

### Step 6.5 — Commit

```
git add mod/src/main
git commit -m "mod: auto-host tailcat on dedicated server start"
```

---

## Task 7: Native rebuild + integration

**Files:** `mod/src/main/resources/assets/mclink/native/**` (regenerated),
`checksums.json` (regenerated)

### Step 7.1 — Rebuild natives with the new helper

```bash
cd /home/jasper/Projects/jaspercraft
./gradlew clean
bash scripts/build-natives.sh
```

This rebuilds all five platforms with the pinned Tailscale Go (downloading it
if absent) and rewrites the resources + `checksums.json`. The five `mclink-helper`
binaries and `checksums.json` under `mod/src/main/resources/assets/mclink/native/`
change.

### Step 7.2 — Full test + build

```bash
./gradlew :mod:test
./gradlew :mod:build
unzip -l mod/build/libs/tailcat-for-minecraft-0.1.0.jar | grep native
```

Expect all tests green (incl. `HelperEventTest`, `PlatformTest`, `InvitesTest`,
`TailcatConfigTest`) and the jar to contain the five native binaries plus
`checksums.json`.

### Step 7.3 — Final E2E (run once, per spec)

1. Start the dedicated server (packaged helper now, no `MCLINK_HELPER`):
   `./gradlew :mod:runServer` → copy the console invitation.
2. On a second machine / instance: write `config/mclink.json` with
   `{"name":"Tailcat World","tailcat":"<the invitation>"}` (plus an optional
   64x64 `icon.png` next to it) and launch the client.
   - Entry pinned at top, icon + green status, one-click join works through
     the tunnel (singleplayer world or a real dedicated server on the host).
3. Restart the server → identical invitation; client config still works.
4. Client: Shift+Arrow can't move the entry, Delete/Edit disabled, F5 keeps
   it, `servers.dat` never contains `mclink:tailcat`.

### Step 7.4 — Commit

```
git add mod/src/main/resources/assets/mclink/native
git commit -m "release: rebuild native helpers with state file support"
```

---

## Rollback notes

- Each task is an independent commit; tasks 1–6 can be reverted individually.
- `--state-file` unset leaves helper behavior byte-identical to before
  (task 2 keeps the old code path for the flag-less invocation).
- Deleting `config/mclink.json` disables the client entry completely;
  deleting `tailcat-for-minecraft/state.json` + restart rotates the server
  invitation (documented non-goal: no in-game rotation command).
