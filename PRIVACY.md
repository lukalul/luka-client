# What luka connects to

luka has no analytics or tracking. It connects to these places, and only for these reasons:

| Where | Why |
|---|---|
| client.luka.wtf | The signed list of what to install, and luka's own mods |
| api.luka.wtf | Luka Online: friends and the luka icon over other luka players (see below, and you can switch it off) |
| Mojang (minecraft.net, mojang.com) | Minecraft itself, its assets, and joining online-mode servers |
| Microsoft (microsoft.com, xboxlive.com) | Signing in, only if you choose a Microsoft account |
| Modrinth and GitHub | Open-source mods, Argentum, and the Vulkan renderer |
| Adoptium, or Amazon Corretto as a backup | Java |
| Minecraft servers | The ones you join, and the ones listed on the launcher's Servers page, which it pings to show player counts |

Apart from Luka Online, these are the downloads and connections luka needs to work, so there is no switch to turn them off. Your Microsoft sign-in stays on your PC and is sent only to Microsoft and Mojang.

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

**How sign-in works:** your game proves it owns your account the same way it does when you join an online-mode server. It asks Mojang's session server to confirm, and luka's service checks with Mojang. Your Microsoft or Minecraft access token goes to Mojang only, never to luka.

**Who sees what:** other players can see that you use luka (the icon) while you are online. Only your friends see that you are online and, if you allow it, which server you are on. luka's team can see who has signed in, who is online and the server they share with friends, to run the service.

**What it does not do:** no chat logs, no recordings, no ads, and nothing is sold to anyone.

**Turning it off:** open the luka menu and switch off the **Luka Online** card. The client then stops talking to api.luka.wtf. To have your account removed from the service, ask in the luka Discord.
