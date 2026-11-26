# What luka connects to

luka has no analytics or tracking. It connects to these places, and only for these reasons:

| Where | Why |
|---|---|
| client.luka.wtf | The signed list of what to install, luka's own mods and the Vulkan renderer |
| api.luka.wtf | Luka Online: friends and the luka icon over other luka players (see below, and you can switch it off). Also, with no switch: a crash report when luka fails (see below); and logs, only when you press SEND LOGS (see below) |
| Mojang (minecraft.net, mojang.com) | Minecraft itself, its assets, and joining online-mode servers |
| Microsoft (microsoft.com, xboxlive.com) | Signing in, only if you choose a Microsoft account |
| Modrinth | Mods by other authors, and Argentum |
| Adoptium (its downloads are hosted on GitHub), or Amazon Corretto as a backup | Java |
| Minecraft servers | The ones you join, and the ones listed on the launcher's Servers page, which it pings to show player counts |

Apart from Luka Online and the crash reports described below, these are the downloads and connections luka needs to work, so there is no switch to turn them off. Your Microsoft sign-in stays on your PC and is sent only to Microsoft and Mojang.

luka checks client.luka.wtf for updates every time you press Play. Without an internet connection it still starts for up to a day, with the list from its last check.

## Nvidia graphics cards

On an Nvidia card, the launcher sets Nvidia's "Vulkan/OpenGL present method" to Prefer native in a driver profile named `luka`, for luka's own copy of Java only. No other program is affected. To undo it, untick **Optimize Nvidia settings** on the launcher's Settings page and press Play once, which deletes that profile.

## Luka Online (api.luka.wtf)

Luka Online gives you a friends list and puts the purple luka icon beside the nametag of other players who use luka. It runs on luka's own service at api.luka.wtf, and it is on by default.

**What it sends, while you play with it on:**
- Your Minecraft name and UUID, when you start the game.
- That you are online, about once a minute.
- The address of the multiplayer server you are on, so your friends can see it and join you. Untick **Show friends my server** (or turn on **Streamer mode**) and it is not sent.
- The UUIDs of the players around you, to find out which of them use luka. The service only answers which of them are luka players.
- Your friend requests and your friends list.
- The messages you write to your friends.

**Messages:** only friends can write to each other. Messages are kept on api.luka.wtf for 30 days and then deleted; removing a friend deletes your conversation with them at once. They are not end-to-end encrypted: they travel over HTTPS, but the service stores them as text, so luka's team could read them. Do not send passwords or anything private. A player can send at most 20 messages a minute.

**How sign-in works:** your game asks Mojang for two things: its player certificate (the key Mojang gives every account for signed chat in newer Minecraft versions) and its own profile, which Mojang signs and which holds your name. It signs a one-time code from luka with that certificate and sends luka the certificate, the signature and the signed profile. luka checks Mojang's signatures with Mojang's published keys. Your Microsoft or Minecraft access token goes to Mojang only, never to luka, and the certificate's private key never leaves your PC.

**Who sees what:** other players can see that you use luka (the icon) while you are online. Only your friends see that you are online and, if you allow it, which server you are on. luka's team can see who has signed in, who is online and the server they share with friends, to run the service.

**What it does not do:** no logs of your game chat, no recordings, no ads, and nothing is sold to anyone.

**Turning it off:** open the luka menu and switch off the **Luka Online** card. The client then stops sending friends and presence data to api.luka.wtf (it still sends the crash reports described below, which are not part of Luka Online). To have your account removed from the service, ask in the luka Discord.

## Crash reports (api.luka.wtf)

The launcher generates a random id once, when you first install luka, and keeps it in your install. It does not identify you and is not your Minecraft account. There is no switch for this; it is about luka's own stability, not about you.

**What it sends:**
- That random install id.
- When luka fails internally or the game crashes: which part failed, the error message, and the stack trace. Never anything you typed or any personal data.

**What it does not do:** known, harmless noise (a flaky network connection, an optional feature that is not installed) is filtered out before anything is sent.

## Sending logs (api.luka.wtf)

Only when you press **SEND LOGS** (on the launcher's Settings page, or at the bottom left of luka's title screen in the
game) and then SEND, luka sends luka's team a zip of what they need to look into a problem. You choose what goes in;
each of these can be unticked:
- The game log of your last three sessions, with every line of game chat taken out. The log still contains your
  Minecraft name and the servers you joined.
- Crash reports and Java crash files from the last week, the FPS Profiler's results, replay render logs and the replay
  editor's last render job, the launcher's log, and your video and replay settings.
- Your operating system, Java version, processor and graphics card names, memory, and the mods you use.
- A debug session, if you recorded one. Only after you choose **Record a debug session first** in the launcher's SEND
  LOGS window and then play: while that one game runs, the launcher writes down once a second how busy your processor
  and graphics card are (in total and for the game), free memory, and on Nvidia cards the clock speed, power, temperature
  and slowdown reasons; once, your graphics cards and driver versions, screen resolution and refresh rate, and power
  plan; and when another program uses the graphics card heavily, that program's file name. The game writes down once a
  second its frame rate, frame times, memory and a few of its settings. It stays on your PC until you send it.

Always sent with it: your luka and Minecraft versions, renderer, the install id described above, and the note you
write (if any).

No account details, passwords or tokens are sent. The zip goes through api.luka.wtf straight to a private channel of
luka's team on Discord, where it is read. A helper of the team also sends the parts that matter (your note, the
report details, error lines and the end of the logs, crash files, FPS Profiler and debug session results) to
Anthropic's Claude API, which writes a short summary for the team. api.luka.wtf
keeps only the report id, the install id, the size and the time, for 90 days. You get a report id to quote. At most
3 reports an hour from one install.

## Discord status (Discord app on your PC, api.luka.wtf)

While the luka launcher is open and the Discord app runs on your PC, your Discord profile shows that you use luka:
"Idle / In the launcher" with the time since you opened it, and while you play, luka's and Minecraft's version and
where you are (menus, singleplayer, or the server you joined), with buttons to luka's website and Discord. The game's
**Discord Status** module chooses what is shown (hide the server or the version, or no status while you play).
Streamer mode hides the server too.

To do this, the launcher talks to the Discord app on your PC, which tells it your Discord user. The launcher then
sends luka's team, at api.luka.wtf, your Discord user id, username and display name together with the Minecraft names
and UUIDs of the accounts saved in your launcher, and the install id described above. It sends this again only when
one of these changes. luka's team can look up which Discord user uses which Minecraft names. Nothing else about your
Discord account is read (no messages, servers or friends).

**Turning it off:** untick **DISCORD STATUS** on the launcher's Settings page. The status disappears and the launcher
stops sending your Discord name. To have what was already sent removed, ask luka's team on Discord.

## Copying a Lunar Client profile

Only when you click **Copy** in the luka menu's Profiles view, luka reads Lunar Client's settings files on your PC (the profile list and that profile's mod settings) and copies mod switches and HUD positions into a new luka profile. Nothing is sent anywhere, and Lunar Client's files are never changed.
