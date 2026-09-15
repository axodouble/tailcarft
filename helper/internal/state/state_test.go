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
