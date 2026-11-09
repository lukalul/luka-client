# luka

luka is an open-source Minecraft 1.8.9 client built for PvP and faction servers. It runs on Fabric (Ornithe), with a Normal (OpenGL) or Vulkan renderer.

luka is an independent project. It is not affiliated with Mojang, Microsoft or any server.

## Download

There are two ways to play. Pick one.

### The luka launcher

Download **[Luka-Setup.exe](https://github.com/LukaLUL/luka-client/releases/latest/download/Luka-Setup.exe)** and run it. It needs no administrator rights. Press Play and the launcher fetches the game, Java and the mods.

Prefer no installer? [Luka.exe](https://github.com/LukaLUL/luka-client/releases/latest/download/Luka.exe) runs on its own.

### Prism Launcher

In Prism, click **Add Instance**, open the **Import** tab, paste one of these links and click OK:

- Normal renderer: `https://github.com/LukaLUL/luka-client/releases/latest/download/luka-1.8.9-prism.zip`
- Vulkan renderer: `https://github.com/LukaLUL/luka-client/releases/latest/download/luka-1.8.9-vulkan-prism.zip`

The instance needs Java 25. Each time you press Launch it updates luka's mods by itself, so you never have to import it again.

## Is it safe?

Don't take our word for it. All of luka's code is in this repository, and every release is built from it by GitHub Actions, so you can follow a download back to the exact code. The launcher checks a signature on every update, and a file hash on every download. See [what luka connects to](PRIVACY.md).

## Building it yourself

See [BUILDING.md](BUILDING.md).

## Licence

luka is free software under the GPL-3.0-or-later, with the additional terms in [NOTICE](NOTICE). The Vulkan renderer is AGPL-3.0, custom sky is MIT and custom colours is LGPL-3.0. Other parts keep their own licences: see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
