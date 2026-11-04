package com.banumusateam.digitalpid

import kotlin.math.max
import kotlin.math.min

class DiscretePID(
    var kp: Double = 0.0,
    var ki: Double = 0.0,
    var kd: Double = 0.0,
    var minOutput: Double = -250.0,
    var maxOutput: Double = 250.0
) {
    var integral: Double = 0.0
        private set
    var prevError: Double = 0.0
        private set
    var currentError: Double = 0.0
        private set
    var currentOutput: Double = 0.0
        private set
    private var firstRun: Boolean = true

    fun setSaturationLimits(minVal: Double, maxVal: Double) {
        if (minVal <= maxVal) {
            minOutput = minVal
            maxOutput = maxVal
        } else {
            minOutput = maxVal
            maxOutput = minVal
        }
    }

    fun compute(setpoint: Double, measured: Double, dtSeconds: Double): Double {
        currentError = setpoint - measured

        if (firstRun) {
            prevError = currentError
            firstRun = false
        }

        val pTerm = kp * currentError

        // Integral with Anti-Windup clamped to [minOutput, maxOutput]
        integral += ki * currentError * dtSeconds
        integral = clamp(integral, minOutput, maxOutput)

        // Derivative term protected against zero/negative dt
        val dTerm = if (dtSeconds > 0.0) {
            kd * (currentError - prevError) / dtSeconds
        } else {
            0.0
        }

        val u = clamp(pTerm + integral + dTerm, minOutput, maxOutput)
        prevError = currentError
        currentOutput = u
        return u
    }

    fun reset() {
        integral = 0.0
        prevError = 0.0
        currentError = 0.0
        currentOutput = 0.0
        firstRun = true
    }

    private fun clamp(value: Double, minVal: Double, maxVal: Double): Double {
        return max(minVal, min(maxVal, value))
    }
}

/**
 * Represents one complete quadcopter axis (Roll, Pitch, or Yaw)
 * combining the Angle PID (Stage 1) and Angular Rate -> Torque PID (Stage 2).
 */
class CascadeAxisController(val axisName: String) {
    val anglePID = DiscretePID(minOutput = -250.0, maxOutput = 250.0)
    val ratePID = DiscretePID(minOutput = -1000.0, maxOutput = 1000.0)

    var enableAngle: Boolean = false
    var enableRate: Boolean = false
    var linkedCascade: Boolean = true // Auto-feeds AnglePIDOutput -> desiredRate

    var desiredAngle: Double = 0.0
    var manualDesiredRate: Double = 0.0

    var sampleTimeAngleMs: Long = 10L
    var sampleTimeRateMs: Long = 10L

    fun stepAngle(measuredAngle: Double): Double {
        if (!enableAngle) return 0.0
        val dt = sampleTimeAngleMs / 1000.0
        return anglePID.compute(desiredAngle, measuredAngle, dt)
    }

    fun stepRate(measuredRate: Double): Double {
        if (!enableRate) return 0.0
        val dt = sampleTimeRateMs / 1000.0
        val activeRateSetpoint = if (enableAngle && linkedCascade) {
            anglePID.currentOutput
        } else {
            manualDesiredRate
        }
        return ratePID.compute(activeRateSetpoint, measuredRate, dt)
    }

    fun stopAngle() {
        enableAngle = false
        anglePID.reset()
    }

    fun stopRate() {
        enableRate = false
        ratePID.reset()
    }
}
