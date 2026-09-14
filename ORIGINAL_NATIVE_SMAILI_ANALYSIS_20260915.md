# 原廠 Android 原生外殼／Smali 索引（2026-09-15）

輸入是 clean Apktool decode 的原廠 `base.apk`；clone decode 沒有混入。這份報告只描述可直接從 Manifest/Smali 讀到的 Android 層，不把 Flutter AOT 的推論冒充成 Java/Kotlin source。

## Manifest

- package：`kr.piehealthcare.point.sensor`
- application：`com.pairip.application.Application`
- permissions：**33**
- components：**34**

| type | name | exported | permission |
|---|---|---|---|
| `activity` | `kr.piehealthcare.point.sensor.MainActivity` | `true` | `` |
| `activity` | `io.flutter.plugins.urllauncher.WebViewActivity` | `false` | `` |
| `activity` | `com.google.firebase.auth.internal.GenericIdpActivity` | `true` | `` |
| `activity` | `com.google.firebase.auth.internal.RecaptchaActivity` | `true` | `` |
| `activity` | `androidx.credentials.playservices.controllers.identityauth.HiddenActivity` | `false` | `` |
| `activity` | `androidx.credentials.playservices.controllers.identitycredentials.IdentityCredentialApiHiddenActivity` | `false` | `` |
| `activity` | `com.google.android.gms.auth.api.signin.internal.SignInHubActivity` | `false` | `` |
| `activity` | `com.google.android.gms.common.api.GoogleApiActivity` | `false` | `` |
| `activity` | `com.google.android.play.core.common.PlayCoreDialogWrapperActivity` | `false` | `` |
| `activity` | `com.pairip.licensecheck.LicenseActivity` | `false` | `` |
| `service` | `kr.piehealthcare.point.sensor.ScreenRecorderService` | `false` | `` |
| `service` | `com.google.firebase.components.ComponentDiscoveryService` | `false` | `` |
| `service` | `com.example.flutter_mocap_lib.service.BLEService` | `false` | `` |
| `service` | `androidx.camera.core.impl.MetadataHolderService` | `false` | `` |
| `service` | `io.flutter.plugins.firebase.messaging.FlutterFirebaseMessagingBackgroundService` | `false` | `android.permission.BIND_JOB_SERVICE` |
| `service` | `io.flutter.plugins.firebase.messaging.FlutterFirebaseMessagingService` | `false` | `` |
| `service` | `androidx.credentials.playservices.CredentialProviderMetadataHolder` | `false` | `` |
| `service` | `com.google.android.gms.auth.api.signin.RevocationBoundService` | `true` | `com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION` |
| `service` | `com.google.firebase.messaging.FirebaseMessagingService` | `false` | `` |
| `service` | `com.google.firebase.sessions.SessionLifecycleService` | `false` | `` |
| `service` | `com.google.android.gms.measurement.AppMeasurementService` | `false` | `` |
| `service` | `com.google.android.gms.measurement.AppMeasurementJobService` | `false` | `android.permission.BIND_JOB_SERVICE` |
| `service` | `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery` | `false` | `` |
| `service` | `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService` | `false` | `android.permission.BIND_JOB_SERVICE` |
| `receiver` | `com.dexterous.flutterlocalnotifications.ScheduledNotificationReceiver` | `false` | `` |
| `receiver` | `com.dexterous.flutterlocalnotifications.ScheduledNotificationBootReceiver` | `false` | `` |
| `receiver` | `io.flutter.plugins.firebase.messaging.FlutterFirebaseMessagingReceiver` | `true` | `com.google.android.c2dm.permission.SEND` |
| `receiver` | `com.google.firebase.iid.FirebaseInstanceIdReceiver` | `true` | `com.google.android.c2dm.permission.SEND` |
| `receiver` | `com.google.android.gms.measurement.AppMeasurementReceiver` | `false` | `` |
| `receiver` | `androidx.profileinstaller.ProfileInstallReceiver` | `true` | `android.permission.DUMP` |
| `receiver` | `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver` | `false` | `` |
| `provider` | `io.flutter.plugins.firebase.messaging.FlutterFirebaseMessagingInitProvider` | `false` | `` |
| `provider` | `com.google.firebase.provider.FirebaseInitProvider` | `false` | `` |
| `provider` | `androidx.startup.InitializationProvider` | `false` | `` |

## Smali 統計

- Smali files：**15,400**；classes：**15,400**；原廠 package classes：**2**

