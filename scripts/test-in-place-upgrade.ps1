param(
  [Parameter(Mandatory = $true)][string]$CandidateInstaller
)

$ErrorActionPreference = 'Stop'
$previousUrl = 'https://github.com/KatoteKiru/veilark-windows/releases/download/v0.3.18/Veilark-0.3.18.msi'
$previousSha256 = '4CE9D422B879E2A3188E905DAB411C029A0FAF0BD8373EC4DB8C700003DC895D'
$candidate = (Resolve-Path -LiteralPath $CandidateInstaller).Path
$testDirectory = Join-Path $env:RUNNER_TEMP 'veilark-upgrade-test'
New-Item -ItemType Directory -Path $testDirectory -Force | Out-Null
$previous = Join-Path $testDirectory 'Veilark-0.3.18.msi'

Invoke-WebRequest -Uri $previousUrl -OutFile $previous
$downloadedHash = (Get-FileHash -LiteralPath $previous -Algorithm SHA256).Hash
if ($downloadedHash -ne $previousSha256) { throw 'Previous release MSI SHA-256 mismatch' }

function Get-InstalledVersion {
  $roots = @(
    'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
    'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
  )
  $matches = @(Get-ItemProperty -Path $roots -ErrorAction SilentlyContinue |
    Where-Object { $_.DisplayName -eq 'Veilark' })
  if ($matches.Count -ne 1) {
    throw "Expected one installed Veilark product, found $($matches.Count)"
  }
  return [string]$matches[0].DisplayVersion
}

$install = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', "`"$previous`"", '/qn', '/norestart') -Wait -PassThru
if ($install.ExitCode -notin @(0, 3010)) { throw "Previous MSI install failed: $($install.ExitCode)" }
if ((Get-InstalledVersion) -ne '0.3.18') { throw 'Previous version was not installed' }

$dataDirectory = Join-Path $env:LOCALAPPDATA 'Veilark'
New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$sentinel = Join-Path $dataDirectory 'language.txt'
[IO.File]::WriteAllText($sentinel, 'ru', [Text.UTF8Encoding]::new($false))
$before = (Get-FileHash -LiteralPath $sentinel -Algorithm SHA256).Hash

$upgrade = Start-Process -FilePath $candidate -ArgumentList @('/quiet', '/norestart') -Wait -PassThru
if ($upgrade.ExitCode -notin @(0, 3010)) { throw "OTA wrapper upgrade failed: $($upgrade.ExitCode)" }
if ((Get-InstalledVersion) -ne '0.3.19') { throw 'Candidate version was not installed' }
if (-not (Test-Path -LiteralPath 'C:\Program Files\Veilark\Veilark.exe')) {
  throw 'Upgraded application launcher is missing'
}
$resources = Join-Path $env:ProgramFiles 'Veilark\app\resources'
$coreCandidates = @(
  (Join-Path $resources 'sing-box.exe'),
  (Join-Path $resources 'windows\sing-box.exe')
)
$core = @($coreCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
if ($core.Count -ne 1) { throw "Expected one installed sing-box core, found $($core.Count)" }
& $core[0] version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Installed sing-box core failed to start' }
if (-not (Test-Path -LiteralPath $sentinel)) { throw 'Local application data was removed' }
if ((Get-FileHash -LiteralPath $sentinel -Algorithm SHA256).Hash -ne $before) {
  throw 'Local application data changed during upgrade'
}
Write-Host 'In-place upgrade 0.3.18 -> 0.3.19 passed; local data preserved.'
