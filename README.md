# SoundDeck

SoundDeck is a small Jetpack Compose Android controller for an ESP32 sound board. It has **exactly 50** persistent, user-definable sample slots. Samples are copied into app-private storage and validated as mono, 16-bit PCM WAV files no longer than 30 seconds.

## Build

Open the repository in Android Studio (JDK 17). The project does not currently check in a Gradle wrapper, so from a machine with Gradle installed use `gradle test` and `gradle assembleDebug` (or use Android Studio's Gradle actions). Android SDK 35 is used; Gradle will download normal AndroidX/Compose dependencies.

## ESP32 BLE protocol

The app uses Nordic UART-style UUIDs:

* service `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
* control/write `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
* audio/write `6e400003-b5a3-f393-e0a9-e50e24dcca9e`
* acknowledgement/notify `6e400004-b5a3-f393-e0a9-e50e24dcca9e`

Writes use a 180-byte GATT payload limit; DATA payloads are 177 bytes because each frame has a 3-byte opcode/sequence header. A transfer sends a little-endian `BEGIN` frame (`01, slot, uint32 byteLength, uint32 CRC32`), then `DATA` frames (`02, uint16 sequence, payload`) and an `END` (`03`). For each DATA frame the ESP32 must notify an acknowledgement containing the little-endian uint16 sequence (optionally followed by a status byte, zero for success). The app waits two seconds and retries each chunk up to three times, reporting failure if no acknowledgement arrives. The UI reports transfer progress and the protocol constants are centralized in `Protocol.kt`.
