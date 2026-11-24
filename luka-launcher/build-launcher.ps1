# Builds the luka launcher into .\dist :
#   dist\luka-launcher.jar        the launcher
#   dist\Luka Launcher.bat        double-click to start it
#   dist\luka\libs, natives, game\mods ...  the game files taken from this PC's Prism "Ornithe-1.8.9-HCF" instance
# Run it again after changing the client mods to refresh the bundle.
param([switch]$NoGameFiles)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
if (-not (Test-Path $jdk)) { $jdk = $env:JAVA_HOME }
$prism = "$env:APPDATA\PrismLauncher"
$instance = "$prism\instances\Ornithe-1.8.9-HCF\minecraft"
$dist = "$here\dist"
$out = "$here\build"

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $out, $dist | Out-Null
$sources = Get-ChildItem "$here\src" -Recurse -Filter *.java | ForEach-Object FullName
& "$jdk\bin\javac.exe" --release 8 -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) { throw 'compile failed' }
Copy-Item "$here\res\*" $out -Force
"Main-Class: dev.luka.launcher.Main`r`n" | Set-Content "$out\manifest.txt" -Encoding ascii
& "$jdk\bin\jar.exe" cfm "$dist\luka-launcher.jar" "$out\manifest.txt" -C $out dev -C $out logo.png -C $out logo_inner.png -C $out header.png -C $out Roboto-Regular.ttf -C $out Roboto-Bold.ttf -C $out LICENSE-luka.txt -C $out NOTICE-luka.txt -C $out LICENSE-roboto-apache-2.0.txt -C $out classpath.txt -C $out jvmargs.txt -C $out classpath-1.7.10.txt -C $out jvmargs-1.7.10.txt
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
# Luka.exe's entry point: its own tiny jar (see Boot.java), so the launcher jar itself can be replaced by an update
$bootDir = "$here\build-boot"
Remove-Item $bootDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$bootDir\dev\luka\launcher" | Out-Null
Copy-Item "$out\dev\luka\launcher\Boot.class" "$bootDir\dev\luka\launcher\" -Force
"Main-Class: dev.luka.launcher.Boot`r`n" | Set-Content "$bootDir\manifest.txt" -Encoding ascii
& "$jdk\bin\jar.exe" cfm "$dist\luka-boot.jar" "$bootDir\manifest.txt" -C $bootDir dev
if ($LASTEXITCODE -ne 0) { throw 'boot jar failed' }
Remove-Item $bootDir -Recurse -Force
# Linux: the same two jars with luka.sh and a menu entry, as luka-launcher-linux.tar.gz (see linux\make_tar.py)
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) { & $python.Source -I "$here\linux\make_tar.py" $dist "$here\res\logo.png"; if ($LASTEXITCODE -ne 0) { throw 'linux package failed' } }
else { Write-Warning 'python not found: no Linux package' }
$batch = @'
@echo off
if exist "%~dp0luka-launcher.jar.new" move /Y "%~dp0luka-launcher.jar.new" "%~dp0luka-launcher.jar" >nul
start "" javaw -jar "%~dp0luka-launcher.jar"
'@
Set-Content "$dist\Luka Launcher.bat" -Encoding ascii -Value $batch
# Installer metadata and luka's own jars are distribution inputs, never folded into the launcher jar.
$depsSrc = if (Test-Path "$here\dependencies.json") { "$here\dependencies.json" } else { Join-Path (Split-Path $here -Parent) "release-work\dependencies.json" }
if (Test-Path $depsSrc) { Copy-Item $depsSrc "$dist\dependencies.json" -Force }
if (Test-Path "$here\luka-mods") { Copy-Item "$here\luka-mods" "$dist\luka-mods" -Recurse -Force }
if (Test-Path "$here\default-config") { Copy-Item "$here\default-config" "$dist\default-config" -Recurse -Force }

if ($NoGameFiles) { 'launcher only'; return }

