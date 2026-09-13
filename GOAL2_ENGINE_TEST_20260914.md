# Goal 2 引擎回歸測試（2026-09-14）

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\official-clone\test-vendor-engine.ps1
```

結果：`VENDOR_ENGINE_TEST_OK`

測試向量涵蓋：

- 六面加速度 bias／scale 與陀螺儀靜止偏置校正。
- 引擎逐點輸出的線性加速度、線性速度、角速度、角加速度與 JSON 版本欄位。
- 甩球／投擲與角運動各 3 次週期及事件種類。
- VBT 3 次速度峰值、速度損失與 1RM 的 Epley、Brzycki、Lander、Mayhew、O'Conner、LVP 欄位。
- 跳躍與反向跳／CMJ 各 2 次起跳／飛行／落地、下沉事件、跳高與 RSI。

測試使用反射建立受控 `DerivedSample` 僅驗證事件邊界與公式鍵值，不把測試資料打包進 APK；手機實機資料仍以 `GOAL2_QUALITY_VALIDATION_20260914.md` 為準。算法版本持續標示為 `vendor-compatible-reconstruction-2026.09`，尚未宣稱與原廠 AOT 逐位元相同。
