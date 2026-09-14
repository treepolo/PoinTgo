# Poin*T Go 原廠靜態清冊（遞迴組譯統計修正版）

`ORIGINAL_APP_STATIC_INVENTORY_20260915.md` 的清冊仍可使用；其中 `asm_txt` 欄位只反映舊版第一層掃描。這份修正版以同一批原廠 APK/AOT 輸入，補上遞迴組譯統計，避免把 9,305 個第一層檔案誤解成全部組譯。

## 固定輸入

- package：`kr.piehealthcare.point.sensor`
- version：1.3.5（versionCode 2026090101）
- base APK：73,228,712 bytes；SHA-256 `94d764aa9234f2687ea4c129a4a3645c15b968e191229aeb4466e1c14c7f7a10`
- ARM64 split：48,665,828 bytes；SHA-256 `7e8f0cde58813901855c7df9162d9f76ee80a936fd6fedc0b786fee959d33dfd`
- Flutter ARM64 `libapp.so`：16,122,800 bytes；SHA-256 `7ea3d30692b31cf8a2346b422fbe1407663b5bd86693738790fbfa7392fb97b4`

## 清冊摘要

- clean Apktool base decode：15,943 files、198,148,620 bytes；15,400 Smali files。
- Flutter assets：158 files、47,013,076 bytes。
- AOT functions：46,790；classes：6,696；string refs：42,304；call edges：214,034。
- ARM64 組譯：**46,790 個 `.txt`、107,073,436 bytes**，分布於 **4,474** 個 owner/class 目錄。
- 組譯檔首列均可解析出實際 `0x...` PC；與 46,790 筆 function metadata 的連結是 **46,790/46,790**，未連結 **0**。

## 解讀

第一層 9,305 檔是舊版工具的掃描下限，不是原廠 AOT 的總量。真正的遞迴結果與重要 parser、校正、裝置、運動計算器入口，請看 [ORIGINAL_AOT_ASM_INVENTORY_20260915.md](ORIGINAL_AOT_ASM_INVENTORY_20260915.md)；演算法責任與 A/B/C 證據邊界請看 [ORIGINAL_ALGORITHM_EVIDENCE_20260915.md](ORIGINAL_ALGORITHM_EVIDENCE_20260915.md)。
