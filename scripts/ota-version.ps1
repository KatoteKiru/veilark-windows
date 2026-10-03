# Display versions use three decimal fields; OTA counters remain compatible with
# already published 3xx releases. Never concatenate fields (0.4.0 would be 40).
function Get-VeilarkVersionCode {
  param([Parameter(Mandatory = $true)][string]$Version)
  if ($Version -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$') {
    throw 'Expected a canonical major.minor.patch version'
  }
  $major = [long]$Matches[1]
  $minor = [long]$Matches[2]
  $patch = [long]$Matches[3]
  if ($minor -ge 100 -or $patch -ge 100) {
    throw 'Minor and patch must be below 100 for the OTA counter schema'
  }
  $code = $major * 10000L + $minor * 100L + $patch
  if ($code -lt 1 -or $code -gt [int]::MaxValue) { throw 'OTA counter out of range' }
  return [int]$code
}
