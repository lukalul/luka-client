# Builds luka-hidden.exe (hidden.cpp) into res\, from where build-launcher.ps1 puts it in the launcher jar. The launcher
# unpacks it next to itself to start the replay editor's renders on a hidden desktop (GameRunner.hiddenHelper).
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$launcher = Split-Path -Parent $here
$vcvars = 'C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat'
if (-not (Test-Path $vcvars)) {
  $vs = & "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -property installationPath
  $vcvars = "$vs\VC\Auxiliary\Build\vcvars64.bat"
}
$obj = "$here\out\hidden-obj"
New-Item -ItemType Directory -Force $obj | Out-Null
$cmd = @"
@echo off
call "$vcvars" >nul || exit /b 1
cd /d "$here"
cl /nologo /std:c++20 /EHsc /O2 /MT /W3 /D_UNICODE /DUNICODE /Fo"$obj\\" /Fe"$launcher\res\luka-hidden.exe" hidden.cpp /link /SUBSYSTEM:CONSOLE /OPT:REF /OPT:ICF user32.lib || exit /b 3
"@
Set-Content "$obj\build.cmd" $cmd -Encoding ascii
cmd /c "$obj\build.cmd"
if ($LASTEXITCODE -ne 0) { throw "luka-hidden build failed ($LASTEXITCODE)" }
'luka-hidden.exe: {0:n0} KB' -f ((Get-Item "$launcher\res\luka-hidden.exe").Length / 1KB)
