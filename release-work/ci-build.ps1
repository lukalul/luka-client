# Builds everything for a release. GitHub Actions runs this on every version tag, and it can be run by hand.
# Needs JAVA_21 and JAVA_25 (JDK folders), the Vulkan SDK in VULKAN_SDK, Visual Studio 2022 C++ tools, CMake, Ninja and NSIS.
# Usage: ci-build.ps1 -Repo <github user>/<repo name> -Tag v1.0.0
param([Parameter(Mandatory)][string]$Repo, [Parameter(Mandatory)][string]$Tag)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Run-Gradle($folder, [string[]]$extra) {
  Push-Location "$root\$folder"
  & "$root\$folder\gradlew.bat" build --no-daemon --console=plain @extra
  if ($LASTEXITCODE -ne 0) { throw "$folder failed to build" }
  Pop-Location
}

$env:JAVA_HOME = $env:JAVA_21
foreach ($m in 'cbui', 'envoy', 'bakeplus', 'motionblur', 'subframe', 'fullbright', 'fastpacks', 'hcfopt', 'customsky', 'customcolors') { Run-Gradle "$m-ornithe" @() }

$env:JDK21 = $env:JAVA_21 -replace '\\', '/'
$keepPath = $env:Path
$env:Path = ($env:Path -split ';' | Where-Object { $_ -notmatch 'WinLibs|mingw' }) -join ';'
& cmd /c "$root\luka-vulkan\build-native.bat"
if ($LASTEXITCODE -ne 0) { throw 'The Vulkan native library failed to build' }
$env:Path = $keepPath
$env:JAVA_HOME = $env:JAVA_25
Run-Gradle 'luka-vulkan' @('-x', 'configureNative', '-x', 'buildNative', "-Porg.gradle.java.installations.paths=$($env:JAVA_21),$($env:JAVA_25)")

$env:JAVA_HOME = $env:JAVA_21
& powershell -NoProfile -ExecutionPolicy Bypass -File "$root\luka-launcher\exe\build-exe.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Luka.exe failed to build' }
& powershell -NoProfile -ExecutionPolicy Bypass -File "$root\luka-launcher\exe\build-installer.ps1"
if ($LASTEXITCODE -ne 0) { throw 'The installer failed to build' }

& python -I "$root\release-work\build_manifest.py" --github "https://github.com/$Repo/releases/download/$Tag" --unsigned
if ($LASTEXITCODE -ne 0) { throw 'The release files could not be written' }
Copy-Item "$root\luka-launcher\exe\out\Luka.exe", "$root\luka-launcher\exe\out\Luka-Setup.exe" "$root\release\online\" -Force
Compress-Archive -Path "$root\release\online\client.luka.wtf\*" -DestinationPath "$root\release\online\client.luka.wtf.zip" -Force
'Built: release\online'
