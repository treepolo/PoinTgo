# Goal 2 六面校正 UI 驗證

更新：2026-09-14

## 採用方案

依使用者選擇，採用「原廠圖＋重力自動提示」：保留原始 APK 的裝置六面圖，並以目前資料的重力向量提示可能面向；系統只提示，不會自動替使用者取樣或覆寫 profile。

## 原廠資源

原始 APK 的六張資源已確認並複製到私人副本：

- `assets/img/device/front.png`
- `assets/img/device/back.png`
- `assets/img/device/left.png`
- `assets/img/device/right.png`
- `assets/img/device/top.png`
- `assets/img/device/bottom.png`

建置腳本會把它們放回 APK 的 `flutter_assets/assets/img/device/`，校正精靈使用相同的檔案，不重新繪製示意圖。

## UI 行為

- 畫面明確定義：以 `front.png` 為感測器正面；正面朝向使用者時，外殼左側才是「左面」。
- 六面按鈕以 2×3 排列；選取面會顯示對應原廠圖與「取樣目前面」按鈕。
- 重力提示取最近 60 筆衍生樣本的低通重力平均，映射為左／右／前／背／頂／底，並顯示信心與重力向量。
- 取樣與六面品質閘門仍由使用者確認；品質不足時不儲存 profile。
- 校正內容放入可捲動區，確保「靜止陀螺儀校正」與「完成六面校正並儲存 profile」在小螢幕仍可觸及。

## 驗證結果

- `test-vendor-engine.ps1`：`VENDOR_ENGINE_TEST_OK`。
- 隔離 JDK／Android SDK／Apktool 建置完成，APK v2/v3 簽章驗證成功。
- ADB 安裝結果：`Success`。
- 實機 UIAutomator 確認 `ImageView`、六面按鈕、重力提示、取樣按鈕與可捲動底部完成按鈕存在；切換左／右會同步更新選取文字與取樣按鈕；資料不足按完成不閃退。

本次 UI 驗證時感測器連線曾出現 GATT direct connection timeout，因此尚未把實際六面姿態標成完成；待感測器重新進入等待連線狀態後，再依圖示逐面取樣並記錄品質結果。
