# luka changelog

The launcher shows the notes below on its What's New page. Add a new `## version` heading at the top for each release.

## 1.6.1
- Replay editor: 1.7.10 recordings work. Before, every 1.7.10 recording showed "not a recording" and could not be added.
- Replay editor (Linux): if ffmpeg is missing, the editor tells you to install it with your package manager instead of asking for ffmpeg.exe.

## 1.6.0
- New replay editor: press REPLAY EDITOR in the launcher, next to PLAY. It works like a video editor: a big preview, and a timeline where your clips sit end to end. Drag a clip's edges to trim it, drag clips to change their order, and render them into one video.
- Press MAKE PREVIEWS to see pictures of your recording along the timeline. Then PLAY (or SPACE) plays the clip, and clicking the time ruler moves the playhead.
- Cut a recording into as many clips as you like: S (or SPLIT) cuts at the playhead or where your mouse is, and right-click on the timeline lets you split, start or end the clip there.
- Renders from the editor run in the background with the game window hidden (Windows), and the game closes by itself when the video is done. You can untick "Hide the game window" to watch.
- Editor: slow motion and speed per clip (0.25x to 4x, "Clip speed" above the preview).
- Editor: your kills show as red marks on the timeline (from the death messages in the recorded chat). K jumps to the next kill, SHIFT+K to the previous one.
- Editor: undo and redo (CTRL+Z, CTRL+Y).
- Editor: choose how the video looks, for that video only: field of view, render distance, brightness, particles, view bobbing, time of day, clear weather and a resource pack. You can hide the whole HUD, the hand, crosshair, chat, scoreboard or nametags, and pick which luka HUD parts show (for example no FPS counter). Your own game settings stay as they are.
- Replays (Normal renderer): fixed renders and the viewer showing only sky, hand and HUD (no ground, no players) for the whole video when the recording started with you standing still.
- Replays: a render that starts partway into a recording (a cut clip, or a selected range) no longer begins with a broken frame (black world, doubled HUD) or missing ground. It now waits a moment until the world, the chunks and the skins have loaded before the first frame is saved.
- Videos need ffmpeg, as before: if luka can't find it, the editor asks you to pick ffmpeg.exe once.

## 1.5.9
- Replays: when the recorded player opens a chest, the replay keeps playing. Before, the chest screen took over and you had to press E or Esc to continue.
- Replays: chests and other containers the player opened, and their own inventory (E), are shown over the replay and in rendered videos, with the items that were in them. Switch it off with "Show opened chests" in the replay menu. Closing a chest and opening your own inventory are only saved in recordings made with this version.

## 1.5.8
- Replays: you can type a frame rate up to 1,000,000 fps (was 20,000). Expect very long renders and very large files at those rates: the check before rendering shows how big and how long it will be.

## 1.5.7
- Replays: the enchantment shine on items moves at its normal speed in renders. Before, it ran at the render's own speed, so at high frame rates it raced and flickered.
- Replays: the render log is written again on setups whose game window library had no fullscreen check.

## 1.5.6
- Replays: if you use the Dynamic FPS mod, it is switched off while a replay is open (and back on afterwards), so it can no longer slow down or stutter a render.
- Vulkan renderer (1.8.9): fixed scrambled or frozen frames in replay renders after the game window was resized or switched to fullscreen.

## 1.5.5
- Replays: choose the video size in the render menu (720p, 1080p, 1440p, 4K, or type any size such as 2560x1080), independent of the game window.
- Replays: a render keeps its full speed when you tab out or minimise the game; "Limit FPS when unfocused" no longer slows it down.

## 1.5.4
- Replays: video renders use AMD and Intel graphics cards too (AMD AMF and Intel Quick Sync on Windows, VAAPI on Linux), not only NVIDIA. Without one, the CPU encoder is used as before.

## 1.5.3
- Replays on the Vulkan renderer: a render waits until the terrain in view is built, so a cut no longer starts with missing walls and ground (or, on 1.8.9, players standing in them).

## 1.5.2
- Replays: the FPS counter changes once a second, like in game, instead of on every frame.

## 1.5.1
- Replays: particles from your own hits (crits, Sharpness sparks), death effects and other effects show up in replays and renders again.
- Replays: you can type a frame rate up to 20,000 fps. Very high frame rates are encoded on the graphics card where it can, and a check before rendering shows how big and how long a large render will be.

## 1.5.0
- luka is open source again, under the GNU GPL v3: the code is at github.com/lukalul/luka-client, and the Vulkan renderer's at github.com/lukalul/luka-vulkan.
- luka's mods are plain files again, no longer protected: you no longer need a signed-in Microsoft account to play, and offline names work again.
- Without an internet connection, luka still starts for up to a day with the last update list.
- The launcher no longer sends a checksum of your mod files when you play. Crash reports stay.
- Replays: the replay menu has a box next to Video fps where you can type any frame rate from 1 to 1000.
- 1.7.10: Borderless fullscreen now works on the Normal renderer too.

## 1.4.1
- Video Settings is one screen again, laid out like OptiFine: the main page, then Quality, Details, Performance, Animations and Other. The separate renderer settings screen is gone; its options now live on these pages.
- Every particle switch is on the Animations page, with All ON and All OFF buttons. Each switch is simply on or off: the old All / Decreased / Minimal setting is gone.
- New players start with optimal settings: only crit, sharpness and potion particles, Fast graphics, no smooth lighting or clouds, render distance 8 and no frame cap. Your own settings are not changed; Other > Reset video settings applies the same set whenever you like.
- Envoy: chat icons show at full brightness again, coloured fire shows its real colour (also on your own screen), and unused staff nametag settings are gone.

