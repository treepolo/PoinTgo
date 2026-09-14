# AOT raw-IMU parser reverse-engineering (in progress)

Source: `analysis/apk_protocol/unpacked_arm64/lib/arm64-v8a/libapp.so`, disassembled with unflutter.  Relevant recovered functions:

- `BleMocapDataParser.parseReceivedPacket`, pc `0x83fdac`
- `BleMocapDataParser._parseRawImuBatch`, pc `0x83ffbc`, 3228 bytes
- `RawImuBatch.fromParsed`, pc `0x8ffd94`
- `CommandMeasureRawImuHighRate.command`, pc `0xdf981c`

## Confirmed parser contract

`parseReceivedPacket` reads the first byte of the packet as a packet type.  Type `0x04` dispatches to `_parseRawImuBatch`.  The raw parser then casts the packet to a byte-list view and reads the view's visible data.

In `_parseRawImuBatch`:

```text
length = packet.length
sampleCount = u8(packet[1])
assert length == sampleCount * 12 + 8
```

Each sample has a fixed 12-byte record, little-endian signed 16-bit values:

```text
recordBase = 8 + sampleIndex * 12
field0 = i16le(recordBase + 0)
field1 = i16le(recordBase + 2)
field2 = i16le(recordBase + 4)
field3 = i16le(recordBase + 6)
field4 = i16le(recordBase + 8)
field5 = i16le(recordBase + 10)
```

The first four fields are multiplied by one pool double (`PP[8212]`, D2), and the last two by another (`PP[8213]`, D1).  A per-sample time value is generated as `(base + index * step) / divisor`; the relevant registers are loaded around `0x840530` and the timestamp expression is around `0x8408a4`.

The parser reads header bytes at visible offsets 1, 2, 3 and a 32-bit little-endian value at offset 4.  Byte 3 is used as the key for `_rawImuTimestampWrapCountMap`.  Header offset 4 is converted to a non-negative 32-bit value and is the base used by the timestamp loop.

## Capture discrepancy to resolve

The captured NUS value is `0x04` + 60 bytes (`analysis/hci/poinT_att.txt`, 21,177 packets), but byte 1 in the first value is `0x9e`; a direct application of the above contract would require `61 == 0x9e*12+8`, which is false.  The HCI ACL/ATT decode confirms the `0x04` is the ATT value's first byte (ACL payload starts `02 41 20 ... 1b 14 00 04 ...`).  Thus the firmware stream in this capture appears to use a different/outer framing (or a firmware/app protocol mismatch) than the exact raw-batch format this APK parser expects.  Do not treat the 61-byte values as directly valid `_parseRawImuBatch` inputs until that framing is identified.

## Command status

`CommandMeasureRawImuHighRate.command` is a Dart command-object constructor/serializer stub; it loads command map/enum pool objects (`PP[27651..27664]`) and does not contain literal BLE bytes in its body.  Need trace its command serialization/callers to identify the wire command.  The capture's last pre-stream writes are `05 01 04`, `03 03`, `03 00`, `05 01 06`, `05 01 04`, `03 03`; stream starts after `03 03` at ATT notification `0c 01 0d 00`.
