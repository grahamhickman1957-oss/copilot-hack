package com.example.sounddeck

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { SoundDeckApp() } }
    @Composable private fun SoundDeckApp() {
        val store = remember { SampleStore(this) }; var samples by remember { mutableStateOf(store.load()) }
        var selected by remember { mutableStateOf<Int?>(null) }; var message by remember { mutableStateOf("Ready") }
        var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }; var scanning by remember { mutableStateOf(false) }
        val ble = remember { BleClient(this, object : BleClient.Listener { override fun status(v: String) { message = v }; override fun progress(v: Int) { message = "Transfer $v%" } }) }
        val scanner = remember { BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner }
        val currentDevices by rememberUpdatedState(devices)
        val scanCallback = remember {
            object : ScanCallback() {
                override fun onScanResult(type: Int, result: ScanResult) {
                    runOnUiThread {
                        if (!currentDevices.any { it.address == result.device.address }) {
                            devices = currentDevices + result.device
                        }
                    }
                }
                override fun onScanFailed(errorCode: Int) {
                    runOnUiThread { scanning = false; message = "BLE scan failed ($errorCode)" }
                }
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            val slot = selected ?: return@rememberLauncherForActivityResult
            val file = File(filesDir, "slot_$slot.wav")
            contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
            WavValidator.validate(file).onSuccess { info ->
                samples = samples.map { if (it.slot == slot) it.copy(name = it.name.ifBlank { "Sample ${slot + 1}" }, path = file.absolutePath) else it }; store.save(samples)
                message = "Imported ${"%.1f".format(info.durationSeconds)}s"
            }.onFailure { file.delete(); message = it.message ?: "Invalid WAV" }
        }
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                scanner?.startScan(scanCallback)
                scanning = true
            } else {
                message = "Bluetooth permission is required to scan"
            }
        }
        Scaffold(topBar = { TopAppBar(title = { Text("SoundDeck • 50 slots") }, actions = {
            TextButton(onClick = { ble.disconnect() }) { Text("Disconnect") }
        }) }) { pad ->
            Column(Modifier.padding(pad).padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (scanning) {
                            scanner?.stopScan(scanCallback)
                            scanning = false
                        } else {
                            permissions.launch(
                                if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            )
                        }
                    }) { Text(if (scanning) "Stop scan" else "Scan BLE") }
                }
                if (devices.isNotEmpty()) {
                    Text("Devices", style = MaterialTheme.typography.titleMedium)
                    devices.forEach { d -> TextButton(onClick = { ble.connect(d) }) { Text("${d.name ?: "Unnamed"} (${d.address})") } }
                }
                Text(message, Modifier.padding(vertical = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(samples) { sample ->
                        var name by remember(sample.slot, sample.name) { mutableStateOf(sample.name) }
                        Card { Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${sample.slot + 1}", Modifier.width(28.dp))
                            OutlinedTextField(name, { name = it; samples = samples.map { s -> if (s.slot == sample.slot) s.copy(name = it) else s }; store.save(samples) }, Modifier.weight(1f), label = { Text(if (sample.configured) "Name" else "Empty") }, singleLine = true)
                            Button(onClick = { selected = sample.slot; picker.launch(arrayOf("audio/wav", "audio/x-wav")) }) { Text(if (sample.configured) "Replace" else "Import") }
                            if (sample.configured) TextButton(onClick = { ble.send(sample.slot, File(sample.path!!).readBytes()) }) { Text("Send") }
                            TextButton(onClick = { samples = samples.map { if (it.slot == sample.slot) Sample(it.slot) else it }; File(sample.path ?: "").delete(); store.save(samples) }) { Text("Clear") }
                        } }
                    }
                }
            }
        }
    }
}
