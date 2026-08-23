[CmdletBinding()]
param(
  [string]$Destination = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

if ([string]::IsNullOrWhiteSpace($Destination)) {
  $Destination = Join-Path $PSScriptRoot '..\packaging\resources\windows'
}

$singBoxVersion = '1.13.14'
$winTunVersion = '0.14.1'
$trustTunnelVersion = '1.1.5-rc.6'
$singBoxSha256 = 'F580782C6DD10F7691C66CEA1D7C421813C5FBF7E305D1EE7CE0C3A40D196341'
$winTunSha256 = '07C256185D6EE3652E09FA55C0B673E2624B565E02C4B9091C79CA7D2F24EF51'
$trustTunnelSha256 = 'ECF95542B675C89A64B52CF6A3A795BD5F2E1B0E05D71CC021DF53A3738A300C'
$geoIpRuSha256 = '1A8115AF741918FF24B37B87D3C6DA21ECCABC58F1EEC059E461DCA8BAC16FF7'
$geoSiteRuSha256 = 'C36E157ADF86EDF7B722B51F3ACB93BBB2A7F8083932DAE29B4B5EF2C1CED870'
$singBoxUrl = "https://github.com/SagerNet/sing-box/releases/download/v$singBoxVersion/sing-box-$singBoxVersion-windows-amd64.zip"
$winTunUrl = "https://www.wintun.net/builds/wintun-$winTunVersion.zip"
$trustTunnelUrl = "https://github.com/TrustTunnel/TrustTunnelClient/releases/download/v$trustTunnelVersion/trusttunnel_client-v$trustTunnelVersion-windows-x86_64.zip"
$geoIpRuUrl = 'https://raw.githubusercontent.com/SagerNet/sing-geoip/b9c5e675b4d5359d4b47f4434fa7ae77e9991306/geoip-ru.srs'
$geoSiteRuUrl = 'https://raw.githubusercontent.com/SagerNet/sing-geosite/a70ce9f1f078f129cd40500f0bc0aee6eb6d59cd/geosite-category-ru.srs'

$resolvedDestination = [System.IO.Path]::GetFullPath($Destination)
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $resolvedDestination.StartsWith($projectRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "Destination must stay inside $projectRoot"
}

$downloadDirectory = Join-Path $projectRoot 'build\downloads'
$extractDirectory = Join-Path $projectRoot 'build\runtime-extract'
$singBoxArchive = Join-Path $downloadDirectory "sing-box-$singBoxVersion-windows-amd64.zip"
$winTunArchive = Join-Path $downloadDirectory "wintun-$winTunVersion.zip"
$trustTunnelArchive = Join-Path $downloadDirectory "trusttunnel_client-v$trustTunnelVersion-windows-x86_64.zip"
$geoDirectory = Join-Path $resolvedDestination 'geo'
$geoIpRuPath = Join-Path $geoDirectory 'geoip-ru.srs'
$geoSiteRuPath = Join-Path $geoDirectory 'geosite-category-ru.srs'

New-Item -ItemType Directory -Force -Path $downloadDirectory, $resolvedDestination, $geoDirectory | Out-Null

function Receive-VerifiedArchive {
  param(
    [string]$Url,
    [string]$Path,
    [string]$ExpectedSha256
  )
  if (-not (Test-Path -LiteralPath $Path)) {
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Path
  }
  $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash
  if ($actual -ne $ExpectedSha256) {
    throw "SHA-256 mismatch for $Path. Expected $ExpectedSha256, got $actual"
  }
}

Receive-VerifiedArchive $singBoxUrl $singBoxArchive $singBoxSha256
Receive-VerifiedArchive $winTunUrl $winTunArchive $winTunSha256
Receive-VerifiedArchive $trustTunnelUrl $trustTunnelArchive $trustTunnelSha256
Receive-VerifiedArchive $geoIpRuUrl $geoIpRuPath $geoIpRuSha256
Receive-VerifiedArchive $geoSiteRuUrl $geoSiteRuPath $geoSiteRuSha256

if (Test-Path -LiteralPath $extractDirectory) {
  Remove-Item -Recurse -Force -LiteralPath $extractDirectory
}
New-Item -ItemType Directory -Path $extractDirectory | Out-Null
Expand-Archive -LiteralPath $singBoxArchive -DestinationPath (Join-Path $extractDirectory 'sing-box')
Expand-Archive -LiteralPath $winTunArchive -DestinationPath (Join-Path $extractDirectory 'wintun')
Expand-Archive -LiteralPath $trustTunnelArchive -DestinationPath (Join-Path $extractDirectory 'trusttunnel')

$singBoxRoot = Join-Path $extractDirectory "sing-box\sing-box-$singBoxVersion-windows-amd64"
Copy-Item -Force -LiteralPath (Join-Path $singBoxRoot 'sing-box.exe') -Destination $resolvedDestination
Copy-Item -Force -LiteralPath (Join-Path $singBoxRoot 'libcronet.dll') -Destination $resolvedDestination
Copy-Item -Force -LiteralPath (Join-Path $singBoxRoot 'LICENSE') -Destination (Join-Path $resolvedDestination 'LICENSE-sing-box.txt')
Copy-Item -Force -LiteralPath (Join-Path $extractDirectory 'wintun\wintun\bin\amd64\wintun.dll') -Destination $resolvedDestination
Copy-Item -Force -LiteralPath (Join-Path $extractDirectory 'wintun\wintun\LICENSE.txt') -Destination (Join-Path $resolvedDestination 'LICENSE-WinTUN.txt')
Copy-Item -Force -LiteralPath (Join-Path $extractDirectory 'trusttunnel\trusttunnel_client.exe') -Destination $resolvedDestination
Copy-Item -Force -LiteralPath (Join-Path $extractDirectory 'trusttunnel\setup_wizard.exe') -Destination $resolvedDestination
Copy-Item -Force -LiteralPath (Join-Path $extractDirectory 'trusttunnel\LICENSE.txt') -Destination (Join-Path $resolvedDestination 'LICENSE-TrustTunnel.txt')

$geoAssets = @(
  @{ Path = $geoIpRuPath; Sha256 = $geoIpRuSha256 },
  @{ Path = $geoSiteRuPath; Sha256 = $geoSiteRuSha256 }
)
foreach ($asset in $geoAssets) {
  if (-not (Test-Path -LiteralPath $asset.Path -PathType Leaf)) {
    throw "Missing bundled routing asset: $($asset.Path)"
  }
  $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $asset.Path).Hash
  if ($actual -ne $asset.Sha256) {
    throw "SHA-256 mismatch for $($asset.Path). Expected $($asset.Sha256), got $actual"
  }
}

& (Join-Path $resolvedDestination 'sing-box.exe') version
& (Join-Path $resolvedDestination 'trusttunnel_client.exe') --version
Write-Host "Runtime prepared in $resolvedDestination"
