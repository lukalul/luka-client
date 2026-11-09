# luka

luka is a Minecraft 1.8.9 client built for PvP and faction servers: a performance-focused renderer stack, a HUD and module menu, replays, cosmetics, waypoints and a lot of small quality-of-life features. It runs on Ornithe/Fabric and has its own launcher.

luka is an independent project. It is **not** affiliated with Mojang, Microsoft, Lunar Client or any server. A purchased copy of Minecraft is needed to play online.

## Licence

luka is free software under the GPL-3.0-or-later with the additional terms in `NOTICE`: anyone can read, change and share it, but copies have to stay open source, and modified versions have to use their own name and logo. The source is published with every release.

## Install

1. Nothing to install first: `Luka.exe` carries its own small Java runtime. The game itself needs Java 25; the launcher downloads it from Adoptium on the first run and keeps it in its own folder.
2. Unzip this folder somewhere you can write to (Desktop, Documents, a games folder; not Program Files) and double-click `Luka.exe`. Windows may show a SmartScreen warning because the exe is not code-signed yet: choose More info, then Run anyway.
3. On the first start the launcher downloads Minecraft 1.8.9, its libraries and assets from Mojang, the loader and the mods it depends on from their official pages, and installs luka's own mods. This happens once; later starts are instant.
4. Add an account (offline, or Microsoft sign-in once you have set up your own app registration, see below) and press Play.

## What is in the folder

| Item | What it is |
|---|---|
| `Luka.exe`, `runtime/` | The launcher and the Java runtime it runs on |
| `app/` | The launcher itself (`luka-launcher.jar`), `luka-mods/` (luka's own mods and the bundled SoundFix, LGPL-3.0), `dependencies.json` (the third-party mods it downloads, with hashes and licences), `default-config/` (settings the client was tuned with, copied only when you have none) and, after the first start, the `luka/` game folder |
| `LICENSE`, `THIRD_PARTY_NOTICES.md`, `licenses/` | Licences and credits |

## Updates, backups and sync

* **Updates:** set `updateUrl=` in `luka/launcher.properties` to the address of an `update.json` (the release builder writes one). The launcher checks it in the background, downloads changed mods with their SHA-512 checked, and stages a new launcher jar that is applied the next time you start it. It points at `https://client.luka.wtf/update.json` by default; clear `updateUrl=` to turn it off.
* **Backups:** the launcher backs up your settings (options, configs, keybinds; never accounts or tokens) after each game session and before every update, keeps the newest 10, and can restore any of them from Settings.
* **Sync:** choose a folder (for example inside OneDrive or Dropbox) in Settings and the launcher keeps your settings in step between computers; the newest file wins and nothing is ever deleted.

## Server rules

Servers can switch mods off (Hypixel and MineMen use Badlion's mod API, StrafePvP and others use Lunar's Apollo protocol). luka reads both: any luka module the server disables is turned off while you are on that server and comes back when you leave; your own switches are never changed. The module card says "DISABLED BY SERVER" and luka prints one chat line listing what the server restricts. Macros also follow the server's command lists.

## Chat macros

The Chat Macros card has 8 slots. Give each a text (a leading `/` sends a command; `{x} {y} {z} {name} {health}` are filled in) and bind a key to it in Controls. A shared cooldown stops a held key from spamming a server.

## Microsoft sign-in

Mojang only lets approved apps sign players in. To use Microsoft accounts, register your own Azure app, request access through Mojang's form, and put its application ID in `luka/launcher.properties` as `msClientId=...`. Until then, offline accounts work for single player and servers that allow them. luka does not ship an app ID and never uses another launcher's.

## Staff tools

The Staff Mode card (X-Ray, sticky nametags) only works while the server you are on has granted staff mods through the Apollo protocol. It does nothing on other servers.

## Luka Online

With luka online switched on (the "Luka Online" card) you get a friends list (add people by name, see what server they are on, join them with one click, send them direct messages), a small luka mark beside the nametag of other luka players in the colour of their rank, and the capes your account owns (pick one in Cosmetics, ACCOUNT). A key called "Ping location" (Options > Controls > luka, unbound by default) marks the place you are looking at for friends on the same server. Streamer mode hides your server from friends, silences friend alerts and hides the server address box.

## Team HUD and alerts

Two HUD boxes for faction play: **Team HUD** lists your teammates (scoreboard team, luka friends, names you type, or the server's team view) with health, armour left, distance and an arrow toward them; **Alerts** warns about low health, armour about to break and helpful potions running out.

## Cosmetics

luka does not include any cape or cosmetic files. To use your own, put them in `game/luka-cosmetics/` next to an `index.json`.

## Credits and other licences

Some features were originally inspired by other mods; where their code has since been fully rewritten, no part of that mod's licence applies any more. A few bundled or downloaded components remain under their own open-source licences — see `THIRD_PARTY_NOTICES.md` for the full, accurate list and the original licences, which are respected.
