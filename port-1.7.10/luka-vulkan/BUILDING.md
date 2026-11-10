# Building the luka Vulkan renderer

You need Windows x64, the Visual Studio 2022 C++ Build Tools, CMake, Ninja, the Vulkan SDK, Git, JDK 21 and JDK 25.

Keep the source in a short folder such as `C:\src\luka-vulkan`. The native build creates deep paths, and the compiler fails with error C1083 when the full path gets too long.

1. Build the native library. Set `VULKAN_SDK` and `JDK21` first if yours are not at the default locations in the script. If a MinGW toolchain is on your `PATH`, remove it for this step.

   ```
   build-native.bat
   ```

2. Build the mod with `JAVA_HOME` pointing at JDK 25. List both JDKs so Gradle can find them, and skip the two native tasks because step 1 already did that work.

   ```
   gradlew build -x configureNative -x buildNative "-Porg.gradle.java.installations.paths=<path to JDK 21>,<path to JDK 25>"
   ```

The finished jar is in `build/libs`.
