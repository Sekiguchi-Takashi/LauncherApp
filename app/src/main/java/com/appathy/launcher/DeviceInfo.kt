package com.appathy.launcher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.InputDevice
import java.net.ServerSocket

data class PeripheralState(
    val keyboard: Boolean,
    val mouse: Boolean,
    val monitor: Boolean,
    val speaker: Boolean,
    val microphone: Boolean
)

object DeviceInfo {

    const val SERVICE_TYPE = "_appathylnch._tcp."

    fun deviceName(context: Context): String {
        val stored = runCatching {
            Settings.Global.getString(context.contentResolver, "device_name")
        }.getOrNull()
        if (!stored.isNullOrBlank()) return stored
        return Build.MODEL ?: "Android"
    }

    fun wifiEnabled(context: Context): Boolean? = runCatching {
        context.getSystemService(WifiManager::class.java)?.isWifiEnabled
    }.getOrNull()

    fun bluetoothEnabled(context: Context): Boolean? = runCatching {
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter: BluetoothAdapter? = manager?.adapter
        adapter?.isEnabled
    }.getOrNull()

    fun peripherals(context: Context): PeripheralState {
        var keyboard = false
        var mouse = false
        runCatching {
            InputDevice.getDeviceIds().forEach { id ->
                val device = InputDevice.getDevice(id) ?: return@forEach
                if (device.isVirtual) return@forEach
                val sources = device.sources
                if (device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                    sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
                ) {
                    keyboard = true
                }
                if (sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
                    sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD
                ) {
                    mouse = true
                }
            }
        }

        val monitor = runCatching {
            val manager = context.getSystemService(DisplayManager::class.java)
            manager?.displays?.any { it.displayId != Display.DEFAULT_DISPLAY } ?: false
        }.getOrDefault(false)

        var speaker = false
        var microphone = false
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val outputs = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            speaker = outputs.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_HDMI
            }
            val inputs = audio?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
            microphone = inputs.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        }

        return PeripheralState(keyboard, mouse, monitor, speaker, microphone)
    }
}

class PeerFinder(private val context: Context) {

    private var nsd: NsdManager? = null
    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredName: String = ""

    fun start(onPeers: (List<String>) -> Unit) {
        val found = linkedSetOf<String>()
        val manager = runCatching {
            context.getSystemService(NsdManager::class.java)
        }.getOrNull() ?: return
        nsd = manager

        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return
        serverSocket = socket

        val info = NsdServiceInfo().apply {
            serviceName = DeviceInfo.deviceName(context)
            serviceType = DeviceInfo.SERVICE_TYPE
            port = socket.localPort
        }

        val reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredName = info.serviceName ?: ""
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        registrationListener = reg
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, reg) }

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                val name = service.serviceName ?: return
                if (name == registeredName) return
                found.add(name)
                onPeers(found.toList())
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val name = service.serviceName ?: return
                found.remove(name)
                onPeers(found.toList())
            }

            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
        }
        discoveryListener = discovery
        runCatching {
            manager.discoverServices(
                DeviceInfo.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discovery
            )
        }
    }

    fun stop() {
        val manager = nsd ?: return
        registrationListener?.let { runCatching { manager.unregisterService(it) } }
        discoveryListener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        runCatching { serverSocket?.close() }
        registrationListener = null
        discoveryListener = null
        serverSocket = null
    }
}
