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