| Dex root | files |
|---|---:|
| `smali` | 10,662 |
| `smali_classes2` | 134 |
| `smali_classes3` | 4,604 |

### 主要 package

| package | classes |
|---|---:|
| `com.google.android.gms.internal.firebase-auth-api` | 1,192 |
| `com.google.android.gms.internal.measurement` | 938 |
| `` | 596 |
| `com.google.android.recaptcha.internal` | 550 |
| `com.google.android.gms.measurement.internal` | 431 |
| `io.flutter.plugins.camerax` | 326 |
| `com.google.firebase.firestore.pipeline.evaluation` | 278 |
| `com.google.android.gms.internal.fido` | 234 |
| `com.google.android.gms.internal.auth` | 231 |
| `com.google.firebase.crashlytics.internal.model` | 179 |
| `G4` | 171 |
| `B` | 166 |
| `x` | 150 |
| `com.google.android.gms.common.api.internal` | 143 |
| `com.google.firebase.firestore` | 139 |
| `com.google.android.gms.identitycredentials` | 124 |
| `com.google.firebase.auth` | 124 |
| `com.google.android.gms.common.internal` | 122 |
| `com.google.protobuf` | 118 |
| `com.google.android.gms.fido.fido2.api.common` | 116 |
| `U3` | 112 |
| `N` | 105 |
| `H` | 104 |
| `com.google.firebase.sessions` | 103 |
| `com.google.firebase.auth.internal` | 94 |
| `L5` | 94 |
| `com.google.firebase.firestore.local` | 93 |
| `com.google.firebase.firestore.pipeline` | 93 |
| `androidx.fragment.app` | 86 |
| `com.google.android.play.core.integrity` | 86 |
| `com.google.firebase.firestore.core` | 84 |
| `D3` | 79 |
| `io.flutter.plugins.firebase.auth` | 79 |
| `com.fasterxml.jackson.databind.deser.std` | 78 |
| `W0` | 75 |
| `E4` | 75 |
| `A` | 73 |
| `m` | 73 |
| `com.fasterxml.jackson.databind.ser.std` | 71 |
| `com.google.android.gms.common` | 68 |

## 原生 API／字串命中

命中只代表該常數或字串確實存在於 Smali；它是 A 級存在證據，不代表每個呼叫情境都已由靜態分析證明。

### `flavor`：4 classes
- `com.google.firebase.database.collection.BuildConfig` · `analysis/reverse_engineering/original_decoded/base/smali/com/google/firebase/database/collection/BuildConfig.smali` · methods `1`
- `com.google.firebase.firestore.core.PipelineFlavor` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/com/google/firebase/firestore/core/PipelineFlavor.smali` · methods `6`
- `com.google.firebase.firestore.core.PipelineUtilKt` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/com/google/firebase/firestore/core/PipelineUtilKt.smali` · methods `8`
- `kr.piehealthcare.point.sensor.MainActivity` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/kr/piehealthcare/point/sensor/MainActivity.smali` · methods `4`

### `START_RECORDING_SERVICE`：2 classes
- `e6.b` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/e6/b.smali` · methods `9`
- `kr.piehealthcare.point.sensor.ScreenRecorderService` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/kr/piehealthcare/point/sensor/ScreenRecorderService.smali` · methods `5`

### `STOP_RECORDING_SERVICE`：2 classes
- `e6.b` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/e6/b.smali` · methods `9`
- `kr.piehealthcare.point.sensor.ScreenRecorderService` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/kr/piehealthcare/point/sensor/ScreenRecorderService.smali` · methods `5`

### `screen_recorder_channel`：2 classes
- `e6.c` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/e6/c.smali` · methods `30`
- `kr.piehealthcare.point.sensor.ScreenRecorderService` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/kr/piehealthcare/point/sensor/ScreenRecorderService.smali` · methods `5`

### `Screen recording ready`：1 classes
- `kr.piehealthcare.point.sensor.ScreenRecorderService` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/kr/piehealthcare/point/sensor/ScreenRecorderService.smali` · methods `5`

### `Poin*T GO`：1 classes
- `kr.piehealthcare.point.sensor.ScreenRecorderService` · `analysis/reverse_engineering/original_decoded/base/smali_classes3/kr/piehealthcare/point/sensor/ScreenRecorderService.smali` · methods `5`

