param(
  [string]$AndroidForeground = "..\veilark-android\app\src\main\res\drawable-xxxhdpi\veilark_logo_foreground_v2.png",
  [string]$ResourceDirectory = "desktopApp\src\main\resources"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$foregroundPath = [IO.Path]::GetFullPath((Join-Path $root $AndroidForeground))
$resourcePath = [IO.Path]::GetFullPath((Join-Path $root $ResourceDirectory))
$logoPath = Join-Path $resourcePath "veilark-logo.png"
$appIconPath = Join-Path $resourcePath "veilark-app-icon.png"
$icoPath = Join-Path $resourcePath "veilark.ico"

if (-not (Test-Path -LiteralPath $foregroundPath -PathType Leaf)) {
  throw "Android brand asset not found: $foregroundPath"
}

New-Item -ItemType Directory -Path $resourcePath -Force | Out-Null
Copy-Item -LiteralPath $foregroundPath -Destination $logoPath -Force

function New-ScaledBitmap {
  param(
    [System.Drawing.Image]$Source,
    [int]$Size
  )

  $bitmap = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  try {
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.DrawImage($Source, 0, 0, $Size, $Size)
  } finally {
    $graphics.Dispose()
  }
  return $bitmap
}

$canvasSize = 1024
$canvas = New-Object System.Drawing.Bitmap($canvasSize, $canvasSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$foreground = [System.Drawing.Image]::FromFile($foregroundPath)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
try {
  $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
  $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml("#11151A"))
  $circleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#1D242C"))
  try {
    $circleInset = [int][Math]::Round($canvasSize * 8 / 108)
    $circleSize = $canvasSize - 2 * $circleInset
    $graphics.FillEllipse($circleBrush, $circleInset, $circleInset, $circleSize, $circleSize)
  } finally {
    $circleBrush.Dispose()
  }
  $foregroundInset = [int][Math]::Round($canvasSize * 16 / 108)
  $foregroundSize = $canvasSize - 2 * $foregroundInset
  $graphics.DrawImage($foreground, $foregroundInset, $foregroundInset, $foregroundSize, $foregroundSize)
} finally {
  $graphics.Dispose()
  $foreground.Dispose()
}

try {
  $canvas.Save($appIconPath, [System.Drawing.Imaging.ImageFormat]::Png)

  $sizes = @(16, 24, 32, 48, 64, 128, 256)
  $frames = foreach ($size in $sizes) {
    $scaled = New-ScaledBitmap -Source $canvas -Size $size
    $stream = New-Object IO.MemoryStream
    try {
      $scaled.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
      [byte[]]$bytes = $stream.ToArray()
      [pscustomobject]@{ Size = $size; Bytes = $bytes }
    } finally {
      $stream.Dispose()
      $scaled.Dispose()
    }
  }

  $file = [IO.File]::Open($icoPath, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None)
  $writer = New-Object IO.BinaryWriter($file)
  try {
    $writer.Write([uint16]0)
    $writer.Write([uint16]1)
    $writer.Write([uint16]$frames.Count)
    $offset = 6 + 16 * $frames.Count
    foreach ($frame in $frames) {
      $dimension = if ($frame.Size -eq 256) { 0 } else { $frame.Size }
      $writer.Write([byte]$dimension)
      $writer.Write([byte]$dimension)
      $writer.Write([byte]0)
      $writer.Write([byte]0)
      $writer.Write([uint16]1)
      $writer.Write([uint16]32)
      $writer.Write([uint32]$frame.Bytes.Length)
      $writer.Write([uint32]$offset)
      $offset += $frame.Bytes.Length
    }
    foreach ($frame in $frames) {
      $writer.Write($frame.Bytes)
    }
  } finally {
    $writer.Dispose()
  }
} finally {
  $canvas.Dispose()
}

Write-Host "Generated Veilark Windows brand assets from the Android source asset."
