param(
  [string]$ResourceDirectory = "desktopApp\src\main\resources",
  [string]$ForegroundOutput = ""
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$resourcePath = [IO.Path]::GetFullPath((Join-Path $root $ResourceDirectory))
[xml]$svg = Get-Content -Raw (Join-Path $root "design\veilark-mark.svg")
New-Item -ItemType Directory -Path (Join-Path $resourcePath "icons") -Force | Out-Null

# Parse the authored mark, not a bitmap. Every DPI size is rasterized independently.
function New-MarkPath([string]$Data) {
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $tokens = [regex]::Matches($Data, '[MLHVZmlhvz]|-?\d+(?:\.\d+)?') | ForEach-Object Value
  $x = 0.0; $y = 0.0; $command = ''; $i = 0
  while ($i -lt $tokens.Count) {
    if ($tokens[$i] -match '^[MLHVZ]$') { $command = $tokens[$i]; $i++ }
    if ($command -eq 'Z') { $path.CloseFigure(); $command = ''; continue }
    switch ($command) {
      'M' { $x = [float]$tokens[$i]; $y = [float]$tokens[$i+1]; $i += 2; $path.StartFigure(); $command = 'L' }
      'L' { $nx = [float]$tokens[$i]; $ny = [float]$tokens[$i+1]; $i += 2; $path.AddLine($x,$y,$nx,$ny); $x=$nx; $y=$ny }
      'H' { $nx = [float]$tokens[$i]; $i++; $path.AddLine($x,$y,$nx,$y); $x=$nx }
      'V' { $ny = [float]$tokens[$i]; $i++; $path.AddLine($x,$y,$x,$ny); $y=$ny }
      default { throw "Unsupported brand path command" }
    }
  }
  return ,$path
}
function New-BrandBitmap([int]$Size, [bool]$Transparent = $false) {
  $bitmap = New-Object System.Drawing.Bitmap($Size,$Size,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bitmap)
  $tile = New-Object System.Drawing.Drawing2D.GraphicsPath
  $ink = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,243,244,246))
  $plate = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,28,29,33))
  try {
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $inset = [Math]::Max(0.5, $Size * .025)
    $extent = $Size - 2*$inset
    $r = $Size * .36
    $tile.AddArc($inset,$inset,$r,$r,180,90)
    $tile.AddArc($inset+$extent-$r,$inset,$r,$r,270,90)
    $tile.AddArc($inset+$extent-$r,$inset+$extent-$r,$r,$r,0,90)
    $tile.AddArc($inset,$inset+$extent-$r,$r,$r,90,90)
    $tile.CloseFigure()
    if (-not $Transparent) { $g.FillPath($plate,$tile) }
    # The 432-unit canonical viewport includes its own optical clear space.
    $g.ScaleTransform($Size/432.0,$Size/432.0)
    foreach ($element in $svg.svg.path) {
      $path = New-MarkPath $element.d
      try { $g.FillPath($ink,$path) } finally { $path.Dispose() }
    }
  } finally { $g.Dispose(); $tile.Dispose(); $ink.Dispose(); $plate.Dispose() }
  return ,$bitmap
}
$sizes = @(16,20,24,32,40,48,64,128,256)
$frames = foreach ($size in $sizes) {
  $bitmap = New-BrandBitmap $size
  $stream = New-Object IO.MemoryStream
  try {
    $bitmap.Save((Join-Path $resourcePath "icons\veilark-$size.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Save($stream,[System.Drawing.Imaging.ImageFormat]::Png)
    [pscustomobject]@{Size=$size; Bytes=$stream.ToArray()}
  } finally { $stream.Dispose(); $bitmap.Dispose() }
}
$canvas = New-BrandBitmap 512
try { $canvas.Save((Join-Path $resourcePath "veilark-app-icon.png"), [System.Drawing.Imaging.ImageFormat]::Png) } finally { $canvas.Dispose() }
$writer = New-Object IO.BinaryWriter([IO.File]::Open((Join-Path $resourcePath "veilark.ico"),[IO.FileMode]::Create))
try {
  $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$frames.Count)
  $offset = 6 + 16*$frames.Count
  foreach ($frame in $frames) {
    $dimension = if ($frame.Size -eq 256) { 0 } else { $frame.Size }
    $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
    $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([uint16]1); $writer.Write([uint16]32)
    $writer.Write([uint32]$frame.Bytes.Length); $writer.Write([uint32]$offset)
    $offset += $frame.Bytes.Length
  }
  foreach ($frame in $frames) { $writer.Write([byte[]]$frame.Bytes) }
} finally { $writer.Dispose() }
Write-Host "Generated canonical vector logo at 9 Windows DPI sizes."
if ($ForegroundOutput) {
  New-Item -ItemType Directory -Path (Split-Path -Parent ([IO.Path]::GetFullPath($ForegroundOutput))) -Force | Out-Null
  $foreground = New-BrandBitmap 432 $true
  try { $foreground.Save([IO.Path]::GetFullPath($ForegroundOutput), [System.Drawing.Imaging.ImageFormat]::Png) }
  finally { $foreground.Dispose() }
}
