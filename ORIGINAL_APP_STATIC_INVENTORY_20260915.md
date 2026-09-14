# Poin*T Go 原廠靜態拆包／反編譯／反組譯清冊

生成日期：2026-09-15。此清冊只讀專案內保存的 APK 與分析輸出，不需要手機。

## 輸入 APK

| 檔案 | bytes | SHA-256 | ZIP entries | 分類數 |
| --- | ---: | --- | ---: | --- |
| `analysis\official_clone\original\base.apk` | 73,228,712 | `94d764aa9234f2687ea4c129a4a3645c15b968e191229aeb4466e1c14c7f7a10` | 525 | `{"android_resource": 152, "dex": 3, "flutter_asset": 158, "other": 126, "signature_or_metadata": 86}` |
| `analysis\official_clone\original\split_config.arm64_v8a.apk` | 48,665,828 | `7e8f0cde58813901855c7df9162d9f76ee80a936fd6fedc0b786fee959d33dfd` | 16 | `{"native_lib": 11, "other": 2, "signature_or_metadata": 3}` |
| `analysis\official_clone\original\split_config.en.apk` | 45,465 | `3ecafec87302cbf0afa6874b7b5ec075230d4d56f9716374e9e9e363b5a98545` | 6 | `{"other": 3, "signature_or_metadata": 3}` |
| `analysis\official_clone\original\split_config.xxhdpi.apk` | 100,918 | `2665f4bc590318ab3da7ef125f91654aed3964e707601de94a56b24939047cd8` | 66 | `{"android_resource": 60, "other": 3, "signature_or_metadata": 3}` |
| `analysis\official_clone\original\split_config.zh.apk` | 33,177 | `40b44b7669cc591958cf31c8cabbf48b31fea354523a72192e69793bd3ca5b7d` | 6 | `{"other": 3, "signature_or_metadata": 3}` |

## Manifest／Dex／資源

- package：`kr.piehealthcare.point.sensor`；application：`com.pairip.application.Application`；manifest 182 行、133 個 name/component；權限 33 個。
- 解包 base：15,943 檔、198,148,620 bytes；副檔名：`{".a": 1, ".b": 1, ".bin": 4, ".css": 1, ".dev": 1, ".frag": 2, ".js": 5, ".json": 4, ".jsonfactory": 1, ".kotlin_builtins": 8, ".l0": 1, ".md": 1, ".mf": 1, ".mp3": 15, ".objectcodec": 1, ".otf": 7, ".png": 114, ".product": 1, ".prof": 1, ".profm": 1, ".properties": 31, ".proto": 77, ".s": 1, ".smali": 15400, ".svg": 12, ".task": 1, ".textproto": 1, ".tflite": 1, ".ttf": 1, ".txt": 3, ".u": 1, ".version": 73, ".xml": 166, ".y": 1, ".yml": 1, ".z": 1, "<none>": 1}`。
- Smali：`{"smali": {"files": 10662, "bytes": 90974239, "packages": 454}, "smali_classes2": {"files": 134, "bytes": 1474552, "packages": 6}, "smali_classes3": {"files": 4604, "bytes": 41984618, "packages": 245}, "native_dex": {"files": 1, "bytes": 131456}}`。

## Flutter 前端資源

- `assets/flutter_assets`：158 檔、47,013,076 bytes；第一層分類：`{".env.dev": 1, ".env.product": 1, "AssetManifest.bin": 1, "FontManifest.json": 1, "NOTICES.Z": 1, "NativeAssetsManifest.json": 1, "assets": 137, "fonts": 1, "packages": 12, "shaders": 2}`。
- 代表性資源包括 `assets/img/device`、`assets/img/exercise`、`assets/img/placement`、`assets/icon/features`、音效、WantedSans 字型、JS bridge、TFLite／pose model 與 shader；完整清單在 JSON。

## Flutter AOT／ARM64

- `classes.jsonl`：6,696 lines、2,316,429 bytes。
- `functions.jsonl`：46,790 lines、4,658,663 bytes。
- `index.jsonl`：46,790 lines、8,379,222 bytes。
- `string_refs.jsonl`：42,304 lines、7,792,098 bytes。
- `call_edges.jsonl`：214,034 lines、25,872,651 bytes。
- `objects.jsonl`：59,235 lines、6,604,238 bytes。
- `edges.jsonl`：153,557 lines、7,909,147 bytes。
- `code_map.jsonl`：45,521 lines、4,851,390 bytes。
- `asm_txt`：9,305 files、18,309,399 bytes。
- `string_file`：79,986 lines、1,346,919 bytes。
- `libapp.so`：16,122,800 bytes，SHA-256 `7ea3d30692b31cf8a2346b422fbe1407663b5bd86693738790fbfa7392fb97b4`。
- 重要入口命中與字串 keyword 計數詳見 `analysis/original_app_inventory.json`；每個結論仍需以 AOT listing／物件池／呼叫邊原文追溯。

## 證據邊界

- APK／資源／Smali／AOT bytes 是直接證據；由 symbol/字串聚類推導的前端頁面或算法意義是交叉推論。
- Flutter release AOT 不含原始 Dart 變數名、註解與完整型別語義；因此本清冊證明拆包與反組譯輸出完整，不把尚未有 runtime golden vector 的公式宣稱為官方原式。
- 原始 APK 及此清冊不含手機必要步驟；手機只在後續要驗證官方 runtime 輸出、權限、登入或真實 BLE 行為時使用。
