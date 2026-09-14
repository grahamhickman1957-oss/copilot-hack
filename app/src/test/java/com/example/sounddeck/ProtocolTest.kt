package com.example.sounddeck

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test fun dataFrameHasOpcodeSequenceAndPayload() {
        assertArrayEquals(byteArrayOf(2, 7, 0, 4, 5), Protocol.data(7, byteArrayOf(4, 5)))
    }
    @Test fun beginFrameIsLittleEndian() {
        val b = Protocol.begin(3, 0x01020304, 0x05060708)
        assertArrayEquals(byteArrayOf(1, 3, 4, 3, 2, 1, 8, 7, 6, 5), b)
    }

    @Test fun dataFrameFitsGattWriteLimit() {
        assertTrue(Protocol.data(0, ByteArray(Protocol.MAX_CHUNK)).size <= Protocol.MAX_WRITE)
    }
}
