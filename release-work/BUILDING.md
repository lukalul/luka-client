# Building luka

* **Mods:** install JDK 21, then run `gradlew build` inside each `*-ornithe` folder (1.8.9 at the top level, 1.7.10 under `port-1.7.10`). The jars land in `build/libs`. Gradle downloads everything it needs from public repositories, so nothing has to be added by hand.
* `cbui-ornithe` compiles against OneConfig's API and the Celeritas API. Gradle takes them from Polyfrost's Maven repository and Argentum's Modrinth page while building. They are used only to compile and never go into the mod.
* **Launcher:** with JDK 21 installed and `JAVA_HOME` pointing at it, run `luka-launcher/build-launcher.ps1 -NoGameFiles`. The jars land in `luka-launcher/dist`.
* **Luka.exe:** `luka-launcher/exe/build-exe.ps1` also needs the Visual Studio 2022 C++ Build Tools. It writes `luka-launcher/exe/out/Luka.exe`.
* **The Windows installer:** after `Luka.exe`, run `luka-launcher/exe/build-installer.ps1`. It needs [NSIS](https://nsis.sourceforge.io/).
* The Vulkan renderer is in its own repository, https://github.com/lukalul/luka-vulkan, with its own build guide.
