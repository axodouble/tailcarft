// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Package state persists the Tailcat host identity (node key and DERP
// region) so a server keeps the same invitation across restarts.
package state

import (
	"encoding/json"
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
