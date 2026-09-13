# Goal 2 建置與復原

所有 Android 建置都使用專案外的隔離 JDK／SDK／Apktool 路徑，沒有使用使用者本機 Android Studio／Gradle cache。

## 建置

在專案根目錄執行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\official-clone\build-goal2.ps1 -Clean
```

腳本會：

1. 將 `analysis/official_clone/build/zh-decoded/res/values-zh-rTW`、`values-zh-rHK`、`values-zh-rCN` 合併到 decoded base，解決單 APK 漏裝語系 split。
2. 在 manifest 宣告 `MotionAnalyzerActivityV2`。
3. 呼叫既有 `build-official-clone.ps1`，以隔離 JDK、Android 35 platform、build-tools 35 和 Apktool 重建／對齊／v2/v3 簽章。

官方 APK 與 decoded/AOT 分析資料位於 `analysis/`，因檔案尺寸、個人裝置來源與原始封包隱私而被 `.gitignore` 排除；可由已授權手機的 `adb pull` 重新準備，GitHub checkpoint 則保存所有副本原始碼、建置腳本、證據與驗證紀錄。

## 目前產物

- APK：`official-clone/build/pointgo-clone-debug.apk`
- 私人副本套件：`com.treepolo.pointgo.clone`
- 官方套件：`kr.piehealthcare.point.sensor`（未修改）
- 2026-09-14 建置已通過 `apksigner verify --verbose` 的 v2/v3。
