# Signs a release's update list on the maintainer's PC. The launcher refuses any list this key did not sign.
# Usage: sign-release.ps1 -Body update-body.json -Out update.json [-Key <key file>]
param([Parameter(Mandatory)][string]$Body, [Parameter(Mandatory)][string]$Out, [string]$Key = "$HOME\.luka-signing\update-signing.key")
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Key)) { throw "No signing key at $Key" }
$java = if ($env:JAVA_HOME) { "$env:JAVA_HOME\bin\java.exe" } else { 'java' }
& $java "$PSScriptRoot\SignManifest.java" sign $Key $Body $Out
if ($LASTEXITCODE -ne 0) { throw 'Signing failed' }
"Signed. Upload $Out to client.luka.wtf last, after every other file."
