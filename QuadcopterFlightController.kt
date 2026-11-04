package com.banumusateam.digitalpid

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AxisTelemetry(
    val measuredAngle: Double = 0.0,
    val measuredRate: Double = 0.0,
    val angleError: Double = 0.0,
    val anglePIDOutput: Double = 0.0,
    val rateError: Double = 0.0,
    val torqueOutput: Double = 0.0
)

data class QuadcopterTelemetry(
    val pitch: AxisTelemetry = AxisTelemetry(),
    val roll: AxisTelemetry = AxisTelemetry(),
    val yaw: AxisTelemetry = AxisTelemetry()
)

class QuadcopterFlightController(context: Context) : SensorEventListener {

    val pitchController = CascadeAxisController("PITCH")
    val rollController = CascadeAxisController("ROLL")
    val yawController = CascadeAxisController("YAW")

    val usbController = UsbSerialController(context)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // [0] = Roll, [1] = Pitch, [2] = Yaw
    @Volatile private var angles = DoubleArray(3)
    @Volatile private var rates = DoubleArray(3)

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controlLoopJob: Job? = null

    private val _telemetry = MutableStateFlow(QuadcopterTelemetry())
    val telemetry: StateFlow<QuadcopterTelemetry> = _telemetry

    fun startController() {
        registerSensors()
        if (controlLoopJob?.isActive == true) return

        controlLoopJob = controllerScope.launch {
            var lastPitchAngleTick = System.currentTimeMillis()
            var lastRollAngleTick = System.currentTimeMillis()
            var lastYawAngleTick = System.currentTimeMillis()

            var lastPitchRateTick = System.currentTimeMillis()
            var lastRollRateTick = System.currentTimeMillis()
            var lastYawRateTick = System.currentTimeMillis()

            while (isActive) {
                val now = System.currentTimeMillis()

                // Independent Inner Angle PID Loops
                if (now - lastPitchAngleTick >= pitchController.sampleTimeAngleMs) {
                    pitchController.stepAngle(angles[1])
                    lastPitchAngleTick = now
                }
                if (now - lastRollAngleTick >= rollController.sampleTimeAngleMs) {
                    rollController.stepAngle(angles[0])
                    lastRollAngleTick = now
                }
                if (now - lastYawAngleTick >= yawController.sampleTimeAngleMs) {
                    yawController.stepAngle(angles[2])
                    lastYawAngleTick = now
                }

                // Independent Outer Rate -> Torque PID Loops
                var rateUpdated = false
                if (now - lastPitchRateTick >= pitchController.sampleTimeRateMs) {
                    pitchController.stepRate(rates[1])
                    lastPitchRateTick = now
                    rateUpdated = true
                }
                if (now - lastRollRateTick >= rollController.sampleTimeRateMs) {
                    rollController.stepRate(rates[0])
                    lastRollRateTick = now
                    rateUpdated = true
                }
                if (now - lastYawRateTick >= yawController.sampleTimeRateMs) {
                    yawController.stepRate(rates[2])
                    lastYawRateTick = now
                    rateUpdated = true
                }

                if (rateUpdated) {
                    // Send computed Torques over USB OTG to Arduino / STM32
                    usbController.sendTorques(
                        pitchTorque = pitchController.ratePID.currentOutput,
                        rollTorque = rollController.ratePID.currentOutput,
                        yawTorque = yawController.ratePID.currentOutput
                    )

                    // Update UI Telemetry
                    _telemetry.value = QuadcopterTelemetry(
                        pitch = buildTelemetry(pitchController, angles[1], rates[1]),
                        roll = buildTelemetry(rollController, angles[0], rates[0]),
                        yaw = buildTelemetry(yawController, angles[2], rates[2])
                    )
                }

                delay(1L)
            }
        }
    }

    fun stopAll() {
        controlLoopJob?.cancel()
        pitchController.stopAngle(); pitchController.stopRate()
        rollController.stopAngle(); rollController.stopRate()
        yawController.stopAngle(); yawController.stopRate()
        usbController.unregister()
        sensorManager?.unregisterListener(this)
    }

    private fun buildTelemetry(ctrl: CascadeAxisController, angle: Double, rate: Double) = AxisTelemetry(
        measuredAngle = angle,
        measuredRate = rate,
        angleError = ctrl.anglePID.currentError,
        anglePIDOutput = ctrl.anglePID.currentOutput,
        rateError = ctrl.ratePID.currentError,
        torqueOutput = ctrl.ratePID.currentOutput
    )

    private fun registerSensors() {
        rotationSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        gyroSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            angles[0] = Math.toDegrees(orientation[2].toDouble()) // Roll
            angles[1] = Math.toDegrees(orientation[1].toDouble()) // Pitch
            angles[2] = Math.toDegrees(orientation[0].toDouble()) // Yaw
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            rates[0] = Math.toDegrees(event.values[0].toDouble()) // Roll Rate
            rates[1] = Math.toDegrees(event.values[1].toDouble()) // Pitch Rate
            rates[2] = Math.toDegrees(event.values[2].toDouble()) // Yaw Rate
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
