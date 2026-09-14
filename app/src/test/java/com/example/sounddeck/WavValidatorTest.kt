package com.example.sounddeck

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavValidatorTest {
    private fun wav(channels: Short = 1, bits: Short = 16, bytes: Int = 16000, extraChunk: Boolean = false): File {
        val f = File.createTempFile("wav-test", ".wav")
        val h = ByteBuffer.allocate(if (extraChunk) 52 else 44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt((if (extraChunk) 44 else 36) + bytes); put("WAVE".toByteArray()); put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels); putInt(8000); putInt(16000); putShort(2); putShort(bits)
            if (extraChunk) { put("JUNK".toByteArray()); putInt(4); putInt(0) }
            put("data".toByteArray()); putInt(bytes)
        }.array(); f.writeBytes(h + ByteArray(bytes)); f.deleteOnExit(); return f
    }
    @Test fun acceptsMonoPcm16() { assertTrue(WavValidator.validate(wav()).isSuccess) }
    @Test fun rejectsStereo() { assertTrue(WavValidator.validate(wav(2)).isFailure) }
    @Test fun rejectsNon16Bit() { assertTrue(WavValidator.validate(wav(bits = 8)).isFailure) }
    @Test fun acceptsChunksBeforeData() { assertTrue(WavValidator.validate(wav(extraChunk = true)).isSuccess) }
    @Test fun rejectsMoreThanThirtySeconds() { assertTrue(WavValidator.validate(wav(bytes = 8000 * 2 * 30 + 2)).isFailure) }
}
