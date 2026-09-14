package com.example.sounddeck

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ESP32 GATT protocol. Service and characteristics are intentionally stable/documented in README. */
object Protocol {
    const val SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val CONTROL_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e" // write
    const val AUDIO_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e" // write
    const val ACK_UUID = "6e400004-b5a3-f393-e0a9-e50e24dcca9e" // notify
    const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    const val OP_BEGIN: Byte = 1; const val OP_DATA: Byte = 2; const val OP_END: Byte = 3
    const val MAX_WRITE = 180
    const val DATA_HEADER_SIZE = 3
    const val MAX_CHUNK = MAX_WRITE - DATA_HEADER_SIZE
    fun begin(slot: Int, size: Int, crc32: Long): ByteArray = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        .put(OP_BEGIN).put(slot.toByte()).putInt(size).putInt(crc32.toInt()).array()
    fun data(sequence: Int, payload: ByteArray): ByteArray = ByteBuffer.allocate(3 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        .put(OP_DATA).putShort(sequence.toShort()).put(payload).array()
    fun end(): ByteArray = byteArrayOf(OP_END)
}
