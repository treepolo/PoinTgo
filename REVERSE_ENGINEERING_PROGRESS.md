# 原廠程式拆包／反編譯／反組譯進度

更新：2026-09-15

## 進度檢核

- [ ] Phase A：所有官方 split 完整雜湊與 split 關係清冊
- [ ] Phase B：資源、前端 asset、locale、Dex/Smali、plugin 清冊
- [ ] Phase C：Flutter AOT metadata／函式／字串／物件／呼叫邊／ARM64 清冊
- [ ] Phase D：前端入口、狀態與資料流的逐群組分析
- [ ] Phase E：證據矩陣、未知項、驗證命令與 GitHub checkpoint

## 證據分級

- **A：直接證據**：原始 APK bytes、Manifest／資源檔、Dex/Smali 指令、AOT 反組譯、AOT object/string/call graph、已保存的去識別化輸出。
- **B：交叉推論**：多個 A 級來源一致，但仍可能受 Flutter AOT 去除名稱／最佳化影響。
- **C：待驗證**：只有字串、UI 觀察或合理物理推導，尚無原廠 runtime golden vector。

## 目前已知限制

官方是 Flutter release/AOT；Dart 原始碼與符號不在 APK。`unflutter`／Capstone 可以得到 pseudo-Dart、ARM64 與 metadata，但不能保證還原原始變數名、註解或每個公式。這是分析結果的明確邊界，不是把未知欄位偽裝成已完成。
