# Third-party notices for luka

luka is free software under the GNU General Public License v3.0 or later, with the additional terms in `NOTICE` (see `LICENSE`). The Vulkan renderer is AGPL-3.0, custom sky is MIT and custom colours is LGPL-3.0, each in its own source bundle.

## 1. Code and ideas luka's features are based on

These luka features are inspired by the mods below, under permissive licences (Unlicense/MIT) that impose no copyleft obligation on luka. The original copyright notices belong to the authors named here.

| Original mod | Author(s) | Licence | Source | luka feature |
|---|---|---|---|---|
| Old Animations | awruff | Unlicense | https://modrinth.com/mods/oam | 1.7 animations |
| BetterHurtCam | uku, thecount | MIT | https://modrinth.com/mod/betterhurtcam | Hurt camera |
| framejob | Z1xus | MIT | https://github.com/Z1xus/framejob | Between-tick smoothing in Motion Blur (licence file kept in the jar) |
| FastPacks / McPacks | its authors | MIT | https://modrinth.com/mod/fastpacks | Idea for the fast resource-pack browser |

## 1a. Features independently reimplemented (no longer licensed code)

luka used to ship ports of the copyleft-licensed mods below. Their code has since been replaced end to end with independently written implementations that reach the same player-facing behaviour, so none of this table's entries constrain luka's own licence any more. The rows are kept here only as a credit for the original idea/feature concept, which is not itself copyrightable.

| Feature | Original idea from | Licence of the original (no longer applicable) |
|---|---|---|
| PolyZoom (smooth zoom key) | Polyfrost's PolyZoom | GPL-3.0-or-later |
| CrashPatch (render-crash recovery screen) | Polyfrost's CrashPatch | GPL-3.0-or-later |
| Nametags (render-distance/own-tag options) | Polyfrost's PolyNametag | GPL-3.0 |
| PolyTime (forced world time / real-world time) | Polyfrost's PolyTime | GPL-3.0 |
| Screenshot Message Enhancer (clickable screenshot chat buttons) | Tomeko's Screenshot Message Enhancer | GPL-3.0 |
| TNTCountdown (fuse countdown over primed TNT) | chromatic's TNTCountdown | GPL-3.0 |
| SoundTweaks (per-category volume sliders) | Polyfrost's SoundTweaks | GPL-3.0 |
| Hitboxes | Polyfrost's PolyHitbox | LGPL-3.0-or-later |
| Crosshair | Polyfrost's PolyCrosshair | LGPL-3.0-or-later |
| Damage tint | Polyfrost's DamageTint | LGPL-3.0 |
| Freelook | chromatic's Freelook | LGPL-3.0 |
| FOV changer | chromatic's FOVChanger | LGPL-3.0 |
| Disconnect confirmation | Microcontrollers' Confirm Disconnect | LGPL-3.0 |
| Dropped items | Microcontrollers' Dropped Item Tweaks | LGPL-3.0 |
| Enchantment levels | Microcontrollers' Numerical Enchantments | LGPL-3.0 |
| Overlays | Microcontrollers' Overlay Tweaks | LGPL-3.0 |
| Render options | Microcontrollers' Render Tweaks | LGPL-3.0 |
| Scrolling | Microcontrollers' Scroll Tweaks | LGPL-3.0 |
| Shake options | Microcontrollers' Shake Tweaks | LGPL-3.0 |
| Sky smoothing | Microcontrollers' Smooth Skies | LGPL-3.0 |
| Envoy — Apollo protocol (waypoints, glow, nametags, team view, cooldowns, chat, markers, ...) | Natsu / CrystallicDev's "Artemis" (and the parts they adapted from Eterion) | Redistribution allowed if it stays free and links the original (no longer applicable — see note below) |

The 13 LGPL rows above were rewritten on 2026-11-08 in `cbui-ornithe` (clean-room: behaviour read, not code; new
internal structure; public surface, persisted setting names and mixin injection targets kept byte-identical).
See `cbui-ornithe/PROGRESS.md` for the file-by-file detail and the checks run on each one.

Credit: Envoy began as a port of Artemis by Natsu / CrystallicDev (https://modrinth.com/mod/lunar-artemis).

Envoy was renamed from "Artemis" on 2026-11-07 (identifiers only, at that point still the original port), then
clean-room rewritten end to end across 11 waves, finishing 2026-11-07. Every one of its ~12,610 lines —
protocol dispatch, every module, rendering, GUI, mixins, and the root files — is now independently written,
verified build-by-build against the real Gradle build and `javap` API-shape checks after each wave. The public
class names, `register(ApolloNetwork)` signatures, and the Apollo wire-protocol strings were kept stable
throughout so nothing else in luka broke, but none of the implementation is copied from the original any more.

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

Roboto Regular and Bold, copyright Google LLC, licensed under the Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0). The font files in `assets/cbui/font/` are unchanged.

## 5. Artwork

The logo, icons and menu panorama in luka are original artwork made for luka. The luka name and logo are reserved as described in `NOTICE`. No artwork from other clients is used. The cosmetics/cape library is **not** included: players put their own cape files in `luka-cosmetics/`.

## 6. Names and trademarks

Server names that appear as launcher shortcuts belong to their owners; luka only links to them and implies no endorsement. luka speaks the open Apollo protocol for compatibility; "Lunar Client" is a trademark of its owner and luka is not affiliated with it.

