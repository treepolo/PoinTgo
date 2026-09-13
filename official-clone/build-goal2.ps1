param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AnalysisRoot = Join-Path $ProjectRoot 'analysis\official_clone'
$DecodedRoot = Join-Path $AnalysisRoot 'decoded\base'
$ZhRoot = Join-Path $AnalysisRoot 'build\zh-decoded\res'

if (-not (Test-Path $DecodedRoot)) { throw "Decoded base APK not found: $DecodedRoot" }
if (-not (Test-Path $ZhRoot)) {
    throw "Decoded Chinese split not found: $ZhRoot (decode split_config.zh.apk first)"
}

# A repacked single APK does not install split_config.zh.apk. Merge the
# official Traditional Chinese values into base before the isolated builder
# copies and rebuilds the decoded tree.
foreach ($locale in @('values-zh-rTW', 'values-zh-rHK', 'values-zh-rCN')) {
    $source = Join-Path $ZhRoot $locale
    if (-not (Test-Path $source)) { continue }
    $target = Join-Path (Join-Path $DecodedRoot 'res') $locale
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Copy-Item -Path (Join-Path $source '*') -Destination $target -Force
}

# Declare the expanded native workbench; the original build script already
# declares CloneLauncherActivity and the legacy analyzer.
$manifestPath = Join-Path $DecodedRoot 'AndroidManifest.xml'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
if ($manifest -notmatch 'MotionAnalyzerActivityV2') {
    $manifest = $manifest.Replace('</application>', @'
        <activity android:name="com.treepolo.pointgo.clone.MotionAnalyzerActivityV2"
            android:exported="false" android:label="原廠相容分析" />
    </application>
'@)
    [IO.File]::WriteAllText($manifestPath, $manifest, [Text.UTF8Encoding]::new($false))
}

$buildScript = Join-Path $PSScriptRoot 'build-official-clone.ps1'
if ($Clean) { & $buildScript -Clean } else { & $buildScript }
if ($LASTEXITCODE -ne 0) { throw "Official clone build failed ($LASTEXITCODE)" }
