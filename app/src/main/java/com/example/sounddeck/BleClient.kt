package com.example.sounddeck

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.bluetooth.BluetoothGattDescriptor
import android.os.Handler
import android.os.Looper

class BleClient(private val context: Context, private val listener: Listener) {
    interface Listener { fun status(value: String); fun progress(value: Int) }
    private var gatt: BluetoothGatt? = null
    @Volatile private var acknowledgements = mutableMapOf<Int, CountDownLatch>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun status(value: String) = mainHandler.post { listener.status(value) }
    private fun progress(value: Int) = mainHandler.post { listener.progress(value.coerceIn(0, 100)) }
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) { status("Connecting…"); gatt = device.connectGatt(context, false, callback) }
    @SuppressLint("MissingPermission") fun disconnect() { gatt?.disconnect(); gatt?.close(); gatt = null; status("Disconnected") }
    @SuppressLint("MissingPermission")
    fun send(slot: Int, bytes: ByteArray) {
        val service = gatt?.getService(UUID.fromString(Protocol.SERVICE_UUID)) ?: return status("Device protocol not found")
        val control = service.getCharacteristic(UUID.fromString(Protocol.CONTROL_UUID))
        val audio = service.getCharacteristic(UUID.fromString(Protocol.AUDIO_UUID))
        Thread {
            fun write(c: BluetoothGattCharacteristic, data: ByteArray) { c.value = data; c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; gatt?.writeCharacteristic(c) }
            write(control, Protocol.begin(slot, bytes.size, java.util.zip.CRC32().apply { update(bytes) }.value))
            bytes.asSequence().chunked(Protocol.MAX_CHUNK).forEachIndexed { n, chunk ->
                val frame = Protocol.data(n, chunk.toByteArray())
                var delivered = false
                repeat(3) {
                    if (!delivered) {
                        val latch = CountDownLatch(1); acknowledgements = mutableMapOf(n to latch)
                        write(audio, frame); delivered = latch.await(2, TimeUnit.SECONDS)
                    }
                }
                if (!delivered) { status("Transfer failed at chunk $n"); return@Thread }
                progress(((n + 1L) * Protocol.MAX_CHUNK * 100 / bytes.size.coerceAtLeast(1)).toInt())
            }
            write(control, Protocol.end()); progress(100)
        }.start()
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) { this@BleClient.status(if (state == BluetoothProfile.STATE_CONNECTED) "Connected" else "Disconnected"); if (state == BluetoothProfile.STATE_CONNECTED) g.discoverServices() }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ack = g.getService(UUID.fromString(Protocol.SERVICE_UUID))?.getCharacteristic(UUID.fromString(Protocol.ACK_UUID))
            if (ack != null) {
                g.setCharacteristicNotification(ack, true)
                ack.getDescriptor(UUID.fromString(Protocol.CCCD_UUID))?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(it)
                }
            }
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == UUID.fromString(Protocol.ACK_UUID) && characteristic.value.size >= 2) {
                val sequence = (characteristic.value[0].toInt() and 255) or ((characteristic.value[1].toInt() and 255) shl 8)
                acknowledgements[sequence]?.countDown()
            }
        }
    }
}
