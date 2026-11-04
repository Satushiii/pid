package com.banumusateam.digitalpid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class UsbSerialController(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.banumusateam.digitalpid.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        openPort(currentBaudRate)
                    } else {
                        _connectionStatus.value = "USB Permission Denied"
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                disconnect()
            }
        }
    }

    private var currentBaudRate: Int = 115200

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun connect(baudRate: Int = 115200) {
        currentBaudRate = baudRate
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            _connectionStatus.value = "No Arduino/STM32 Found"
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        if (!usbManager.hasPermission(device)) {
            _connectionStatus.value = "Requesting USB Permission..."
            // FIXED: Changed MutableStateFlow to S (Android 12 / API 31)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else 0
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, permissionIntent)
            return
        }

        openPort(baudRate)
    }

    private fun openPort(baudRate: Int) {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return

        val driver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            _connectionStatus.value = "Failed to open USB Device"
            return
        }

        try {
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // Required for STM32 Native USB CDC & Arduino Leonardo/Micro to start streaming:
            port.dtr = true
            port.rts = true

            serialPort = port
            _isConnected.value = true
            _connectionStatus.value = "Connected: ${driver.device.productName ?: "USB Serial"} @ $baudRate"
        } catch (e: IOException) {
            _connectionStatus.value = "Error: ${e.message}"
            disconnect()
        }
    }

    /**
     * Sends the 3 computed Torque outputs to Arduino/STM32 in a clean packet:
     * Format: "<pitchTorque,rollTorque,yawTorque>\n"
     */
    fun sendTorques(pitchTorque: Double, rollTorque: Double, yawTorque: Double) {
        val port = serialPort ?: return
        if (!_isConnected.value) return

        val packet = "<${"%.2f".format(pitchTorque)},${"%.2f".format(rollTorque)},${"%.2f".format(yawTorque)}>\n"
        try {
            port.write(packet.toByteArray(Charsets.UTF_8), 10)
        } catch (e: IOException) {
            disconnect()
        }
    }

    fun disconnect() {
        try {
            serialPort?.close()
        } catch (ignored: IOException) {}
        serialPort = null
        _isConnected.value = false
        _connectionStatus.value = "Disconnected"
    }

    fun unregister() {
        disconnect()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (ignored: Exception) {}
    }
}
