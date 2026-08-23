$ErrorActionPreference = "Stop"

if (Get-NetTCPConnection -LocalPort 20808 -State Listen -ErrorAction SilentlyContinue) {
  throw "Diagnostic port 20808 is already in use"
}

$configPath = Join-Path (Resolve-Path ".").Path "build\singbox-live-proxy.json"
$stdoutPath = Join-Path (Resolve-Path ".").Path "build\singbox-live-proxy.stdout.log"
$stderrPath = Join-Path (Resolve-Path ".").Path "build\singbox-live-proxy.stderr.log"
$values = @{}
Get-Content "C:\AI-Agent\secrets\servers.env" | ForEach-Object {
  if ($_ -match "^([^#=]+)=(.*)$") {
    $values[$matches[1].Trim()] = $matches[2].Trim().Trim('"').Trim("'")
  }
}
$env:VEILARK_TEST_SUBSCRIPTION = $values["SUBSCRIPTION_URL"]
$env:VEILARK_PROXY_CONFIG_OUTPUT = $configPath
& ".\gradlew.bat" ":shared:jvmTest" "--tests" "*SubscriptionParserTest.liveSubscriptionCompilesWhenExplicitlyProvided" "--rerun-tasks" | Out-Null
if ($LASTEXITCODE -ne 0) {
  throw "Live subscription compilation failed"
}

$core = (Resolve-Path "packaging\resources\windows\sing-box.exe").Path
$coreProcess = $null
try {
  $coreProcess = Start-Process -FilePath $core `
    -ArgumentList @("run", "-c", $configPath) `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutPath `
    -RedirectStandardError $stderrPath `
    -PassThru
  Start-Sleep -Seconds 3
  if ($coreProcess.HasExited) {
    $diagnostic = (Get-Content -LiteralPath $stderrPath -ErrorAction SilentlyContinue |
      Select-Object -Last 12) -join [Environment]::NewLine
    throw "sing-box exited with $($coreProcess.ExitCode): $diagnostic"
  }
  $publicIp = & curl.exe -fsS --max-time 25 `
    --proxy "socks5h://127.0.0.1:20808" `
    "https://api.ipify.org"
  if ($LASTEXITCODE -ne 0 -or $publicIp -notmatch "^\d{1,3}(\.\d{1,3}){3}$") {
    throw "Proxy traffic check failed"
  }
  Write-Output "SINGBOX_PROXY_OK"
  Write-Output "PUBLIC_IP_REDACTED"
} finally {
  if ($coreProcess -and !$coreProcess.HasExited) {
    Stop-Process -Id $coreProcess.Id -Force
  }
  if (Test-Path -LiteralPath $configPath) {
    Remove-Item -LiteralPath $configPath -Force
  }
  if (Test-Path -LiteralPath $stdoutPath) {
    Remove-Item -LiteralPath $stdoutPath -Force
  }
  if (Test-Path -LiteralPath $stderrPath) {
    Remove-Item -LiteralPath $stderrPath -Force
  }
}
