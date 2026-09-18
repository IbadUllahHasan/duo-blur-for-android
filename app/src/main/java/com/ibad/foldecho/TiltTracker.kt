package com.ibad.foldecho

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Tracks continuous device tilt as angular deviation from a calibrated
 * "held normally" neutral pose, using TYPE_ROTATION_VECTOR. Deviation is a
 * magnitude, not a signed single-axis value, so tilting left, right, up, or
 * down all drive the effect the same way — this one continuous trigger also
 * covers "picking up the phone from flat," since that's itself a large
 * deviation from neutral.
 */
class TiltTracker(
    private val sensorManager: SensorManager,
    private val listener: Listener
) : SensorEventListener {

    interface Listener {
        /**
         * [deviationDeg] is angular distance from the neutral pose, in degrees.
         * [dPitch]/[dRoll] are signed and normalized to [-1, 1] against
         * [NORMALIZE_DEG], for driving warp direction.
         */
        fun onTilt(deviationDeg: Float, dPitch: Float, dRoll: Float)
    }

    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var neutralPitchDeg = 0f
    private var neutralRollDeg = 0f
    private var calibrated = false

    fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Re-baselines the neutral pose to whatever the device's orientation is on the next sensor reading. */
    fun calibrateToCurrentPose() {
        calibrated = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

        if (!calibrated) {
            neutralPitchDeg = pitchDeg
            neutralRollDeg = rollDeg
            calibrated = true
            return
        }

        val dPitchDeg = pitchDeg - neutralPitchDeg
        val dRollDeg = rollDeg - neutralRollDeg
        val deviationDeg = sqrt(dPitchDeg * dPitchDeg + dRollDeg * dRollDeg)

        listener.onTilt(
            deviationDeg = deviationDeg,
            dPitch = (dPitchDeg / NORMALIZE_DEG).coerceIn(-1f, 1f),
            dRoll = (dRollDeg / NORMALIZE_DEG).coerceIn(-1f, 1f)
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** Deviation magnitude, in degrees, that maps to "full" warp/blur/dim intensity. */
        const val NORMALIZE_DEG = 30f
    }
}
