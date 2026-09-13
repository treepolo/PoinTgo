# Goal 2 進度與驗證紀錄

更新：2026-09-14

## 已完成

- [x] Phase 0 AOT／BLE／登入／locale 證據整理（見 `PHASE0_EVIDENCE.md`）。
- [x] 原廠六面加速度校正命令與校正資料 parser 入口索引。
- [x] 原廠 `GlobalAcceleration`、`GlobalVelocity`、`RotationQuaternion` 資料模型索引。
- [x] 私人副本訪客優先 launcher；本機功能不要求登入。
- [x] 登入按鈕仍可進入原廠 Flutter Google 登入頁；官方套件未修改。
- [x] `VendorMotionEngine`：校正 profile、重力／姿態估計、全域線性加速度、線性速度、角速度、角加速度與四元數。
- [x] 原廠六面圖＋重力自動提示校正精靈：面向定義、可捲動底部操作、實機 UIAutomator 與資料不足邊界測試通過；見 `GOAL2_CALIBRATION_UI_VALIDATION_20260914.md`。
- [x] 動作模組：通用、甩球／投擲、角運動、VBT、1RM、跳躍、反向跳／CMJ；事件、次數與中間量都保留。
- [x] 1RM 匯出 Epley、Brzycki、Lander、Mayhew、O'Conner 與 LVP 重建欄位。
- [x] 四面板逐點圖表：線性加速度、線性速度、角速度、角加速度；事件線標記。
- [x] CSV／JSON 逐筆匯出 raw、校正後、重力、全域資料、速度、角資料、四元數、事件與算法版本。
- [x] 實機匯出 CSV／JSON 並解析確認各 5,118 筆樣本及 `analysis`、`profile`、`algorithmVersion` 欄位。
- [x] `build-goal2.ps1` 合併官方 `split_config.zh.apk` 的 Traditional Chinese values，並宣告 V2 分析頁。
- [x] 隔離 JDK／Android SDK／Apktool 建置、v2/v3 簽章驗證成功。
- [x] Samsung SM-N9810 實機安裝；launcher、自由分析、訪客相容頁、校正對話框及可選登入路徑無啟動崩潰。
- [x] 引擎合成資料 smoke test：600 samples，線性與角加速度峰值非零，JSON 可序列化。
- [x] 確定性 `VendorMotionEngine` regression vectors：六面校正、通用／甩球／角運動、VBT、1RM、跳躍／CMJ。
- [x] Calibration quality regression; see `GOAL2_CALIBRATION_VALIDATION_20260914.md`.
- [x] BLE 資料品質計數與 JSON metadata：notification／解碼／忽略／格式異常／時間間隔／回退與 received bytes。
- [x] 全 session min/max envelope 圖表與觸控游標，不再只繪製尾端 1,000 筆。
- [x] 校正 profile 匯出實際 bias／scale／gyro bias；VBT 匯出逐 rep 中間量與繁體中文摘要標籤。
- [x] 控制列固定在圖表前，記錄期間可直接停止／匯出，降低圖表觸控造成的捲動阻礙。
- [x] 最新實機短 session：2,792 notification／2,769 解碼封包／5,538 samples，無 >40 ms gap、無 timestamp regression；見 `GOAL2_REAL_DEVICE_VALIDATION_20260914.md`。

## 尚待完成的實機驗證

- [x] 感測器保持等待連線時，完成一次 V2 自有 GATT 連線並取得長段 notification（約 88 秒：2,582 封包／5,118 樣本）；實測紀錄見 `GOAL2_VALIDATION_20260914.md`。
- [ ] 以官方畫面與相同受控動作建立 golden vector，量化四種連續信號與各模組事件誤差。
- [ ] 實際六面固定姿態各取樣一次，確認 profile bias/scale 與匯出欄位。
- [ ] 用含負荷的重量訓練、甩球／投擲、角運動、跳躍／反向跳 session 驗證次數與事件邊界。
- [ ] 檢查長時間（30 分鐘）封包遺失、timestamp wrap、背景切換與回放穩定性。

目前算法狀態標示為 `vendor-compatible-reconstruction-2026.09`；在 golden vector 完成前，不宣稱與官方 AOT 逐位元相同。
