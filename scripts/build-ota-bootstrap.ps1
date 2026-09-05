param(
  [Parameter(Mandatory = $true)]
  [string]$InnerInstaller,

  [Parameter(Mandatory = $true)]
  [string]$Output,

  [switch]$VerificationOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$innerPath = (Resolve-Path -LiteralPath $InnerInstaller).Path
$inner = Get-Item -LiteralPath $innerPath
if (-not $inner.Name.StartsWith('Veilark-') -or -not $inner.Name.EndsWith('.exe')) {
  throw 'The embedded installer must be named Veilark-<version>.exe'
}
$versionName = $inner.Name.Substring(8, $inner.Name.Length - 12)
if ($versionName -notmatch '^\d+\.\d+\.\d+$') {
  throw 'The embedded installer version must contain three numeric components'
}

$updateSource = Get-Content -Raw (Join-Path $projectRoot 'shared\src\jvmMain\kotlin\uk\senyasenyavski\veilark\update\UpdateClient.kt')
$gradleSource = Get-Content -Raw (Join-Path $projectRoot 'desktopApp\build.gradle.kts')
$sourceVersion = [regex]::Match($updateSource, 'CURRENT_VERSION_NAME\s*=\s*"([^"]+)"').Groups[1].Value
$sourceCode = [regex]::Match($updateSource, 'CURRENT_VERSION_CODE\s*=\s*(\d+)').Groups[1].Value
$packageVersion = [regex]::Match($gradleSource, 'packageVersion\s*=\s*"([^"]+)"').Groups[1].Value
$upgradeUuid = [regex]::Match($gradleSource, 'upgradeUuid\s*=\s*"([^"]+)"').Groups[1].Value
$expectedCode = [int](($versionName.Split('.') | ForEach-Object { [int]$_ }) -join '')
if ($sourceVersion -ne $versionName -or $packageVersion -ne $versionName -or [int]$sourceCode -ne $expectedCode) {
  throw "OTA version drift: payload=$versionName client=$sourceVersion/$sourceCode package=$packageVersion"
}
if ($upgradeUuid -ne '47a6cdd8-9630-4fa5-a2fd-c29c5774dc1a') {
  throw "MSI UpgradeCode drift: $upgradeUuid"
}

$compilerCandidates = @(
  (Get-Command csc.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
  (Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'),
  (Join-Path $env:SystemRoot 'Microsoft.NET\Framework\v4.0.30319\csc.exe')
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
$compiler = $compilerCandidates | Select-Object -First 1
if (-not $compiler) {
  throw 'A Windows C# compiler was not found'
}

$outputPath = [IO.Path]::GetFullPath($Output)
$outputDirectory = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$temporary = Join-Path ([IO.Path]::GetTempPath()) ('veilark-ota-bootstrap-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporary | Out-Null
try {
  $payloadHash = (Get-FileHash -LiteralPath $innerPath -Algorithm SHA256).Hash.ToUpperInvariant()
  $payloadSize = $inner.Length.ToString([Globalization.CultureInfo]::InvariantCulture)
  $hashResource = Join-Path $temporary 'payload.sha256'
  $sizeResource = Join-Path $temporary 'payload.size'
  $assemblyInfo = Join-Path $temporary 'AssemblyInfo.cs'
  [IO.File]::WriteAllText($hashResource, $payloadHash, [Text.Encoding]::ASCII)
  [IO.File]::WriteAllText($sizeResource, $payloadSize, [Text.Encoding]::ASCII)
  $assemblyVersion = "$versionName.0"
  [IO.File]::WriteAllText(
    $assemblyInfo,
    "using System.Reflection;`r`n[assembly: AssemblyVersion(`"$assemblyVersion`")]`r`n[assembly: AssemblyFileVersion(`"$assemblyVersion`")]`r`n",
    [Text.Encoding]::ASCII
  )
  $manifest = if ($VerificationOnly) {
    Join-Path $projectRoot 'installer-bootstrap\as-invoker.manifest'
  } else {
    Join-Path $projectRoot 'installer-bootstrap\require-administrator.manifest'
  }
  $source = Join-Path $projectRoot 'installer-bootstrap\VeilarkOtaBootstrap.cs'
  $target = if ($VerificationOnly) { '/target:exe' } else { '/target:winexe' }
  $arguments = @(
    '/nologo',
    $target,
    '/platform:x64',
    '/optimize+',
    "/win32icon:$(Join-Path $projectRoot 'desktopApp\src\main\resources\veilark.ico')",
    "/win32manifest:$manifest",
    "/resource:$innerPath,Veilark.InstallerPayload",
    "/resource:$hashResource,Veilark.PayloadSha256",
    "/resource:$sizeResource,Veilark.PayloadSize",
    "/out:$outputPath",
    $source,
    $assemblyInfo
  )
  & $compiler @arguments
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $outputPath -PathType Leaf)) {
    throw "OTA bootstrap compilation failed with exit code $LASTEXITCODE"
  }
  $wrapper = Get-Item -LiteralPath $outputPath
  $wrapperHash = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash
  $signature = (Get-AuthenticodeSignature -LiteralPath $outputPath).Status
  [pscustomobject]@{
    VersionName = $versionName
    PayloadSize = $inner.Length
    PayloadSha256 = $payloadHash
    WrapperSize = $wrapper.Length
    WrapperSha256 = $wrapperHash
    Authenticode = $signature.ToString()
    VerificationOnly = [bool]$VerificationOnly
  }
} finally {
  if (Test-Path -LiteralPath $temporary) {
    $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
    $expectedParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    if ((Split-Path -Parent $resolvedTemporary).TrimEnd('\') -ne $expectedParent -or
        (Split-Path -Leaf $resolvedTemporary) -notmatch '^veilark-ota-bootstrap-[a-f0-9]{32}$') {
      throw 'Refusing cleanup outside the owned bootstrap temporary directory'
    }
    Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
  }
}
