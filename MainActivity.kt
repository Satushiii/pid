package com.banumusateam.digitalpid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var flightController: QuadcopterFlightController? = null
    private var crashLog: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // CRASH CATCHER: Attempts to start the controller, captures the exact error if it fails
        try {
            flightController = QuadcopterFlightController(this)
            flightController?.startController()
        } catch (e: Exception) {
            crashLog = e.stackTraceToString()
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (crashLog != null) {
                        // CRASH SCREEN - Displays the exact logcat error on the phone
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text("APP CRASHED!", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(crashLog!!, color = Color.DarkGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    } else {
                        // NORMAL SCREEN
                        flightController?.let { UnifiedQuadcopterScreen(it) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        flightController?.stopAll()
    }
}

// =========================================================================
// KEEP YOUR EXISTING UI COMPOSABLES BELOW THIS LINE
// =========================================================================

@Composable
fun UnifiedQuadcopterScreen(fc: QuadcopterFlightController) {
    val telemetry by fc.telemetry.collectAsState()
    val usbStatus by fc.usbController.connectionStatus.collectAsState()
    val isUsbConnected by fc.usbController.isConnected.collectAsState()
    var baudRateInput by remember { mutableStateOf("115200") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("USB Serial Connection (Arduino / STM32)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Status: $usbStatus", color = if (isUsbConnected) Color(0xFF2E7D32) else Color.Gray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = baudRateInput,
                        onValueChange = { baudRateInput = it },
                        label = { Text("Baud Rate") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (isUsbConnected) {
                                fc.usbController.disconnect()
                            } else {
                                fc.usbController.connect(baudRateInput.toIntOrNull() ?: 115200)
                            }
                        }
                    ) {
                        Text(if (isUsbConnected) "Disconnect USB" else "Connect USB")
                    }
                }
            }
        }

        AxisControlSection("SECTION 1: PITCH AXIS CONTROL", fc.pitchController, telemetry.pitch)
        AxisControlSection("SECTION 2: ROLL AXIS CONTROL", fc.rollController, telemetry.roll)
        AxisControlSection("SECTION 3: YAW AXIS CONTROL", fc.yawController, telemetry.yaw)
    }
}

@Composable
fun AxisControlSection(title: String, axis: CascadeAxisController, telemetry: AxisTelemetry) {
    var desiredAngle by remember(axis) { mutableStateOf(axis.desiredAngle.toString()) }
    var kpInner by remember(axis) { mutableStateOf(axis.anglePID.kp.toString()) }
    var kiInner by remember(axis) { mutableStateOf(axis.anglePID.ki.toString()) }
    var kdInner by remember(axis) { mutableStateOf(axis.anglePID.kd.toString()) }
    var dtInner by remember(axis) { mutableStateOf(axis.sampleTimeAngleMs.toString()) }
    var minSatInner by remember(axis) { mutableStateOf(axis.anglePID.minOutput.toString()) }
    var maxSatInner by remember(axis) { mutableStateOf(axis.anglePID.maxOutput.toString()) }
    var enableInner by remember(axis) { mutableStateOf(axis.enableAngle) }

    var kpOuter by remember(axis) { mutableStateOf(axis.ratePID.kp.toString()) }
    var kiOuter by remember(axis) { mutableStateOf(axis.ratePID.ki.toString()) }
    var kdOuter by remember(axis) { mutableStateOf(axis.ratePID.kd.toString()) }
    var dtOuter by remember(axis) { mutableStateOf(axis.sampleTimeRateMs.toString()) }
    var minSatOuter by remember(axis) { mutableStateOf(axis.ratePID.minOutput.toString()) }
    var maxSatOuter by remember(axis) { mutableStateOf(axis.ratePID.maxOutput.toString()) }
    var enableOuter by remember(axis) { mutableStateOf(axis.enableRate) }

    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelemetryWindow("ANGLE (deg)", "%.2f°".format(telemetry.measuredAngle), "Err: ${"%.2f".format(telemetry.angleError)}°", Modifier.weight(1f))
                TelemetryWindow("ANGULAR SPEED (deg/s)", "%.2f °/s".format(telemetry.measuredRate), "Torque: ${"%.2f".format(telemetry.torqueOutput)}", Modifier.weight(1f))
            }

            OutlinedTextField(desiredAngle, { desiredAngle = it }, label = { Text("Desired Angle Setpoint (deg)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            HorizontalDivider()

            Text("Inner PID (Angle Control)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(kpInner, { kpInner = it }, label = { Text("Kp") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(kiInner, { kiInner = it }, label = { Text("Ki") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(kdInner, { kdInner = it }, label = { Text("Kd") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(minSatInner, { minSatInner = it }, label = { Text("Min Sat") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(maxSatInner, { maxSatInner = it }, label = { Text("Max Sat") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(dtInner, { dtInner = it }, label = { Text("Ts (ms)") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            HorizontalDivider()

            Text("Outer PID (Angular Rate -> Torque)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(kpOuter, { kpOuter = it }, label = { Text("Kp Rate") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(kiOuter, { kiOuter = it }, label = { Text("Ki Rate") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(kdOuter, { kdOuter = it }, label = { Text("Kd Rate") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(minSatOuter, { minSatOuter = it }, label = { Text("Min Torque") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(maxSatOuter, { maxSatOuter = it }, label = { Text("Max Torque") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(dtOuter, { dtOuter = it }, label = { Text("Ts (ms)") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableInner, onCheckedChange = { enableInner = it })
                    Text("Inner PID", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableOuter, onCheckedChange = { enableOuter = it })
                    Text("Outer PID", fontSize = 13.sp)
                }
                Button(onClick = {
                    axis.desiredAngle = desiredAngle.toDoubleOrNull() ?: 0.0
                    axis.anglePID.kp = kpInner.toDoubleOrNull() ?: 0.0
                    axis.anglePID.ki = kiInner.toDoubleOrNull() ?: 0.0
                    axis.anglePID.kd = kdInner.toDoubleOrNull() ?: 0.0
                    axis.anglePID.setSaturationLimits(minSatInner.toDoubleOrNull() ?: -250.0, maxSatInner.toDoubleOrNull() ?: 250.0)
                    axis.sampleTimeAngleMs = (dtInner.toLongOrNull() ?: 10L).coerceAtLeast(1L)
                    if (enableInner) axis.enableAngle = true else axis.stopAngle()

                    axis.ratePID.kp = kpOuter.toDoubleOrNull() ?: 0.0
                    axis.ratePID.ki = kiOuter.toDoubleOrNull() ?: 0.0
                    axis.ratePID.kd = kdOuter.toDoubleOrNull() ?: 0.0
                    axis.ratePID.setSaturationLimits(minSatOuter.toDoubleOrNull() ?: -1000.0, maxSatOuter.toDoubleOrNull() ?: 1000.0)
                    axis.sampleTimeRateMs = (dtOuter.toLongOrNull() ?: 10L).coerceAtLeast(1L)
                    if (enableOuter) axis.enableRate = true else axis.stopRate()
                }) {
                    Text("Apply ${axis.axisName}")
                }
            }
        }
    }
}

@Composable
fun TelemetryWindow(label: String, value: String, subLabel: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF424242), RoundedCornerShape(8.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color(0xFF90CAF9), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = Color(0xFF00E676), fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subLabel, color = Color(0xFFBDBDBD), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
