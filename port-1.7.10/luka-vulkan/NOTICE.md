# luka Vulkan renderer

This is a fork of **Astatine** by Ren Karuizawa (https://github.com/yeahhren/astatine), a Vulkan renderer for
Minecraft 1.8.9, taken at commit `8893cafb2b6c569c3a0d0b53cca532be5e1c7700` (2026-10-17).

Astatine is licensed under the GNU Affero General Public License v3.0 (see `LICENSE.md`). This fork is released under
the same licence. All changes made by luka are listed in the git history of this repository and are AGPL-3.0 as well.

## Changes from upstream

- The plain Vulkan swapchain is the default presenter on Windows. Upstream presents through a DXGI swapchain, which in
  testing on an RTX 3070 cost a fixed ~0.8 ms per frame and capped the game at ~1,250 FPS. Set the environment variable
  `ASTATINE_PRESENTER=dxgi` to use the DXGI path again.
- Optional profiler (`ASTATINE_PROFILE=1`): logs render-thread time per step and GPU time per kind of work every
  2 seconds.
- Draws with alpha test off use a fragment shader without `discard` and with early depth testing (new pipeline-key bit
  `NO_DISCARD`; pipeline recipe cache version 2). Normal play ~1,640 -> ~1,840 FPS in testing.

- OpenGL compatibility layer (`src/main/java/yt/ren/astatine/compat`): LWJGL's GL11-GL33 classes are rewritten at load time so direct OpenGL calls from other mods work or do nothing instead of aborting the JVM. State, matrices, immediate mode, display lists and scissor are translated; custom GLSL shaders and raw framebuffers are inert.
- Scissor support in the native renderer; int texture names map to renderer textures; this mod's mixins run at priority 900 so other mods' optional hooks into replaced methods skip quietly.

- Modified further through 2026-11-09: chests, trapped chests, ender chests and signs are baked into the chunk mesh (`terrain/BakedBlockEntities.kt`); held and dropped item meshes keyed by model and tint (`render/geometry/ItemMeshes.kt`); a single transform op for model parts (`PART_TRANSFORM`); chunk build pacing read from the `luka.chunkPace` system property; render type values -1 and -2 honoured for hidden blocks.

## Relation to the luka client

The luka client's own mods are separate programs. They do not include or call any code from this renderer; they draw
through Minecraft's normal rendering classes, which this renderer translates like any other mod's drawing.

## Connected textures

The connected texture tile selection in `src/main/kotlin/yt/ren/astatine/terrain/Ctm.kt` and `src/main/kotlin/yt/ren/astatine/terrain/ConnectedTextures.kt` is adapted from Right Proper MCPatcher (https://github.com/vfx-dev/Right-Proper-MCPatcher), Copyright (C) 2025 FalsePattern, licensed under LGPL-3.0-only. It is combined here under the terms of the GNU AGPL as this project requires. The file format is documented in OptiFine's public doc/ctm.properties.
