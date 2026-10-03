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
	dm := &tailcfg.DERPMap{Regions: map[int]*tailcfg.DERPRegion{}}
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
