# Builds the one-file Luka.exe: native stub + the launcher jars + a small Java just big enough to show the launcher.
# Output: luka-launcher\exe\out\Luka.exe. Nothing from the game or its mods goes in; the Play button downloads those.
param([string]$Version = '1.0.0')
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$launcher = Split-Path -Parent $here
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
if (-not (Test-Path $jdk)) { $jdk = $env:JAVA_HOME }
$vcvars = 'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path $vcvars)) {
  $vs = & "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -property installationPath
  $vcvars = "$vs\VC\Auxiliary\Build\vcvars64.bat"
}
$out = "$here\out"; $obj = "$out\obj"; $payload = "$out\payload"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $obj, $payload | Out-Null

& powershell -NoProfile -ExecutionPolicy Bypass -File "$launcher\build-launcher.ps1" -NoGameFiles | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'launcher build failed' }
Copy-Item "$launcher\dist\luka-launcher.jar", "$launcher\dist\luka-boot.jar" $payload

& "$jdk\bin\jlink.exe" --add-modules java.base,java.desktop,java.logging,java.management,jdk.management,jdk.crypto.ec,jdk.unsupported,java.naming --strip-debug --no-man-pages --no-header-files --compress=zip-0 --vm=server --output "$payload\runtime"
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }

# Windows libraries the launcher never touches (sound, splash screen, JPEG, attach and management natives)
foreach ($dll in 'jsound.dll', 'splashscreen.dll', 'javajpeg.dll', 'j2pkcs11.dll', 'j2pcsc.dll', 'j2gss.dll', 'w2k_lsa_auth.dll', 'sspi_bridge.dll', 'WinFallbackLookup.dll') {
  Remove-Item "$payload\runtime\bin\$dll" -Force -ErrorAction SilentlyContinue
}
Remove-Item "$payload\runtime\bin\server\classes*.jsa" -Force -ErrorAction SilentlyContinue
# changes whenever the launcher changes, so a new Luka.exe always replaces what an older one unpacked
$Version = $Version + '-' + (Get-FileHash "$payload\luka-launcher.jar" -Algorithm SHA1).Hash.Substring(0, 8)

$cmd = @"
@echo off
call "$vcvars" >nul || exit /b 1
cd /d "$here"
rc /nologo /fo "$obj\stub.res" stub.rc || exit /b 2
cl /nologo /std:c++20 /EHsc /O2 /MT /W3 /D_UNICODE /DUNICODE /Fo"$obj\\" /Fe"$out\stub.exe" stub.cpp "$obj\stub.res" /link /SUBSYSTEM:WINDOWS /OPT:REF /OPT:ICF || exit /b 3
"@
Set-Content "$obj\build.cmd" $cmd -Encoding ascii
cmd /c "$obj\build.cmd"
if ($LASTEXITCODE -ne 0) { throw "native build failed ($LASTEXITCODE)" }

$p = Start-Process -FilePath "$out\stub.exe" -ArgumentList '--pack', "`"$payload`"", $Version, "`"$out\Luka.exe`"" -Wait -PassThru -NoNewWindow
if ($p.ExitCode -ne 0) { throw "packing failed ($($p.ExitCode))" }
$size = (Get-Item "$out\Luka.exe").Length / 1MB
'Luka.exe: {0:n1} MB' -f $size
