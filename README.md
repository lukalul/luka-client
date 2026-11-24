<p align="center">
  <img src=".github/banner.png" alt="luka" width="100%">
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-8942DA?style=for-the-badge" alt="GPL-3.0"></a>
  <a href="https://client.luka.wtf"><img src="https://img.shields.io/badge/download-client.luka.wtf-8942DA?style=for-the-badge" alt="Download"></a>
</p>

<p align="center"><b>A fast, open-source Minecraft 1.8.9 and 1.7.10 client for PvP and faction servers, on Windows and Linux.</b></p>

## Download

**[Get the luka launcher](https://client.luka.wtf/Luka-Setup.exe)** for Windows. Run it and press Play.

**On Linux**, download [luka-launcher-linux.tar.gz](https://client.luka.wtf/luka-launcher-linux.tar.gz), unpack it and run `luka.sh`. It uses Java 21 or newer if you have it, and otherwise downloads Java once. The Vulkan renderer needs a Vulkan driver.

**Pick your version** in the launcher: 1.8.9 or 1.7.10, next to the Normal and Vulkan renderer switch.

**Code signing:** luka isn't code-signed yet, so Windows may warn you about an unknown publisher: click More info, then Run anyway. See the [code signing policy](CODE_SIGNING_POLICY.md).

## What's included

| Mod | What it does |
|---|---|
| **luka** | The mods menu, HUD editor, HUD modules, Chat mod and video settings |
| **Vulkan renderer** | luka's fork of [Astatine](https://github.com/yeahhren/astatine), a Vulkan renderer, for 1.8.9 and 1.7.10. Its source is in [lukalul/luka-vulkan](https://github.com/lukalul/luka-vulkan) (GNU AGPL v3) |
| **Normal renderer** | On 1.8.9 [Argentum](https://modrinth.com/mod/argentum), downloaded from its authors' Modrinth page; on 1.7.10 the game's own OpenGL |
| **SubFrame** | Replays with a free camera, and video renders with real motion blur |
| **Envoy** | Support for Apollo server features |
| **Motion Blur** | Frame-rate-independent motion blur |
| **BakePlus and HCF Optimizer** | Keep FPS up around chests, signs and big groups of entities |
| **FastPacks** | A fast resource pack browser |
| **Custom Sky and Custom Colors** | OptiFine-style pack skies, colours and HD fonts on Vulkan |
| **Connected textures** | OptiFine-style connected glass and blocks from your packs (1.7.10, Vulkan) |
| **Fullbright** | Full light with normal colours |

## Performance

Average FPS at 1080p on one gaming PC, with luka's full mod set. Yours will differ.

| Normal vs Vulkan | Normal | Vulkan | Vulkan gain |
|---|---:|---:|---:|
| Empty scene | 2,455 | 3,348 | +36% |
| 30 players on screen | 1,341 | 2,274 | +70% |

luka also skips drawing what's hidden behind walls:

| Scene | Normal | Vulkan |
|---|---:|---:|
| 30 players behind a wall | 1,970 → 2,530 | 2,970 → 3,840 |
| 300 enchanted items behind a wall | 2,460 → 3,060 | 2,630 → 5,590 |

The wall tests ran in an automated test window, so compare them only with each other.

On 1.7.10, in the same automated test window (1080p, render distance 12):

| Minecraft 1.7.10 | Average FPS |
|---|---:|
| The game on its own | 720 to 910 |
| luka with the Vulkan renderer | 2,090 to 2,120 |

## Open source

luka's mods and launcher, for 1.8.9 and 1.7.10, are here; the Vulkan renderer has [its own repository](https://github.com/lukalul/luka-vulkan). Releases are served from [client.luka.wtf](https://client.luka.wtf). See [what luka connects to](PRIVACY.md) and [how to build it](BUILDING.md).

Licensed under [GPL-3.0](LICENSE), with the terms in [NOTICE](NOTICE). Not affiliated with Mojang or Microsoft.
