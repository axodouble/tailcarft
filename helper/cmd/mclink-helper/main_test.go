// Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
//
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package main

import "testing"

func TestValidateLoopbackTarget(t *testing.T) {
	valid := []string{
		"127.0.0.1:25565",
		"127.0.0.1:1",
		"127.0.0.1:65535",
		"[::1]:25565",
	}
	for _, target := range valid {
		if err := validateLoopbackTarget(target); err != nil {
			t.Errorf("validateLoopbackTarget(%q) = %v, want nil", target, err)
		}
	}

	invalid := []string{
		"127.0.0.1:-1",
		"127.0.0.1:0",
		"127.0.0.1:65536",
		"127.0.0.1:port",
		"127.0.0.1",
		"10.0.0.1:25565",
		"example.com:25565",
		"::1",
	}
	for _, target := range invalid {
		if err := validateLoopbackTarget(target); err == nil {
			t.Errorf("validateLoopbackTarget(%q) = nil, want error", target)
		}
	}
}
