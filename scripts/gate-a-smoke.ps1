[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

& (Join-Path $PSScriptRoot 'bootstrap-runtime.ps1')
& (Join-Path $projectRoot 'gradlew.bat') clean test :shared:jvmTest
if ($LASTEXITCODE -ne 0) {
  throw "Gradle checks failed with exit code $LASTEXITCODE"
}

$config = Join-Path $projectRoot 'build\gate-a-check.json'
$configBody = @'
{
  "log": {"level": "warn"},
  "inbounds": [{
    "type": "tun",
    "tag": "tun-in",
    "address": ["172.19.0.1/30"],
    "auto_route": true,
    "strict_route": true,
    "stack": "mixed"
  }],
  "outbounds": [{"type": "direct", "tag": "direct"}],
  "route": {"auto_detect_interface": true, "final": "direct"}
}
'@
[System.IO.File]::WriteAllText($config, $configBody, [System.Text.UTF8Encoding]::new($false))
& (Join-Path $projectRoot 'packaging\resources\windows\sing-box.exe') check -c $config
if ($LASTEXITCODE -ne 0) {
  throw "sing-box rejected the Gate A TUN configuration"
}
Write-Host 'Gate A automated smoke passed. Live TUN connect still requires an elevated interactive run.'
