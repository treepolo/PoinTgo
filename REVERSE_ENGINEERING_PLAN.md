# PoinTGo 原廠程式拆包／反編譯／反組譯計畫

日期：2026-09-15  
目標：對官方 Poin*T Go Android APK（含 base、ABI、density、語系 split）完成可重現的拆包、Java/Kotlin/Dex 反編譯、Flutter AOT 反組譯，以及包含前端畫面、路由、資源、BLE、演算法、登入與雲端邊界的程式理解索引。

## 範圍與完成定義

完成不是宣稱拿回原始 Dart 原始碼，而是：

1. 原始輸入 APK、雜湊、split 關係與工具版本可重現。
2. Manifest、資源、所有 Dex/Smali、Flutter assets、native libraries 均有清冊。
3. Java/Kotlin 可逆向部分有 class/package/entry-point 索引；Flutter AOT 有 Dart metadata、函式、字串、物件、呼叫邊與 ARM64 反組譯索引。
4. 前端每一個可觀察入口（啟動、登入、裝置、校正、動作、歷史、設定、通知／錄影）都有路由／狀態／資源／資料來源分析；不只分析資料庫與後端。
5. BLE 命令、GATT、封包 parser、資料模型與演算法入口以證據等級標示；無法從 AOT 證明的部分明確列為未知或相容重建。
6. 有進度／驗證檢核表、差距清單、GitHub checkpoint；不能把「工具跑完」誤報成「全部語義已證明」。

## 五個候選方法（自評）

評分：可重現性 25%、前端覆蓋率 25%、AOT 可理解度 25%、對原版行為保真度 15%、交付維護性 10%。

| 方案 | 作法 | 分數 | 決策 |
| --- | --- | ---: | --- |
| A | 保留全部 split；apktool／baksmali 做資源與 Dex；jadx 可讀視圖；Flutter AOT 用 unflutter、Capstone、字串／物件／呼叫圖與受控 runtime 證據交叉索引 | **9.6/10** | **採用**：涵蓋前端與 native/AOT，且每個結論可追溯 |
| B | 只用 jadx／apktool 產生 Java/Smali，忽略 Flutter AOT | 6.2/10 | Flutter 前端與主要算法會遺失，淘汰 |
| C | 只用 Flutter 專用反編譯器（blutter/unflutter）輸出 Dart 類似碼 | 7.7/10 | AOT 有幫助，但 Android manifest、split、plugin 與 native 邊界不足 |
| D | 直接修改官方 APK 後以 UI 行為推測程式 | 6.8/10 | 能觀察流程但不可完整覆蓋靜態元件，且容易混淆副本改動 |
| E | 只做動態 hook／Frida／封包錄製 | 5.9/10 | 可補 runtime 證據但不能單獨形成完整前端與程式索引 |

採用 A。手機不是拆包／反編譯的必要條件；只有需要官方 runtime golden vector、真實 BLE 封包或登入／權限行為時才是額外驗證來源。

## 階段與 checkpoint

- Phase A：輸入固定與完整 APK／split／hash／Manifest 清冊。
- Phase B：資源／前端 asset／locale／Dex/Smali／plugin inventory。
- Phase C：Flutter AOT metadata、字串、函式、物件、call graph、ARM64 listing inventory。
- Phase D：以入口群組完成前端路由與資料流分析（啟動／登入、裝置／BLE、校正、動作、歷史、設定、通知／錄影、雲端）。
- Phase E：建立證據矩陣、未知／風險與後續 golden-vector 需求；跑驗證並提交推送。

每個階段都只在輸出已寫入專案、檢查可讀且驗證命令成功後 commit/push。原始 HCI／個資不放入 Git；只提交去識別化摘要與雜湊。
