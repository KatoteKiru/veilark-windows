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
$trustTunnelVersion = '1.0.49'
$singBoxSha256 = 'F580782C6DD10F7691C66CEA1D7C421813C5FBF7E305D1EE7CE0C3A40D196341'
$winTunSha256 = '07C256185D6EE3652E09FA55C0B673E2624B565E02C4B9091C79CA7D2F24EF51'
$trustTunnelSha256 = '0E11150E77C083C828593E6FAB7F76A159DD55B9AD9101DD70C4568DA3CDF11B'
$singBoxUrl = "https://github.com/SagerNet/sing-box/releases/download/v$singBoxVersion/sing-box-$singBoxVersion-windows-amd64.zip"
$winTunUrl = "https://www.wintun.net/builds/wintun-$winTunVersion.zip"
$trustTunnelUrl = "https://github.com/TrustTunnel/TrustTunnelClient/releases/download/v$trustTunnelVersion/trusttunnel_client-v$trustTunnelVersion-windows-x86_64.zip"

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

New-Item -ItemType Directory -Force -Path $downloadDirectory, $resolvedDestination | Out-Null

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

& (Join-Path $resolvedDestination 'sing-box.exe') version
& (Join-Path $resolvedDestination 'trusttunnel_client.exe') --version
Write-Host "Runtime prepared in $resolvedDestination"
