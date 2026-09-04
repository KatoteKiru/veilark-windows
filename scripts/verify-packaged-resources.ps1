[CmdletBinding()]
param(
  [string]$ImageRoot = ""
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ([string]::IsNullOrWhiteSpace($ImageRoot)) {
  $ImageRoot = Join-Path $projectRoot 'desktopApp\build\compose\binaries\main\app\Veilark'
}
$image = [IO.Path]::GetFullPath($ImageRoot)
$resources = Join-Path $image 'app\resources'
$source = Join-Path $projectRoot 'packaging\resources\windows'
$required = @(
  'sing-box.exe',
  'trusttunnel_client.exe',
  'setup_wizard.exe',
  'libcronet.dll',
  'wintun.dll',
  'geo\geoip-ru.srs',
  'geo\geosite-category-ru.srs'
)

foreach ($relative in $required) {
  $packaged = Join-Path $resources $relative
  $expected = Join-Path $source $relative
  if (-not (Test-Path -LiteralPath $packaged -PathType Leaf)) {
    throw "Packaged runtime resource is missing: $relative"
  }
  if (-not (Test-Path -LiteralPath $expected -PathType Leaf)) {
    throw "Source runtime resource is missing: $relative"
  }
  $actualHash = (Get-FileHash -LiteralPath $packaged -Algorithm SHA256).Hash
  $expectedHash = (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash
  if ($actualHash -ne $expectedHash) {
    throw "Packaged runtime resource differs from the verified source: $relative"
  }
}

$launcherConfig = Join-Path $image 'app\Veilark.cfg'
if (-not (Test-Path -LiteralPath $launcherConfig -PathType Leaf)) {
  throw 'Packaged launcher configuration is missing'
}
$launcher = Get-Content -LiteralPath $launcherConfig -Raw
$resourceOption = 'java-options=-Dcompose.application.resources.dir=$APPDIR\resources'
if (-not $launcher.Contains($resourceOption, [StringComparison]::Ordinal)) {
  throw 'Packaged launcher does not point at its immutable runtime resources'
}

$singBoxOutput = & (Join-Path $resources 'sing-box.exe') version
$singBoxExit = $LASTEXITCODE
$singBoxOutput | Select-Object -First 1
if ($singBoxExit -ne 0) { throw 'Packaged sing-box does not start' }
$trustOutput = & (Join-Path $resources 'trusttunnel_client.exe') --version
$trustExit = $LASTEXITCODE
$trustOutput
if ($trustExit -ne 0) { throw 'Packaged TrustTunnel client does not start' }

Write-Host 'Packaged runtime resources verified.'
