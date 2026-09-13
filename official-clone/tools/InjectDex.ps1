param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$Dex,
    [Parameter(Mandatory = $true)][string]$Output,
    [string]$NativeLibRoot
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

    if ($NativeLibRoot -and (Test-Path -LiteralPath $NativeLibRoot)) {
        $root = (Resolve-Path -LiteralPath $NativeLibRoot).Path
        foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -File) {
            $relative = $file.FullName.Substring($root.Length).TrimStart('\', '/')
            $entryName = ('lib/' + $relative.Replace('\', '/'))
            $oldEntry = $archive.GetEntry($entryName)
            if ($null -ne $oldEntry) { $oldEntry.Delete() }
            $libEntry = $archive.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
            $libInput = [System.IO.File]::OpenRead($file.FullName)
            $libStream = $libEntry.Open()
            try { $libInput.CopyTo($libStream) } finally { $libStream.Dispose(); $libInput.Dispose() }
        }
    }
} finally {
    $archive.Dispose()
}