### `pairip`：35 classes
- `com.pairip.application.Application` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/application/Application.smali` · methods `2`
- `com.pairip.licensecheck.ILicenseV2ResultListener$Stub` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/ILicenseV2ResultListener$Stub.smali` · methods `4`
- `com.pairip.licensecheck.ILicenseV2ResultListener` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/ILicenseV2ResultListener.smali` · methods `1`
- `com.pairip.licensecheck.LicenseActivity$$ExternalSyntheticLambda0` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/LicenseActivity$$ExternalSyntheticLambda0.smali` · methods `2`
- `com.pairip.licensecheck.LicenseActivity$$ExternalSyntheticLambda1` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/LicenseActivity$$ExternalSyntheticLambda1.smali` · methods `2`
- `com.pairip.licensecheck.LicenseActivity$$ExternalSyntheticLambda2` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/LicenseActivity$$ExternalSyntheticLambda2.smali` · methods `2`
- `com.pairip.licensecheck.LicenseActivity$ActivityType` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/LicenseActivity$ActivityType.smali` · methods `5`
- `com.pairip.licensecheck.LicenseActivity` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/LicenseActivity.smali` · methods `15`
- `com.pairip.licensecheck.LicenseCheckException` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/LicenseCheckException.smali` · methods `2`
- `com.pairip.licensecheck.LicenseClient$$ExternalSyntheticLambda0` · `analysis/reverse_engineering/original_decoded/base/smali/com/pairip/licensecheck/LicenseClient$$ExternalSyntheticLambda0.smali` · methods `2`
- 其餘 25 筆見 JSON。

### `firebase`：3898 classes
- `a.a` · `analysis/reverse_engineering/original_decoded/base/smali/a.1/a.smali` · methods `28`
- `A0.b` · `analysis/reverse_engineering/original_decoded/base/smali/A0/b.smali` · methods `2`
- `A0.n` · `analysis/reverse_engineering/original_decoded/base/smali/A0/n.smali` · methods `4`
- `A1.a` · `analysis/reverse_engineering/original_decoded/base/smali/A1/a.1.smali` · methods `23`
- `A1.B` · `analysis/reverse_engineering/original_decoded/base/smali/A1/B.smali` · methods `18`
- `A1.C` · `analysis/reverse_engineering/original_decoded/base/smali/A1/C.smali` · methods `2`
- `A1.q` · `analysis/reverse_engineering/original_decoded/base/smali/A1/q.smali` · methods `60`
- `androidx.fragment.app.x` · `analysis/reverse_engineering/original_decoded/base/smali/androidx/fragment/app/x.1.smali` · methods `28`
- `B.A1` · `analysis/reverse_engineering/original_decoded/base/smali/B/A1.smali` · methods `2`
- `B.n` · `analysis/reverse_engineering/original_decoded/base/smali/B/n.1.smali` · methods `8`
- 其餘 3888 筆見 JSON。

### `bluetooth`：56 classes
- `a.a` · `analysis/reverse_engineering/original_decoded/base/smali/a.1/a.smali` · methods `28`
- `A1.k` · `analysis/reverse_engineering/original_decoded/base/smali/A1/k.smali` · methods `3`
- `a3.l` · `analysis/reverse_engineering/original_decoded/base/smali/a3.1/l.smali` · methods `2`
- `a3.n` · `analysis/reverse_engineering/original_decoded/base/smali/a3.1/n.smali` · methods `13`
- `a3.q` · `analysis/reverse_engineering/original_decoded/base/smali/a3.1/q.smali` · methods `3`
- `B.A1` · `analysis/reverse_engineering/original_decoded/base/smali/B/A1.smali` · methods `2`
- `B.W1` · `analysis/reverse_engineering/original_decoded/base/smali/B/W1.smali` · methods `2`
- `b3.d` · `analysis/reverse_engineering/original_decoded/base/smali/b3/d.smali` · methods `1`
- `b3.e` · `analysis/reverse_engineering/original_decoded/base/smali/b3/e.smali` · methods `5`
- `b3.f` · `analysis/reverse_engineering/original_decoded/base/smali/b3/f.smali` · methods `5`
- 其餘 46 筆見 JSON。

### `mocap`：17 classes
- `A3.d` · `analysis/reverse_engineering/original_decoded/base/smali/A3/d.smali` · methods `3`
- `a3.n` · `analysis/reverse_engineering/original_decoded/base/smali/a3.1/n.smali` · methods `13`
- `B.A1` · `analysis/reverse_engineering/original_decoded/base/smali/B/A1.smali` · methods `2`
- `b3.e` · `analysis/reverse_engineering/original_decoded/base/smali/b3/e.smali` · methods `5`
- `com.example.flutter_mocap_lib.service.BLEService` · `analysis/reverse_engineering/original_decoded/base/smali/com/example/flutter_mocap_lib/service/BLEService.smali` · methods `8`
- `d3.a` · `analysis/reverse_engineering/original_decoded/base/smali/d3.1/a.smali` · methods `2`
- `d3.b` · `analysis/reverse_engineering/original_decoded/base/smali/d3.1/b.smali` · methods `2`
- `d3.c` · `analysis/reverse_engineering/original_decoded/base/smali/d3.1/c.smali` · methods `1`
- `d3.e` · `analysis/reverse_engineering/original_decoded/base/smali/d3.1/e.smali` · methods `3`
- `d3.f` · `analysis/reverse_engineering/original_decoded/base/smali/d3.1/f.smali` · methods `4`
- 其餘 7 筆見 JSON。

### `calibration`：0 classes

### `recording`：59 classes
- `A1.q` · `analysis/reverse_engineering/original_decoded/base/smali/A1/q.smali` · methods `60`
- `androidx.camera.camera2.compat.quirk.RepeatingStreamConstraintForVideoRecordingQuirk` · `analysis/reverse_engineering/original_decoded/base/smali/androidx/camera/camera2/compat/quirk/RepeatingStreamConstraintForVideoRecordingQuirk.smali` · methods `1`
- `androidx.camera.video.internal.compat.quirk.PreviewFreezeAfterHighSpeedRecordingQuirk` · `analysis/reverse_engineering/original_decoded/base/smali/androidx/camera/video/internal/compat/quirk/PreviewFreezeAfterHighSpeedRecordingQuirk.smali` · methods `2`
- `B.Q` · `analysis/reverse_engineering/original_decoded/base/smali/B/Q.smali` · methods `39`
- `com.google.android.gms.measurement.internal.zzlj` · `analysis/reverse_engineering/original_decoded/base/smali/com/google/android/gms/measurement/internal/zzlj.smali` · methods `63`
- `com.google.android.gms.measurement.internal.zzoa` · `analysis/reverse_engineering/original_decoded/base/smali/com/google/android/gms/measurement/internal/zzoa.smali` · methods `5`
- `D1.f` · `analysis/reverse_engineering/original_decoded/base/smali/D1/f.1.smali` · methods `5`
- `e0.g` · `analysis/reverse_engineering/original_decoded/base/smali/e0.1/g.1.smali` · methods `11`
- `e0.h` · `analysis/reverse_engineering/original_decoded/base/smali/e0.1/h.1.smali` · methods `5`
- `e0.H` · `analysis/reverse_engineering/original_decoded/base/smali/e0.1/H.smali` · methods `3`
- 其餘 49 筆見 JSON。

### `sensor`：46 classes
- `A.e0` · `analysis/reverse_engineering/original_decoded/base/smali/A/e0.smali` · methods `3`
- `A.r0` · `analysis/reverse_engineering/original_decoded/base/smali/A/r0.smali` · methods `6`
- `a.a` · `analysis/reverse_engineering/original_decoded/base/smali/a.1/a.smali` · methods `28`
- `A1.b` · `analysis/reverse_engineering/original_decoded/base/smali/A1/b.1.smali` · methods `30`
- `B.B0` · `analysis/reverse_engineering/original_decoded/base/smali/B/B0.smali` · methods `14`
- `B.c1` · `analysis/reverse_engineering/original_decoded/base/smali/B/c1.1.smali` · methods `4`
- `B.D0` · `analysis/reverse_engineering/original_decoded/base/smali/B/D0.smali` · methods `6`
- `b0.b` · `analysis/reverse_engineering/original_decoded/base/smali/b0.1/b.smali` · methods `10`
- `b1.g` · `analysis/reverse_engineering/original_decoded/base/smali/b1.1/g.smali` · methods `41`
- `C.c` · `analysis/reverse_engineering/original_decoded/base/smali/C/c.smali` · methods `27`
- 其餘 36 筆見 JSON。

## 可核對結論

- `MainActivity` 的 Flutter 外殼、plugin 註冊與 `flavor` MethodChannel 是原生層入口；產品頁面與運動邏輯在 Flutter AOT。
- `ScreenRecorderService` 的 START/STOP action、前景通知 channel 與 service 生命週期可由 Smali 直接核對。
- Android 原生清冊完整不等於伺服器端完整：Firebase/Auth/雲端規則不在 APK；其客戶端邊界另見 AOT call graph。

完整機器可讀資料：`analysis/native_smali_index.json`。
