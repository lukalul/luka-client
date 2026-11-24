# Measures how much CPU an open, unfocused launcher burns. Starts each given launcher jar on a hidden desktop with a
# throwaway home (updates off, closed-source notice already seen), waits for it to settle, then reads the process's CPU
# time over a fixed window. Usage:
#   powershell -ExecutionPolicy Bypass -File luka-launcher\test\idlecpu.ps1 -Jars a.jar,b.jar [-Seconds 20]
param([string[]]$Jars, [int]$Seconds = 20, [int]$Settle = 10)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\..\testbot-run\lukatest.ps1')
$java = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe'
if ($Jars.Count -eq 1) { $Jars = $Jars[0] -split ',' }
foreach ($jar in $Jars) {
  $jar = (Resolve-Path $jar).Path
  $home2 = Join-Path $env:TEMP ('luka-idlecpu-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
  New-Item -ItemType Directory $home2 | Out-Null
  Set-Content (Join-Path $home2 'launcher.properties') 'updateUrl=' -Encoding ascii
  Set-Content (Join-Path $home2 'closed-source-notice.txt') 'shown' -Encoding ascii
  $dname = 'lukaidle_' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
  $desk = [LukaWin.Native]::CreateDesktop($dname, [IntPtr]::Zero, [IntPtr]::Zero, 0, 0x000F01FF, [IntPtr]::Zero)
  $cmd = '"' + $java + '" -Dsun.java2d.d3d=false "-Dluka.home=' + $home2 + '" -cp "' + $jar + '" dev.luka.launcher.Main'
  $p = 0
  $err = [LukaWin.Native]::LaunchTo($cmd, $home2, $dname, $null, [ref]$p)
  if ($err) { throw $err }
  Start-Sleep -Seconds $Settle
  $proc = Get-Process -Id $p
  $c0 = $proc.TotalProcessorTime.TotalMilliseconds
  Start-Sleep -Seconds $Seconds
  $proc.Refresh()
  $c1 = $proc.TotalProcessorTime.TotalMilliseconds
  $ws = [int]($proc.WorkingSet64 / 1MB)
  Stop-Process -Id $p -Force
  Start-Sleep -Milliseconds 500
  Remove-Item -Recurse -Force $home2 -ErrorAction SilentlyContinue
  '{0}: {1:N0} ms CPU in {2} s ({3:N1}% of one core), working set {4} MB' -f (Split-Path $jar -Leaf), ($c1 - $c0), $Seconds, (100 * ($c1 - $c0) / ($Seconds * 1000)), $ws
}