$luka = "$dist\luka"
$libs = "$luka\libs"
New-Item -ItemType Directory -Force $libs, "$luka\natives", "$luka\game\mods", "$luka\game\config", "$luka\assets" | Out-Null

# the class path the HCF instance runs with (relative to the Prism libraries folder), minecraft client jar last
$classpath = @(
  'com/mojang/netty/1.8.8/netty-1.8.8.jar','oshi-project/oshi-core/1.1/oshi-core-1.1.jar','net/java/dev/jna/jna/3.4.0/jna-3.4.0.jar',
  'net/java/dev/jna/platform/3.4.0/platform-3.4.0.jar','com/ibm/icu/icu4j-core-mojang/51.2/icu4j-core-mojang-51.2.jar',
  'net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar','com/paulscode/codecjorbis/20101023/codecjorbis-20101023.jar',
  'com/paulscode/codecwav/20101023/codecwav-20101023.jar','com/paulscode/libraryjavasound/20101123/libraryjavasound-20101123.jar',
  'com/paulscode/librarylwjglopenal/20100824/librarylwjglopenal-20100824.jar','com/paulscode/soundsystem/20120107/soundsystem-20120107.jar',
  'io/netty/netty-all/4.0.23.Final/netty-all-4.0.23.Final.jar','com/google/guava/guava/17.0/guava-17.0.jar',
  'org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar','commons-io/commons-io/2.4/commons-io-2.4.jar',
  'commons-codec/commons-codec/1.9/commons-codec-1.9.jar','net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar',
  'net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar','com/google/code/gson/gson/2.10/gson-2.10.jar',
  'com/mojang/authlib/1.5.21/authlib-1.5.21.jar','org/apache/commons/commons-compress/1.8.1/commons-compress-1.8.1.jar',
  'org/apache/httpcomponents/httpclient/4.3.3/httpclient-4.3.3.jar','commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar',
  'org/apache/httpcomponents/httpcore/4.3.2/httpcore-4.3.2.jar','org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar',
  'org/apache/logging/log4j/log4j-core/2.19.0/log4j-core-2.19.0.jar',
  'org/lwjgl/lwjgl/lwjgl/2.9.4+legacyfabric.15/lwjgl-2.9.4+legacyfabric.15.jar',
  'org/lwjgl/lwjgl/lwjgl_util/2.9.4+legacyfabric.15/lwjgl_util-2.9.4+legacyfabric.15.jar','tv/twitch/twitch/6.5/twitch-6.5.jar',
  'net/ornithemc/calamus-intermediary-gen2/1.8.9/calamus-intermediary-gen2-1.8.9.jar','org/ow2/asm/asm/9.10.1/asm-9.10.1.jar',
  'org/ow2/asm/asm-analysis/9.10.1/asm-analysis-9.10.1.jar','org/ow2/asm/asm-commons/9.10.1/asm-commons-9.10.1.jar',
  'org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar','org/ow2/asm/asm-util/9.10.1/asm-util-9.10.1.jar',
  'net/fabricmc/sponge-mixin/0.17.5+mixin.0.8.7/sponge-mixin-0.17.5+mixin.0.8.7.jar','net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar',
  'org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar','org/apache/logging/log4j/log4j-slf4j2-impl/2.19.0/log4j-slf4j2-impl-2.19.0.jar',
  'it/unimi/dsi/fastutil/8.5.9/fastutil-8.5.9.jar','net/ornithemc/log4j-patch/1.0.0/log4j-patch-1.0.0.jar',
  'com/mojang/brigadier/1.3.11/brigadier-1.3.11.jar','com/mojang/minecraft/1.8.9/minecraft-1.8.9-client.jar'
)
foreach ($entry in $classpath) {
  $source = Join-Path "$prism\libraries" $entry
  if (-not (Test-Path $source)) { throw "Missing library: $source" }
  $target = Join-Path $libs $entry
  New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
  Copy-Item $source $target -Force
}
$utf8 = New-Object Text.UTF8Encoding $false
[IO.File]::WriteAllText("$luka\classpath.txt", (($classpath) -join "`n") + "`n", $utf8)

