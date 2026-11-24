# Third-party notices for luka

luka is free software under the GNU General Public License v3.0 or later, with the additional terms in `NOTICE`
(its source code is at https://github.com/lukalul/luka-client). The third-party parts below keep their own licences.
Releases made between 2026-11-12 and 2026-11-24 were shipped as "all rights reserved"; the source published here covers
them too. The Vulkan renderer is the GNU AGPL v3 fork at https://github.com/lukalul/luka-vulkan. The shipped licence and
notice are `LICENSE-luka.txt` and `NOTICE-luka.txt` in every jar.

## 1. Third-party code in luka

| Part | Author | Licence | Where in luka | What ships |
|---|---|---|---|---|
| Artemis | Natsu91 / CrystallicDev | Artemis terms: free, modified or not, with a link to the original (https://github.com/CrystallicDev/Artemis) | Envoy (the Apollo/Lunar protocol mod) is based on it | credit and link in `NOTICE-luka.txt` and Envoy's `fabric.mod.json`; luka is free |
| Old Animations | awruff | Unlicense | cbui 1.7 animations are based on its code | credit (no notice required) |
| BetterHurtCam | uku | MIT | cbui hurt camera is based on its code | `META-INF/LICENSE.betterhurtcam` in the cbui jars |
| framejob | Z1xus | MIT | Motion Blur's between-tick smoothing | `META-INF/LICENSE.framejob` in the motionblur jars |
| legacy-custom-sky, MCPatcher Better Skies | oery; MCPatcher authors | MIT | customsky | `LICENSE-customsky.txt` in the customsky jars |
| Protocol Buffers for Java 3.25.5 | Google | BSD-3-Clause | bundled unchanged in the Envoy jars | `META-INF/LICENSE.protobuf` |
| Roboto | Google | Apache-2.0 | fonts in cbui and the launcher | licence text in both |
| OneConfig | Polyfrost | LGPL-3.0 + Additional Terms | not included; luka's menu uses it when the player has it installed | notice, GPL and LGPL texts in the cbui jars |
| Eclipse Temurin (OpenJDK) | Adoptium | GPLv2 + Classpath Exception | the Java runtime inside `Luka.exe` / the installer | its `legal/` folder; source at https://github.com/adoptium/jdk21u |
| NSIS | NSIS authors | zlib | builds the Windows installer | nothing required |

Ideas only (no code): FastPacks / McPacks (MIT) for the fast resource-pack browser.

## 1a. Rewritten clean-room (no longer licensed code)

These luka features began as ports of copyleft mods. A first "rewrite" (2026-11-07/08) only edited the ports and did not
remove the derivation; on 2026-11-17 they were written again from behaviour specs by people who never saw the old
code or the originals, and an independent audit compared the result with the old code and the originals:

- GPL originals: PolyZoom, CrashPatch, PolyNametag, PolyTime, SoundTweaks, PolyHitbox, PolyCrosshair (Polyfrost / Wyvest),
  FOVChanger (awruff), TNTCountdown (chromatic), Screenshot Message Enhancer (Tomeko).
- LGPL originals: DamageTint (Polyfrost), Freelook (chromatic), Confirm Disconnect, Dropped Item Tweaks, Numerical
  Enchantments, Overlay Tweaks, Render Tweaks, Scroll Tweaks, Shake Tweaks, Smooth Skies (Microcontrollers), the 1.7.10
  held-keys fix (Sarcio's idea).
- customcolors (MCPatcher via Angelica, LGPL), rewritten from the public OptiFine/MCPatcher format documentation; it
  is now luka's own code, under luka's licence.
## 2. Mods the launcher downloads from their official pages

The launcher does **not** contain these files. On first run it downloads them from the author's own Modrinth page and checks the file hash. Their licences are the authors' own. Argentum, Argentum Extras and Cera are "All Rights Reserved" by their author and are only downloaded from the official page, never redistributed by luka.

| Mod | Version | Licence | Page |
|---|---|---|---|
| Sarcio | 1.1.1 | GNU Lesser General Public License v3.0 or later | https://modrinth.com/mod/sarcio |
| Argentum | 1.0.2 | LicenseRef-All-Rights-Reserved | https://modrinth.com/mod/argentum |
| Argentum Extras | 1.0.2 | LicenseRef-All-Rights-Reserved | https://modrinth.com/mod/argentum-extras |
| Async Logger | 2.2.3+1.8.9-ornithe | GNU Lesser General Public License v3.0 only | https://modrinth.com/mod/asynclogger |
| Cera | 1.0.2 | LicenseRef-All-Rights-Reserved | https://modrinth.com/mod/cera |
| Legacy Dynamic FPS | 1.0.0 | MIT License | https://modrinth.com/mod/legacydynamicfps |
| Exploint | 1.1.0+mc1.8.9 | GNU Lesser General Public License v3.0 only | https://modrinth.com/mod/exploint |
| legacy-lwjgl3 | 1.2.11+1.8.9 | GNU Lesser General Public License v2.1 only | https://modrinth.com/mod/moehreag-legacy-lwjgl3 |
| Ornithe Standard Libraries | 0.23.1 | Apache License 2.0 | https://modrinth.com/mod/osl |
| Pylon (Raw Input) | 0.2.7 | GNU Lesser General Public License v3.0 only | https://modrinth.com/mod/pylon |

## 2a. Bundled under LGPL (not downloaded by the launcher)

Unlike the mods in the table above, this file ships directly in `luka-mods/` because it is not published on
Modrinth. LGPL-3.0 allows this because it ships as its own
unmodified, separately replaceable jar, not merged into luka's own code — anyone can drop in a different build
of it. Its own `fabric.mod.json` carries no source URL; the authors' page is linked below for reference.

| Mod | Version | Licence | Page |
|---|---|---|---|
| SoundFix | 1.2.0+mc1.8.9 | GNU Lesser General Public License v3.0 only | https://modrinth.com/mod/soundfix |

## 3. Minecraft and its libraries

The launcher downloads Minecraft 1.8.9, its libraries and its assets directly from Mojang's servers (`piston-meta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`) on the player's own computer. Fabric Loader, Mixin, ASM, Ornithe intermediary mappings, Legacy Fabric LWJGL and the other libraries come from their public Maven repositories. A purchased copy of Minecraft is required to play online.

## 4. Fonts

Roboto Regular and Bold, copyright Google LLC, licensed under the Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0). The font files in `assets/cbui/font/` are unchanged; the licence text ships in the cbui jar (`META-INF/LICENSE.roboto-apache-2.0`) and in the launcher jar (`LICENSE-roboto-apache-2.0.txt`).

## 5. Artwork

The logo, icons and menu panorama in luka are original artwork made for luka. The luka name and logo are reserved as described in `NOTICE`. No artwork from other clients is used. The cosmetics/cape library is **not** included: players put their own cape files in `luka-cosmetics/`.

## 6. Names and trademarks

Server names that appear as launcher shortcuts belong to their owners; luka only links to them and implies no endorsement. luka speaks the open Apollo protocol for compatibility; "Lunar Client" is a trademark of its owner and luka is not affiliated with it.

