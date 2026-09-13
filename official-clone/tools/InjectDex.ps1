param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$Dex,
    [Parameter(Mandatory = $true)][string]$Output,
    [string]$NativeLibRoot,
    [string]$JarTool = 'jar.exe'
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
    foreach ($oldLib in @($archive.Entries | Where-Object { $_.FullName.StartsWith('lib/') })) {
        $oldLib.Delete()
    }
    $entry = $archive.CreateEntry('classes4.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    $input = [System.IO.File]::OpenRead($Dex)
    $stream = $entry.Open()
    try { $input.CopyTo($stream) } finally { $stream.Dispose(); $input.Dispose() }
} finally {
    $archive.Dispose()
}

if ($NativeLibRoot -and (Test-Path -LiteralPath $NativeLibRoot)) {
    $root = (Resolve-Path -LiteralPath $NativeLibRoot).Path
    $staging = Join-Path ([IO.Path]::GetTempPath()) ('pointgo-lib-' + [guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Force -Path (Join-Path $staging 'lib') | Out-Null
        Copy-Item -Path (Join-Path $root '*') -Destination (Join-Path $staging 'lib') -Recurse -Force
        $jarArgs = @('--update','--file',$Output,'--no-compress','-C',$staging,'lib')
        & $JarTool @jarArgs
        if ($LASTEXITCODE -ne 0) { throw "jar failed while adding native libraries ($LASTEXITCODE)" }
    } finally {
        Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
    }
}
