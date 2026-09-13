# Goal 2 VBT 中間量補強（2026-09-14）

依 AOT `JsVbtCalculator._parseRep@1345406029_2dbcf0` 可直接辨識的欄位，副本的 `VendorMotionEngine` 現在會對每個 VBT rep 區段計算並匯出平均／峰值：

- `rangeOfMotion`、`velocityLoss`
- `eccentricDuration`、`eccentricMeanVelocity`、`eccentricMaxVelocity`、`eccentricRom`
- `tempoRatio`、`timeToPeakVelocity`、`relativeTimeToPeak`
- `rfdPerMass`、`decelerationRate`、`eccentricConcentricRatio`
- `meanPowerPerMass`、`maxPowerPerMass`

區段由自由分析的逐點線性速度峰值與 0.08 m/s 零速門檻建立；在尚未完成官方 golden vector 前，這些欄位標示為 `vendor-compatible-reconstruction-2026.09`，不宣稱與原廠 AOT 逐位元相同。`official-clone/test-vendor-engine.ps1` 的 VBT／1RM 向量已通過。
