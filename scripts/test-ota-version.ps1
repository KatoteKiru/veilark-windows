$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ota-version.ps1')
foreach ($case in @(@('0.3.19',319), @('0.3.20',320), @('0.4.0',400), @('1.0.0',10000))) {
  if ((Get-VeilarkVersionCode $case[0]) -ne $case[1]) { throw "Wrong code for $($case[0])" }
}
if ((Get-VeilarkVersionCode '0.4.0') -le 319) { throw '0.4.0 must update 0.3.19' }
foreach ($invalid in @('0.4','00.4.0','0.100.0','0.4.100','0.0.0','999999.0.0')) {
  $rejected = $false
  try { Get-VeilarkVersionCode $invalid | Out-Null } catch { $rejected = $true }
  if (-not $rejected) { throw "Accepted invalid version: $invalid" }
}
'OTA version regression checks passed'