# natives (unpacked by the launcher at start)
Copy-Item "$prism\libraries\org\lwjgl\lwjgl\lwjgl-platform\2.9.4+legacyfabric.15\lwjgl-platform-2.9.4+legacyfabric.15-natives-windows.jar" "$luka\natives" -Force
Copy-Item "$prism\libraries\net\java\jinput\jinput-platform\2.0.5\jinput-platform-2.0.5-natives-windows.jar" "$luka\natives" -Force

# Java flags (memory and library path are added by the launcher)
$flags = @('-Duser.language=en','-XX:+UseG1GC','-XX:+ParallelRefProcEnabled','-XX:MaxGCPauseMillis=50','-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC','-XX:+AlwaysPreTouch','-XX:G1NewSizePercent=30','-XX:G1MaxNewSizePercent=40','-XX:G1HeapRegionSize=8M',
  '-XX:G1ReservePercent=20','-XX:G1HeapWastePercent=5','-XX:G1MixedGCCountTarget=4','-XX:InitiatingHeapOccupancyPercent=15',
  '-XX:G1MixedGCLiveThresholdPercent=90','-XX:G1RSetUpdatingPauseTimePercent=5','-XX:SurvivorRatio=32','-XX:MaxTenuringThreshold=1',
  '-XX:-UsePerfData','-Djava.net.preferIPv4Stack=true','-Dfabric.fixPackageAccess=true','-Dfabric.gameVersion=1.8.9')
[IO.File]::WriteAllText("$luka\jvmargs.txt", ($flags -join "`n") + "`n", $utf8)

# the client: the same mods and settings as the Prism instance
Copy-Item "$instance\mods\*.jar" "$luka\game\mods" -Force
# a mod removed from the Prism instance must not linger in the bundle (moved aside, not deleted)
$aside = "$luka\game\mods-removed-ported"
New-Item -ItemType Directory -Force $aside | Out-Null
Get-ChildItem "$luka\game\mods" -Filter *.jar | Where-Object { -not (Test-Path "$instance\mods\$($_.Name)") } | ForEach-Object { Move-Item $_.FullName "$aside\$($_.Name)" -Force }
Copy-Item "$instance\config\*" "$luka\game\config" -Recurse -Force -ErrorAction SilentlyContinue
if (Test-Path "$instance\options.txt") { Copy-Item "$instance\options.txt" "$luka\game\options.txt" -Force }

# resource packs: the same folder Prism uses (it is a junction to .minecraft\resourcepacks, several GB), shared with a junction instead of copied
$rpTarget = (Get-Item "$instance\resourcepacks" -ErrorAction SilentlyContinue).Target
if ($rpTarget) { $rpTarget = @($rpTarget)[0] } else { $rpTarget = "$instance\resourcepacks" }
$rp = "$luka\game\resourcepacks"
if (-not (Get-Item $rp -ErrorAction SilentlyContinue).LinkType) {
  if ((Test-Path $rp) -and @(Get-ChildItem $rp -Force).Count -eq 0) { Remove-Item $rp -Force }
  if (-not (Test-Path $rp) -and (Test-Path $rpTarget)) { New-Item -ItemType Junction -Path $rp -Target $rpTarget | Out-Null }
}

# assets: start from what Prism already downloaded (the launcher fetches anything missing)
if (Test-Path "$prism\assets\indexes\1.8.json") {
  New-Item -ItemType Directory -Force "$luka\assets\indexes" | Out-Null
  Copy-Item "$prism\assets\indexes\1.8.json" "$luka\assets\indexes" -Force
  Copy-Item "$prism\assets\objects" "$luka\assets" -Recurse -Force
}
"built: $dist"
