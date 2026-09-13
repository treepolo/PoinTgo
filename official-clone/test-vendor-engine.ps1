$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$JdkRoot = 'C:\Users\gg013\AppData\Local\Temp\pointgo-toolchain\jdk-17.0.20.1+1'
$Javac = Join-Path $JdkRoot 'bin\javac.exe'
$Java = Join-Path $JdkRoot 'bin\java.exe'
$AndroidJar = Join-Path $ProjectRoot 'analysis\toolchain\android-sdk\platforms\android-35\android.jar'
$Output = Join-Path $ProjectRoot 'analysis\engine-test-classes'
$engine = Join-Path $ProjectRoot 'official-clone\src\com\treepolo\pointgo\clone\VendorMotionEngine.java'
$test = Join-Path $ProjectRoot 'official-clone\test\VendorMotionEngineTest.java'

foreach ($path in @($Javac, $Java, $AndroidJar, $engine, $test)) {
    if (-not (Test-Path $path)) { throw "Required test input not found: $path" }
}

Remove-Item -LiteralPath $Output -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Output | Out-Null
& $Javac -encoding UTF-8 -source 8 -target 8 -classpath $AndroidJar -d $Output $engine $test
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }
& $Java -cp "$Output;$AndroidJar" VendorMotionEngineTest
if ($LASTEXITCODE -ne 0) { throw "VendorMotionEngineTest failed ($LASTEXITCODE)" }
