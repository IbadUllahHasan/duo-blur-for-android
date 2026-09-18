package com.ibad.foldecho

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2

/**
 * Tracks device tilt as angular deviation from a calibrated "held normally"
 * pose, using TYPE_ROTATION_VECTOR. Deviation is a magnitude, so tilting
 * left, right, up or down all drive the effect the same way, and picking the
 * phone up from flat is just a large deviation rather than a separate trigger.
 *
 * Deviation is measured as the angle between the screen's normal now and at
 * the neutral pose, rather than by subtracting pitch/roll: raw Euler angles
 * wrap at ±180°, which made the effect jump when the phone passed vertical.
 *
 * Callbacks arrive on the [handler]'s thread, never the main thread.
 */
class TiltTracker(
    private val sensorManager: SensorManager,
    private val handler: Handler,
    private val listener: Listener
) : SensorEventListener {

    interface Listener {
        /**
         * [deviationDeg] is the angle from neutral. [tiltUpDeg]/[tiltRightDeg]
         * are the signed components of that tilt, for driving warp direction.
         */
        fun onTilt(deviationDeg: Float, tiltUpDeg: Float, tiltRightDeg: Float)
    }

    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val neutralNormal = FloatArray(3)
    private var calibrated = false

    private var deviation = 0f
    private var up = 0f
    private var right = 0f

    fun start() {
        val rotationVector = sensor ?: return
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_GAME, handler)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Re-baselines neutral to the device's pose on the next sensor reading. */
    fun calibrateToCurrentPose() {
        calibrated = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Columns of the rotation matrix are the device's own axes in world space.
        val screenNormal = floatArrayOf(rotationMatrix[2], rotationMatrix[5], rotationMatrix[8])

        if (!calibrated) {
            screenNormal.copyInto(neutralNormal)
            calibrated = true
            deviation = 0f
            up = 0f
            right = 0f
            return
        }

        // Where the neutral normal sits in the CURRENT device frame.
        val alongX = dot(neutralNormal, rotationMatrix[0], rotationMatrix[3], rotationMatrix[6])
        val alongY = dot(neutralNormal, rotationMatrix[1], rotationMatrix[4], rotationMatrix[7])
        val alongZ = dot(neutralNormal, rotationMatrix[2], rotationMatrix[5], rotationMatrix[8])
            .coerceIn(-1f, 1f)

        // abs() on the forward component keeps the *direction* continuous past
        // 90°; the magnitude below still grows correctly all the way to 180°.
        val forward = abs(alongZ).coerceAtLeast(1e-3f)
        val rawDeviation = Math.toDegrees(acos(alongZ).toDouble()).toFloat()
        val rawRight = Math.toDegrees(atan2(alongX, forward).toDouble()).toFloat()
        val rawUp = Math.toDegrees(atan2(alongY, forward).toDouble()).toFloat()

        // Raw rotation-vector output is jittery enough to make the overlay
        // shimmer when held still, so ease toward each new reading.
        deviation += (rawDeviation - deviation) * SMOOTHING
        right += (rawRight - right) * SMOOTHING
        up += (rawUp - up) * SMOOTHING

        listener.onTilt(deviation, up, right)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun dot(vector: FloatArray, x: Float, y: Float, z: Float): Float =
        vector[0] * x + vector[1] * y + vector[2] * z

    private companion object {
        const val SMOOTHING = 0.25f
    }
}
