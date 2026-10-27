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

# The compiled release decision and native resource must move together. An
# unaccepted private lab DLL must never leak into an ordinary installer.
$fairwayMetadata = Join-Path $projectRoot 'shared\src\commonMain\kotlin\uk\senyasenyavski\veilark\model\FairwayRelease.kt'
$fairwaySource = Get-Content -LiteralPath $fairwayMetadata -Raw
$acceptedMatch = [regex]::Match($fairwaySource, 'const val accepted = (true|false)\b')
if (-not $acceptedMatch.Success) { throw 'Fairway release decision is missing or ambiguous' }
$coreRelative = 'veilark_windows_native.dll'
$corePackaged = Join-Path $resources $coreRelative
$coreSource = Join-Path $source $coreRelative
if ($acceptedMatch.Groups[1].Value -eq 'true') {
  $sizeMatch = [regex]::Match($fairwaySource, 'const val nativeSize = ([1-9][0-9]*)L\b')
  $hashMatch = [regex]::Match($fairwaySource, 'const val nativeSha256 = "([0-9a-f]{64})"')
  if (-not $sizeMatch.Success -or -not $hashMatch.Success) { throw 'Accepted Fairway has no immutable native pin' }
  foreach ($nativePath in @($coreSource, $corePackaged)) {
    if (-not (Test-Path -LiteralPath $nativePath -PathType Leaf)) { throw 'Accepted Fairway native DLL is missing' }
    if ((Get-Item -LiteralPath $nativePath).Length -ne [long]$sizeMatch.Groups[1].Value -or
        (Get-FileHash -LiteralPath $nativePath -Algorithm SHA256).Hash -ine $hashMatch.Groups[1].Value) {
      throw 'Fairway native DLL does not match immutable release metadata'
    }
  }
} elseif ((Test-Path -LiteralPath $corePackaged) -or (Test-Path -LiteralPath $coreSource)) {
  throw 'Unaccepted Fairway DLL must not be packaged'
}

$launcherConfig = Join-Path $image 'app\Veilark.cfg'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$policyEntryName = 'fairway/ru-ipv4-20261013.txt'
$policyCopies = 0
foreach ($jarFile in Get-ChildItem -LiteralPath (Join-Path $image 'app') -Filter '*.jar' -File) {
  $archive = [IO.Compression.ZipFile]::OpenRead($jarFile.FullName)
  try {
    $entry = $archive.GetEntry($policyEntryName)
    if ($null -ne $entry) {
      if ($entry.Length -ne 135358) { throw 'Packaged Fairway policy size differs from Android' }
      $policyCopies++
      $stream = $entry.Open()
      $digest = [Security.Cryptography.SHA256]::Create()
      try {
        $policyHash = [BitConverter]::ToString($digest.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
        if ($policyHash -ne 'fd0fdb390680c4a6a18a02542205b766e345b6caa34de717a2d9c776dd063cd5') {
          throw 'Packaged Fairway policy differs from Android'
        }
      } finally { $digest.Dispose(); $stream.Dispose() }
    }
  } finally { $archive.Dispose() }
}
if ($policyCopies -ne 1) { throw 'Packaged Fairway policy must exist in exactly one application JAR' }
if (-not (Test-Path -LiteralPath $launcherConfig -PathType Leaf)) {
  throw 'Packaged launcher configuration is missing'
}
$launcher = Get-Content -LiteralPath $launcherConfig -Raw
$resourceOption = 'java-options=-Dcompose.application.resources.dir=$APPDIR\resources'
if ($launcher.IndexOf($resourceOption, [StringComparison]::Ordinal) -lt 0) {
  throw 'Packaged launcher does not point at its immutable runtime resources'
}

$runtimeRelease = Join-Path $image 'runtime\release'
if (Test-Path -LiteralPath $runtimeRelease -PathType Leaf) {
  $modulesLine = Select-String -LiteralPath $runtimeRelease -Pattern '^MODULES=' | Select-Object -First 1
  if (-not $modulesLine -or $modulesLine.Line -notmatch '\bjdk\.unsupported\b') {
    throw 'Packaged Java runtime lacks jdk.unsupported (core config share lock)'
  }
  if ($modulesLine.Line -notmatch '\bjdk\.crypto\.ec\b') {
    throw 'Packaged Java runtime lacks jdk.crypto.ec (X25519 device identity)'
  }
}

$singBoxOutput = & (Join-Path $resources 'sing-box.exe') version
$singBoxExit = $LASTEXITCODE
$singBoxOutput | Select-Object -First 1
if ($singBoxExit -ne 0) { throw 'Packaged sing-box does not start' }
$trustOutput = & (Join-Path $resources 'trusttunnel_client.exe') --version
$trustExit = $LASTEXITCODE
$trustOutput
if ($trustExit -ne 0) { throw 'Packaged TrustTunnel client does not start' }
$wizardOutput = & (Join-Path $resources 'setup_wizard.exe') --help
$wizardExit = $LASTEXITCODE
$wizardOutput | Select-Object -First 1
if ($wizardExit -ne 0) { throw 'Packaged TrustTunnel setup wizard does not start' }

Write-Host 'Packaged runtime resources verified.'
