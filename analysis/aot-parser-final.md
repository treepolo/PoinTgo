# Poin+T Go AOT raw-IMU parser findings

This note records what is directly visible in the arm64 Flutter AOT code. It does not send commands to the phone.

## Parser entry and packet contract

`BleMocapDataParser.parseReceivedPacket` reads the first visible byte as the packet type. Type `0x04` dispatches to `_parseRawImuBatch`.

`_parseRawImuBatch` reads:

```text
packet[0]       = 0x04 (type)
packet[1]       = u8 sampleCount (N)
packet[2]       = header byte / mode field (retained in parser state)
packet[3]       = per-device key used by the timestamp-wrap map
packet[4..7]    = little-endian u32 base timestamp/counter
packet[8..]     = N records, 12 bytes each
```

The code checks the typed-data length against exactly `8 + 12*N`. A record at `8 + 12*i` consists of six signed little-endian 16-bit values:

```text
record + 0  i16  value 0
record + 2  i16  value 1
record + 4  i16  value 2
record + 6  i16  value 3
record + 8  i16  value 4
record +10  i16  value 5
```

The output loop multiplies values 0..3 by one scale and values 4..5 by another. It then emits a timestamp for each sample as `(base + i*step) / 1,000,000`.

## Scales and timestamp

The unflutter disassembly's pool labels are physically off by two slots: `objects --json` reports logical object-pool index `k` at runtime offset `(k+2)*8`, while the printed `PP[...]` annotation uses the raw slot number. After correcting this offset, the constants used by `_parseRawImuBatch` are:

```text
D2 = 0x3f739d013a92a305 = 0.0047884033203125
D1 = 0x3faf400000000000 = 0.06103515625
D0 = 0x412e848000000000 = 1,000,000.0
```

`D2` is numerically `9.80665 * 16 / 32768`, consistent with a ±16 g accelerometer converted to m/s². `D1` is `2000 / 32768`, consistent with a ±2000 dps gyroscope converted to degrees/s. The parser applies D2 to four consecutive wire fields and D1 to the final two; therefore the fourth D2 field must not be silently relabeled as a conventional gyro axis without confirming the device firmware's exact schema.

The timestamp divisor is unambiguous: the final floating-point divide uses `1,000,000`, so the base/step counter is in microseconds (or an equivalent 1 MHz tick), and output seconds are `(base + i*step)/1e6`.

## Output-key evidence

The parser's object-pool strings, after correcting the same two-slot annotation shift, include `seq`, `sampleRateHz`, `timeStamp`, `ax`, `ay`, `az`, `gx`, `gy`, and `gz`. The map-building stores the per-sample arrays under those names. This establishes the intended semantic keys, but not yet a safe one-to-one mapping for all six wire fields because the first four fields share D2 in the current code. `RawImuBatch.fromParsed` is the next place to resolve any wrapper/field-order ambiguity.

## Sample-rate handling

The parser uses a `step` value in the timestamp expression (`base + i*step`) and emits `sampleRateHz` in the parsed structure. The parser assembly alone does not prove the numeric step constant or whether it is derived from a negotiated mode. It does prove that timestamps are generated per sample rather than all samples sharing one packet timestamp.

## HCI capture mismatch

The filtered snoop capture (`analysis/hci/poinT_att.txt`) has about 21k Poin+T notifications whose ATT value is `0x04` plus 60 bytes. In those notifications byte 1 is often values such as `0x9e` or `0xde`, which cannot be a valid `N` for the AOT contract (`8 + 12*N` would exceed 60 immediately). Therefore the captured 61-byte ATT values are not directly the `_parseRawImuBatch` input. Likely possibilities are an outer/inner framing layer, a different firmware high-rate mode, or a capture/parser version mismatch. Do not decode the 60-byte notification as a single six-field batch until that framing is located.

## Command status

`CommandMeasureRawImuHighRate.command` is a Dart object-construction method. It loads constant-map/instance-pool objects and passes an integer argument `4` into the command-building path; it does not contain literal BLE bytes. The exact wire command must be recovered by tracing the command serializer and the caller that writes the resulting byte list to the NUS characteristic. The short writes in the capture (`03 03`, `05 01 04`, etc.) are not assigned to this command without that trace.

## Evidence files

```text
analysis/apk_protocol/unpacked_arm64/lib/arm64-v8a/libapp.so
analysis/aot_unflutter/asm/BleMocapDataParser/parseReceivedPacket_1b946c.txt
analysis/aot_unflutter/asm/BleMocapDataParser/_parseRawImuBatch@859188570_1b967c.txt
analysis/aot_unflutter/asm/CommandMeasureRawImuHighRate/command_772edc.txt
analysis/hci/poinT_att.txt
```
