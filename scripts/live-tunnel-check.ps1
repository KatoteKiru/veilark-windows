<#
.SYNOPSIS
  Runs the headless Veilark connect/disconnect check with administrator rights.

.DESCRIPTION
  Creating a WinTUN adapter requires elevation, so the check itself has to run
  elevated. Gradle stays unelevated: it only compiles and exports the runtime
  class path, and a generated command file carries the elevated JVM invocation.
  Output goes to a log file because an elevated process cannot inherit the
  calling console's streams.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\live-tunnel-check.ps1
  powershell -ExecutionPolicy Bypass -File .\scripts\live-tunnel-check.ps1 -Engine trusttunnel -Cycles 3
#>
[CmdletBinding()]
param(
  [ValidateSet('', 'singbox', 'trusttunnel')]
  [string]$Engine = '',
  [int]$HoldSeconds = 12,
  [int]$Cycles = 1
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $projectRoot
try {
  Write-Host 'Compiling and exporting the runtime class path...'
  & (Join-Path $projectRoot 'gradlew.bat') --offline :helper:exportRuntimeClasspath | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "Gradle failed to prepare the class path (exit code $LASTEXITCODE)"
  }

  $classpathFile = Join-Path $projectRoot 'helper\build\runtime-classpath.txt'
  if (-not (Test-Path -LiteralPath $classpathFile)) {
    throw "Missing $classpathFile"
  }
  $classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()

  $java = ''
  if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $candidate) { $java = $candidate }
  }
  if (-not $java) {
    $java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
  }
  if (-not $java) {
    throw 'java.exe was not found. Set JAVA_HOME.'
  }

  $logFile = Join-Path $projectRoot 'build\live-tunnel-check.log'
  $commandFile = Join-Path $projectRoot 'build\live-tunnel-check.cmd'
  New-Item -ItemType Directory -Force -Path (Split-Path $logFile) | Out-Null
  if (Test-Path -LiteralPath $logFile) { Remove-Item -LiteralPath $logFile -Force }

  $arguments = @($Engine, $HoldSeconds, $Cycles) -join ' '
  $script = @"
@echo off
chcp 65001 >nul
cd /d "$projectRoot"
"$java" -Dfile.encoding=UTF-8 -cp "$classpath" uk.senyasenyavski.veilark.helper.LiveTunnelCheck $arguments > "$logFile" 2>&1
"@
  [System.IO.File]::WriteAllText($commandFile, $script, [System.Text.Encoding]::ASCII)

  Write-Host 'Starting the elevated check. Approve the UAC prompt...'
  $process = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList '/c', "`"$commandFile`"" `
    -Verb RunAs -Wait -PassThru
  Write-Host "Check finished with exit code $($process.ExitCode)"

  if (Test-Path -LiteralPath $logFile) {
    Write-Host '--- Check log ---'
    Get-Content -LiteralPath $logFile -Encoding UTF8
  } else {
    Write-Warning 'No log was produced. The UAC prompt was most likely declined.'
  }
} finally {
  Pop-Location
}
