param(
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$AnalysisRoot = Join-Path $ProjectRoot 'analysis\official_clone'
$ToolRoot = Join-Path $ProjectRoot 'analysis\toolchain'
$OriginalRoot = Join-Path $AnalysisRoot 'original'
$DecodedRoot = Join-Path $AnalysisRoot 'decoded\base'
$WorkRoot = Join-Path $AnalysisRoot 'build\decoded-base'
$NativeClasses = Join-Path $AnalysisRoot 'native-classes'
$NativeDex = Join-Path $AnalysisRoot 'native-dex'
$BuildRoot = Join-Path $AnalysisRoot 'build'
$OutputApk = Join-Path $ProjectRoot 'official-clone\build\pointgo-clone-debug.apk'

$JdkRoot = 'C:\Users\gg013\AppData\Local\Temp\pointgo-toolchain\jdk-17.0.20.1+1'
$env:JAVA_HOME = $JdkRoot
$AndroidRoot = Join-Path $ProjectRoot 'analysis\toolchain\android-sdk'
$AndroidJar = Join-Path $AndroidRoot 'platforms\android-35\android.jar'
$BuildTools = Join-Path $AndroidRoot 'build-tools\35.0.0'
$ApktoolJar = Join-Path $ToolRoot 'apktool\apktool.jar'
$Apktool = Join-Path $JdkRoot 'bin\java.exe'
$Javac = Join-Path $JdkRoot 'bin\javac.exe'
$Keytool = Join-Path $JdkRoot 'bin\keytool.exe'
$D8 = Join-Path $BuildTools 'd8.bat'
$ApktoolOut = Join-Path $BuildRoot 'unsigned-apktool.apk'
$InjectedApk = Join-Path $BuildRoot 'unsigned-injected.apk'
$AlignedApk = Join-Path $BuildRoot 'aligned.apk'
$KeystoreDir = Join-Path $AnalysisRoot 'keys'
$Keystore = Join-Path $KeystoreDir 'pointgo-clone-debug.jks'
$ZipInject = Join-Path $PSScriptRoot 'tools\InjectDex.ps1'

function Run([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $File $($Arguments -join ' ')"
    }
}

if (-not (Test-Path $DecodedRoot)) {
    throw "Decoded APK not found: $DecodedRoot (run apktool decode first)"
}
if (-not (Test-Path $OriginalRoot\base.apk)) {
    throw "Original base APK not found: $OriginalRoot\base.apk"
}
if (-not (Test-Path $JdkRoot)) { throw "Isolated JDK not found: $JdkRoot" }
if (-not (Test-Path $AndroidJar)) { throw "Android platform not found: $AndroidJar" }
if (-not (Test-Path $ApktoolJar)) { throw "Apktool not found: $ApktoolJar" }

if ($Clean) {
    Remove-Item -LiteralPath $WorkRoot, $NativeClasses, $NativeDex, $ApktoolOut, $InjectedApk, $AlignedApk -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $BuildRoot, $NativeClasses, $NativeDex, (Split-Path $OutputApk) | Out-Null

Write-Host '[1/7] Copy and patch decoded APK'
Remove-Item -LiteralPath $WorkRoot -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath $DecodedRoot -Destination $WorkRoot -Recurse

$manifestPath = Join-Path $WorkRoot 'AndroidManifest.xml'
$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8
$manifest = $manifest.Replace('package="kr.piehealthcare.point.sensor"', 'package="com.treepolo.pointgo.clone"')
$manifest = $manifest.Replace('android:authorities="kr.piehealthcare.point.sensor.', 'android:authorities="com.treepolo.pointgo.clone.')
$manifest = $manifest.Replace('android:name="kr.piehealthcare.point.sensor.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"', 'android:name="com.treepolo.pointgo.clone.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"')
$manifest = $manifest.Replace('android:name="com.pairip.application.Application"', 'android:name="com.pairip.application.Application"')
$manifest = [regex]::Replace($manifest, '(?s)<activity[^>]*android:name="com\.pairip\.licensecheck\.LicenseActivity".*?</activity>', '')
$manifest = [regex]::Replace($manifest, '(?s)\s*<meta-data\s+android:name="com\.android\.vending\.splits\.[^"]+"[^>]*/>', '')
$manifest = [regex]::Replace($manifest, '\s*<uses-permission\s+android:name="com\.android\.vending\.CHECK_LICENSE"\s*/>', '')
$manifest = [regex]::Replace($manifest, '\s+android:(?:requiredSplitTypes|splitTypes)="[^"]*"', '')
$manifest = [regex]::Replace($manifest, '\s*<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>', '')
$launcher = @'
        <activity android:name="com.treepolo.pointgo.clone.CloneLauncherActivity"
            android:exported="true" android:label="Poin+T GO 分析副本">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name="com.treepolo.pointgo.clone.MotionAnalyzerActivity"
            android:exported="false" android:label="自由分析" />
'@
$manifest = $manifest.Replace('</application>', "$launcher`n    </application>")
[IO.File]::WriteAllText($manifestPath, $manifest, [Text.UTF8Encoding]::new($false))

$smaliPath = Join-Path $WorkRoot 'smali_classes3\I5\n.smali'
$smali = Get-Content -LiteralPath $smaliPath -Raw -Encoding UTF8
$needle = '    invoke-virtual {v2}, Landroid/bluetooth/BluetoothGattCharacteristic;->getValue()[B'
if (-not $smali.Contains($needle)) { throw "BLE callback injection point not found: $smaliPath" }
$injection = @'
    check-cast v5, Lcom/example/flutter_mocap_lib/service/BLEService;
    new-instance v6, Landroid/content/Intent;
    const-string v7, "com.treepolo.pointgo.clone.RAW_PACKET"
    invoke-direct {v6, v7}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V
    const-string v7, "device_address"
    invoke-virtual {v6, v7, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;
    const-string v7, "packet"
    invoke-virtual {v6, v7, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;[B)Landroid/content/Intent;
    const-string v7, "com.treepolo.pointgo.clone"
    invoke-virtual {v6, v7}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;
    invoke-virtual {v5, v6}, Landroid/content/Context;->sendBroadcast(Landroid/content/Intent;)V
'@
$smali = $smali.Replace($needle, "$needle`n$injection")
$duplicate = '    check-cast v5, Lcom/example/flutter_mocap_lib/service/BLEService;'
$first = $smali.IndexOf($duplicate)
$second = $smali.IndexOf($duplicate, $first + $duplicate.Length)
if ($second -ge 0) { $smali = $smali.Remove($second, $duplicate.Length + 2) }
[IO.File]::WriteAllText($smaliPath, $smali, [Text.UTF8Encoding]::new($false))

$stringsPath = Join-Path $WorkRoot 'res\values\strings.xml'
if (Test-Path $stringsPath) {
    $strings = Get-Content -LiteralPath $stringsPath -Raw -Encoding UTF8
    $strings = $strings.Replace('Poin*T GO', 'Poin+T GO 分析副本')
    [IO.File]::WriteAllText($stringsPath, $strings, [Text.UTF8Encoding]::new($false))
}

$arm64Split = Join-Path $OriginalRoot 'split_config.arm64_v8a.apk'
if (Test-Path $arm64Split) {
    $libRoot = Join-Path $WorkRoot 'lib\arm64-v8a'
    New-Item -ItemType Directory -Force -Path $libRoot | Out-Null
    $tempLib = Join-Path $BuildRoot 'arm64-split'
    Remove-Item -LiteralPath $tempLib -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $tempLib | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($arm64Split, $tempLib)
    if (Test-Path (Join-Path $tempLib 'lib\arm64-v8a')) {
        Copy-Item -Path (Join-Path $tempLib 'lib\arm64-v8a\*') -Destination $libRoot -Force
    }
}

Write-Host '[2/7] Compile native launcher and analyzer'
Remove-Item -LiteralPath $NativeClasses -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $NativeClasses | Out-Null
$javaFiles = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName
$javacArgs = @('-encoding','UTF-8','-source','8','-target','8','-classpath',$AndroidJar,'-d',$NativeClasses) + $javaFiles
Run $Javac $javacArgs

Write-Host '[3/7] Dex native classes'
Remove-Item -LiteralPath $NativeDex -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $NativeDex | Out-Null
$classFiles = Get-ChildItem -LiteralPath $NativeClasses -Recurse -Filter '*.class' | ForEach-Object FullName
$d8Args = @('--min-api','24','--lib',$AndroidJar,'--output',$NativeDex) + $classFiles
Run $D8 $d8Args

Write-Host '[4/7] Rebuild decoded APK with Apktool'
Remove-Item -LiteralPath $ApktoolOut -Force -ErrorAction SilentlyContinue
Run $Apktool @('-jar',$ApktoolJar,'b',$WorkRoot,'-o',$ApktoolOut)

Write-Host '[5/7] Inject native dex'
$dexPath = Join-Path $NativeDex 'classes.dex'
if (-not (Test-Path $dexPath)) { throw "D8 output not found: $dexPath" }
if (-not (Test-Path $ZipInject)) { throw "Dex injector not found: $ZipInject" }
Remove-Item -LiteralPath $InjectedApk -Force -ErrorAction SilentlyContinue
Run (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') @('-NoProfile','-ExecutionPolicy','Bypass','-File',$ZipInject,'-Apk',$ApktoolOut,'-Dex',$dexPath,'-Output',$InjectedApk,'-NativeLibRoot',(Join-Path $WorkRoot 'lib'))

Write-Host '[6/7] Align and sign'
if (-not (Test-Path $Keystore)) {
    New-Item -ItemType Directory -Force -Path $KeystoreDir | Out-Null
    Run $Keytool @('-genkeypair','-keystore',$Keystore,'-storepass','pointgo-clone','-keypass','pointgo-clone','-alias','pointgo','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=PoinTGo Clone,OU=Private,O=treepolo,L=Taipei,C=TW')
}
Remove-Item -LiteralPath $AlignedApk -Force -ErrorAction SilentlyContinue
Run (Join-Path $BuildTools 'zipalign.exe') @('-f','-p','4',$InjectedApk,$AlignedApk)
Remove-Item -LiteralPath $OutputApk -Force -ErrorAction SilentlyContinue
Run (Join-Path $BuildTools 'apksigner.bat') @('sign','--ks',$Keystore,'--ks-pass','pass:pointgo-clone','--out',$OutputApk,$AlignedApk)

Write-Host '[7/7] Verify signed APK'
Run (Join-Path $BuildTools 'apksigner.bat') @('verify','--verbose',$OutputApk)
Write-Host "Built: $OutputApk"
