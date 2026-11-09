<p align="center">
  <img src=".github/banner.png" alt="luka" width="100%">
</p>

<p align="center">
  <a href="https://github.com/lukalul/luka-client/releases"><img src="https://img.shields.io/github/downloads/lukalul/luka-client/total?style=for-the-badge&color=8942DA&label=downloads" alt="Downloads"></a>
  <a href="https://github.com/lukalul/luka-client/releases/latest"><img src="https://img.shields.io/github/v/release/lukalul/luka-client?style=for-the-badge&color=8942DA&label=version" alt="Latest version"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-8942DA?style=for-the-badge" alt="GPL-3.0"></a>
</p>

<p align="center"><b>A fast, open-source Minecraft 1.8.9 client for PvP and faction servers.</b></p>

## Download

**[Get the luka launcher](https://github.com/lukalul/luka-client/releases/latest/download/Luka-Setup.exe)** for Windows. Run it and press Play.

**Using Prism Launcher?** Click Add Instance, open the Import tab and paste a link:

| Renderer | Link |
|---|---|
| Normal | `https://github.com/lukalul/luka-client/releases/latest/download/luka-1.8.9-prism.zip` |
| Vulkan | `https://github.com/lukalul/luka-client/releases/latest/download/luka-1.8.9-vulkan-prism.zip` |

**Code signing:** luka has applied for free code signing provided by [SignPath.io](https://signpath.io), with a certificate by [SignPath Foundation](https://signpath.org). Until that is in place, Windows may warn you about an unknown publisher: click More info, then Run anyway. See the [code signing policy](CODE_SIGNING_POLICY.md).

## What's included

| Mod | What it does |
|---|---|
| **luka** | The mods menu, HUD editor, HUD modules, Chat mod and video settings |
| **Vulkan renderer** | luka's fork of [Astatine](https://github.com/yeahhren/astatine), a Vulkan renderer for 1.8.9 |
| **Normal renderer** | [Argentum](https://modrinth.com/mod/argentum), downloaded from its authors' Modrinth page |
| **SubFrame** | Replays with a free camera, and video renders with real motion blur |
| **Envoy** | Support for Apollo server features |
| **Motion Blur** | Frame-rate-independent motion blur |
| **BakePlus and HCF Optimizer** | Keep FPS up around chests, signs and big groups of entities |
| **FastPacks** | A fast resource pack browser |
| **Custom Sky and Custom Colors** | OptiFine-style pack skies, colours and HD fonts on Vulkan |
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

## Open source

All of luka's code is here, and every release is built from it by GitHub Actions. See [what luka connects to](PRIVACY.md) and [how to build it](BUILDING.md).

Licensed under [GPL-3.0](LICENSE), with the terms in [NOTICE](NOTICE). Not affiliated with Mojang or Microsoft.
