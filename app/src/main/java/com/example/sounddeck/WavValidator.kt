package com.example.sounddeck

import java.io.File
import java.io.RandomAccessFile

data class WavInfo(val durationSeconds: Double, val dataBytes: Int)
object WavValidator {
    fun validate(file: File): Result<WavInfo> = runCatching {
        require(file.exists() && file.length() >= 12) { "File is too small" }
        RandomAccessFile(file, "r").use { wav ->
            require(readFourCC(wav) == "RIFF") { "Expected RIFF/WAVE" }
            val riffSize = readUInt32(wav)
            require(readFourCC(wav) == "WAVE") { "Expected RIFF/WAVE" }
            require(riffSize + 8 <= file.length()) { "Truncated RIFF file" }
            var sampleRate = 0L
            var channels = 0
            var bits = 0
            var dataBytes = -1L
            while (wav.filePointer + 8 <= file.length()) {
                val id = readFourCC(wav)
                val size = readUInt32(wav)
                val chunkStart = wav.filePointer
                require(size <= file.length() - chunkStart) { "Truncated WAV chunk" }
                when (id) {
                    "fmt " -> {
                        require(size >= 16) { "Invalid fmt chunk" }
                        require(readUInt16(wav) == 1) { "Only PCM WAV is supported" }
                        channels = readUInt16(wav)
                        sampleRate = readUInt32(wav)
                        readUInt32(wav) // byte rate
                        require(readUInt16(wav) == 2) { "Invalid 16-bit mono alignment" }
                        bits = readUInt16(wav)
                    }
                    "data" -> if (dataBytes < 0) dataBytes = size
                }
                wav.seek(chunkStart + size + (size and 1L))
            }
            require(sampleRate > 0) { "Missing or invalid fmt chunk" }
            require(channels == 1) { "WAV must be mono" }
            require(bits == 16) { "WAV must be 16-bit" }
            require(dataBytes >= 0 && dataBytes <= Int.MAX_VALUE) { "Missing or oversized data chunk" }
            val maxBytes = 30L * sampleRate * 2
            require(dataBytes <= maxBytes) { "Audio must be 30 seconds or less" }
            WavInfo(dataBytes.toDouble() / (sampleRate * 2), dataBytes.toInt())
        }
    }

    private fun readFourCC(file: RandomAccessFile): String {
        val bytes = ByteArray(4); file.readFully(bytes); return String(bytes, Charsets.US_ASCII)
    }
    private fun readUInt16(file: RandomAccessFile): Int =
        file.readUnsignedByte() or (file.readUnsignedByte() shl 8)
    private fun readUInt32(file: RandomAccessFile): Long =
        readUInt16(file).toLong() or (readUInt16(file).toLong() shl 16)
}
