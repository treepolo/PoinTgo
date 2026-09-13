param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$Dex,
    [Parameter(Mandatory = $true)][string]$Output
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path -LiteralPath $Apk)) { throw "APK not found: $Apk" }
if (-not (Test-Path -LiteralPath $Dex)) { throw "Dex not found: $Dex" }
Copy-Item -LiteralPath $Apk -Destination $Output -Force

$archive = [System.IO.Compression.ZipFile]::Open($Output, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $existing = $archive.GetEntry('classes4.dex')
    if ($null -ne $existing) { $existing.Delete() }
    $entry = $archive.CreateEntry('classes4.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    $input = [System.IO.File]::OpenRead($Dex)
    $stream = $entry.Open()
    try { $input.CopyTo($stream) } finally { $stream.Dispose(); $input.Dispose() }
} finally {
    $archive.Dispose()
}
