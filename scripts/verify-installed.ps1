param(
  [string]$InstallDir = "$env:ProgramFiles\Veilark",
  [string]$ExpectedVersion = ""
)

$ErrorActionPreference = "Stop"
$resolvedInstallDir = [System.IO.Path]::GetFullPath($InstallDir)
$expectedRoot = [System.IO.Path]::GetFullPath("$env:ProgramFiles\Veilark")
if (-not $resolvedInstallDir.StartsWith($expectedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "Refusing to inspect a directory outside the Veilark installation root."
}

$executable = Join-Path $resolvedInstallDir "Veilark.exe"
$appDirectory = Join-Path $resolvedInstallDir "app"
$config = Join-Path $appDirectory "Veilark.cfg"

if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
  throw "Veilark.exe is missing from $resolvedInstallDir"
}
if (-not (Test-Path -LiteralPath $config -PathType Leaf)) {
  throw "Veilark.cfg is missing from $appDirectory"
}

$jna = Get-ChildItem -LiteralPath $appDirectory -Filter "jna-*.jar" |
  Where-Object { $_.Name -notlike "jna-platform-*" } |
  Select-Object -First 1
$jnaPlatform = Get-ChildItem -LiteralPath $appDirectory -Filter "jna-platform-*.jar" |
  Select-Object -First 1

if ($null -eq $jna -or $jna.Length -lt 1900000) {
  throw "JNA runtime is missing or truncated. Installed release would fail during startup."
}
if ($null -eq $jnaPlatform -or $jnaPlatform.Length -lt 1000000) {
  throw "JNA platform runtime is missing or truncated. Installed release would fail during startup."
}

$configText = Get-Content -LiteralPath $config -Raw
if ($ExpectedVersion -and $configText -notmatch [regex]::Escape("-Djpackage.app-version=$ExpectedVersion")) {
  throw "Installed version does not match $ExpectedVersion"
}

Write-Host "Veilark installation verified."
Write-Host "Version: $ExpectedVersion"
Write-Host "JNA: $($jna.Length) bytes"
Write-Host "JNA platform: $($jnaPlatform.Length) bytes"
