# Tailcarft

[![Issues](https://git.jas.pe/labs/tailcarft/badges/issues.svg)](https://git.jas.pe/labs/tailcarft/issues)
[![Releases](https://git.jas.pe/labs/tailcarft/badges/release.svg)](https://git.jas.pe/labs/tailcarft/releases/latest)
[![Workflows](https://git.jas.pe/labs/tailcarft/badges/workflows/release.yaml/badge.svg)](https://git.jas.pe/labs/tailcarft/actions?workflow=release.yaml&actor=0&status=0)
[![Commit](https://img.shields.io/gitea/last-commit/labs/tailcarft?gitea_url=https%3A%2F%2Fgit.jas.pe)](https://git.jas.pe/labs/tailcarft/)

A Minecraft client mod that shares a single-player world. It uses
[Tailcat](https://github.com/tailscale/tailcat) for its connecting
technology — userspace WireGuard with no TUN interface and no Tailscale
installation. The mod ships a bundled Go helper that owns all networking.

It ships one jar per Minecraft version and loader combination: Fabric
(1.20.1, 1.21.1, 26.3), NeoForge (1.21.1, 26.3), and Forge (1.20.1) six 
jars in all. Install the one that matches your Minecraft version and loader.

## Use

- **Host:** pause a single-player game, click **Share with Tailcarft**, and
  copy the `mcl1_...` invitation.
- **Join:** open Multiplayer and click **Connect with Tailcarft**, or paste the
  invitation into any server address field.

**Server workflow.** Sharing publishes your single-player world the same way
*Open to LAN* does, then the bundled helper binds to that local port and
exposes it over [Tailcat](https://github.com/tailscale/tailcat). The invitation is an ephemeral token: anyone holding
it can connect for as long as sharing is on, and only the game port (virtual
TCP 25565) is exposed. Click **Stop** — or leave the world — to end sharing.

## Config

Place `mclink.json` in your `config/` directory:

```json
{
  "tailcat": "mcl1_...",
  "name": "My World",
  "icon": "icon.png"
}
```

| Key | Required | Meaning |
| --- | --- | --- |
| `tailcat` | yes | Invitation — either an `mcl1_...` envelope or a bare `tc...` token |
| `name` | no | Display name of the pinned multiplayer entry (default: `Tailcarft Server`) |
| `icon` | no | A 64x64 PNG in `config/` shown next to the entry; any other size is ignored |

The file stores an invitation so you don't have to paste it every time. With a
valid config, a pinned entry that can't be edited or deleted appears at the top
of the multiplayer list and connects with one click. A missing or invalid
config shows nothing.

## Build

Requires JDK 17, 21, and 25 (the toolchain for each Minecraft version), plus
`curl` and `tar`:

```sh
scripts/build-natives.sh
./gradlew build
```

The twelve jars land in `<loader>-<mc>/build/libs/`, named
`tailcarft-<version>-<loader>-<mc>.jar`. For Java-only development, point
`MCLINK_HELPER` at a locally built helper binary instead.

## Releasing

Tag a commit `vX.Y.Z` matching `mod_version` in `gradle.properties` (for
example `v0.1.0-beta0`). The workflow in `.forgejo/workflows/release.yaml`
installs the three JDK toolchains, runs the tests, rebuilds the helper
natives, builds all twelve leaves, and publishes the twelve jars plus
`SHA256SUMS` as release attachments. A dashed tag (anything after a `-`) is
published as a pre-release.

---

Tailcarft was built on top of
[tailcat-for-minecraft](https://github.com/tailscale/tailcat-for-minecraft)
on GitHub and uses [Tailcat](https://github.com/tailscale/tailcat) for its connecting technology. Its BSD 3-Clause
license still applies in full — see `LICENSE`.
