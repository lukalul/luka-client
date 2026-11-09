# Wraps exe\out\Luka.exe in the normal Windows installer, exe\out\Luka-Setup.exe. Run build-exe.ps1 first.
param([string]$Version = '1.0.0')
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe = "$here\out\Luka.exe"
if (-not (Test-Path $exe)) { throw 'Build Luka.exe first with build-exe.ps1' }
$tool = (Get-Command makensis.exe -ErrorAction SilentlyContinue).Source
foreach ($candidate in "$here\..\..\tools\nsis\makensis.exe", "${env:ProgramFiles(x86)}\NSIS\makensis.exe") {
  if (-not $tool -and (Test-Path $candidate)) { $tool = $candidate }
}
if (-not $tool) { throw 'NSIS is needed. Install it from https://nsis.sourceforge.io/' }
$out = "$here\out\Luka-Setup.exe"
& $tool /V2 "/DVERSION=$Version" "/DEXE=$exe" "/DOUT=$out" "$here\installer.nsi"
if ($LASTEXITCODE -ne 0) { throw 'The installer failed to build' }
"Luka-Setup.exe: {0:N1} MB" -f ((Get-Item $out).Length / 1MB)
