# Goal 2 分析欄位語系驗證（2026-09-14）

自由分析摘要原先會直接顯示 `linearAccelerationRmsMps2`、`sampleCount`、`vbtPeakVelocityMps` 等內部鍵名，這是使用者先前看到英文介面的來源之一。現在所有已輸出的通用、VBT、1RM、跳躍／CMJ metric 都有繁體中文 label；算法版本字串仍保留英文識別碼，供稽核與版本追蹤。

已用隔離 JDK 編譯／執行 `official-clone/test-vendor-engine.ps1`（`VENDOR_ENGINE_TEST_OK`），並用隔離 Android 建置工具鏈完成 APK 資源與 Java 編譯、v2/v3 簽章驗證。