## 1.4.0
- Zoom, Time Changer, Hitboxes, Crosshair, Nametags, Crash Patch, FOV Changer, Sound Tweaks, Screenshot Message, TNT Countdown, Damage Tint, Freelook, Overlay Tweaks, Render Tweaks, Dropped Item Tweaks, Confirm Disconnect, Scroll Tweaks, Shake Tweaks, Smooth Skies, Numerical Enchantments and custom colours are rebuilt from the ground up as luka's own code.
- Renamed: PolyZoom is now Zoom, PolyTime is now Time Changer, PolyHitbox is now Hitboxes and PolyCrosshair is now Crosshair. Your key bindings move with them.
- The settings of the rebuilt mods start fresh once with their new defaults. Whether each mod is on, where it sits on your HUD and its HUD style are kept.
- Freelook turns the camera again (it could leave the view stuck).
- Zoom: stepping back now goes all the way to no zoom, and calm view bob no longer jitters while walking zoomed in.
- Envoy: the /envoy commands (waypoints and the rest) work again.
- Prism Launcher packs are no longer offered: use the luka launcher.
- New Auto Center Inventory switch in Settings > General (on by default): your inventory stays in the middle of the screen while potion effects are active.
- Fixed a few features that quietly fell back to vanilla in the protected builds since 1.1.0, such as the static enchanting-table book.
- The launcher no longer costs you frame rate while it stays open: it stops redrawing itself while you play.
- Linux: the launcher now uses your desktop's cursor size and theme.

## 1.3.0
- Tidier mods menu: Chat, Raw Input, Scroll Tweaks, Sound Tweaks, Auto Reconnect, Confirm Disconnect, Screenshots, Discord Status, Luka Online, Crash Patch and Numerical Enchantments are now switches in Settings > General; FPS Profiler is in Settings > Performance and menu blur in Settings > Style.
- Toggle Sprint now does sprint and sneak in one module: Sprint can be Toggle, Always (nothing to press) or Off, plus Toggle sneak. Your old Always sprint and Toggle sneak settings carry over. Every status line ([Sprinting (Toggled)] and the rest) can be edited.
- New Boss bar HUD element: move, resize or hide the boss bar in the HUD editor.
- New Nick Hider (show a different name for yourself, hide skins) and Tab List (ping as a number with colours, highlight your own row, row tint by ping, no background or borders) mods.
- Style presets: save your Style settings as a named preset and switch between them. Dark and Light are included.
- Every HUD module has a "Hold to show" key: hold it to show a hidden module (or hide a shown one).
- FPS, CPS, Ping, Memory, Reach and Combo: your own format, hide below or above a value, and colour by value. Brackets can now be Off, Always or When background is off.
- New Performance switches: Fewer ambient effects, Particle collisions, Hide arrows in the ground, Hide arrows in flight, and frame rate caps for the main menu, pause menu, containers and the HUD editor. Entity shadows moved to Performance (and now works on 1.7.10).
- 1.7.10: the hit colour now matches vanilla 1.7.10, and HitFix no longer makes the hurt camera shake twice on some servers.
- Server titles show the right colours again.
- Removed: Always sprint and Toggle sneak (now in Toggle Sprint), Hide Blocks (use Hide foliage), and the server features Colored Fire, Custom Models, Limbs, Vignette, Hex Chat Colours, Lightning, Glow and Markers. Waypoints and server boss bar styles stay.

## 1.2.0
- New Saturation mod (on by default): a gold outline on the hunger bar shows your saturation, a light bar behind it shows exhaustion, and holding food flashes the hunger and saturation it would give. "Potential health restored" can be switched on in its options. The outline follows your resource pack's own food icon.
- 1.7.10: slim (Alex) skins and full 64x64 skins with jacket, sleeves and pants layers, for every player and your own first-person arm. Always on.
- New mods menu look: see-through glass cards, green when a mod is on and red when it is off, live previews of HUD mods, and Add to HUD, Enable and Options buttons on every card.
- The whole luka menu and HUD now use the Minecraft font, and the menu follows your GUI Scale setting, so it stays sharp at every size. Small windows get a compact layout.
- New Brackets option for text HUD mods (FPS, CPS, Ping, Clock and more), like [120 FPS]. Set it for all of them on the Style page.
- Vulkan renderer: removed present pacing. Every frame goes straight to the screen again.

## 1.1.0
- The launcher replaces older luka files left in your install with the current ones when you press Play. Your settings, worlds and other mods stay as they are.
- New: copy a Lunar Client profile into luka (luka menu > Profiles > Copy from Lunar Client). Mod switches and HUD positions come along; your current layout is kept as its own profile.
- 1.7.10: fixed the title screen showing the vanilla menu with the picture squeezed into a corner.
- Envoy's settings in the luka menu and the Replays button on the title screen work again.
- HCF Optimizer and FastPacks settings keep their names in the settings files again (the previous update could reset them once).
- 1.8.9 Vulkan renderer: works on Linux again.

## 1.0.0
- First public release of luka for Minecraft 1.8.9.
- Choose the Normal or Vulkan renderer in the launcher.
- Video settings for render distance, performance, quality and animations.
- Entities and dropped items behind walls are skipped, for more FPS in big fights.
