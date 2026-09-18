package com.ibad.foldecho

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.abs

/**
 * Foreground service owning the tilt pipeline. The MediaProjection and its one
 * VirtualDisplay are set up once, when consent is granted; from then on a tilt
 * gesture only asks for a frame and shows/hides the overlay. All sensor and
 * capture work happens on [bg]; the main thread is touched only to move views.
 */
class FoldEchoService : Service(), TiltTracker.Listener {

    private val bgThread = HandlerThread("FoldEchoCapture").apply { start() }
    private val bg = Handler(bgThread.looper)
    private val main = Handler(Looper.getMainLooper())

    private lateinit var overlay: OverlayController
    private var projection: MediaProjection? = null
    private var capture: CaptureSession? = null
    private var tiltTracker: TiltTracker? = null

    @Volatile private var tunables = Tunables()
    private val tunablesChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        tunables = FoldEchoSettings.load(this)
    }

    // Owned by the bg thread.
    private var active = false
    private var frameInFlight = false
    private var activeSince = 0L
    private var suppressedUntilNeutral = false

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this)
        tunables = FoldEchoSettings.load(this)
        FoldEchoSettings.prefs(this).registerOnSharedPreferenceChangeListener(tunablesChanged)
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECALIBRATE -> {
                bg.post { tiltTracker?.calibrateToCurrentPose() }
                return START_NOT_STICKY
            }
        }

        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            // No consent token — which is also what a restart would hand us.
            // Starting a mediaProjection service without one is a SecurityException.
            stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
        )

        val mediaProjection = getSystemService(MediaProjectionManager::class.java)
            .getMediaProjection(resultCode, resultData)
        projection = mediaProjection
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, main)

        bg.post {
            capture = CaptureSession(this, mediaProjection, bg).apply { create() }
            tiltTracker = TiltTracker(getSystemService(SensorManager::class.java), bg, this)
                .apply { start() }
        }
        FoldEchoState.running.value = true

        // Consent can't survive a restart, so there's nothing to be sticky about.
        return START_NOT_STICKY
    }

    override fun onTilt(deviationDeg: Float, tiltUpDeg: Float, tiltRightDeg: Float) {
        val current = tunables
        if (abs(deviationDeg - FoldEchoState.deviationDeg.value) > DEVIATION_REPORT_STEP) {
            FoldEchoState.deviationDeg.value = deviationDeg
        }

        if (suppressedUntilNeutral) {
            if (deviationDeg < current.releaseDeg) suppressedUntilNeutral = false
            return
        }

        val heldTooLong = active &&
            SystemClock.elapsedRealtime() - activeSince > MAX_ACTIVE_MS
        if (heldTooLong) {
            // A touch-blocking overlay that never lifts would strand the user,
            // so give up on this gesture and wait for a return to neutral.
            suppressedUntilNeutral = true
            endGesture()
            return
        }

        val shouldBeActive =
            if (active) deviationDeg >= current.releaseDeg else deviationDeg >= current.activateDeg

        if (shouldBeActive && !active) {
            beginGesture()
        } else if (!shouldBeActive && active) {
            endGesture()
        }

        if (active) {
            val effect = FrameProcessor.effectFor(deviationDeg, tiltUpDeg, tiltRightDeg, current)
            main.post { overlay.applyEffect(effect) }
        }
    }

    private fun beginGesture() {
        val session = capture ?: return
        if (frameInFlight) return

        active = true
        activeSince = SystemClock.elapsedRealtime()
        frameInFlight = true
        FoldEchoState.effectActive.value = true

        session.requestFrame { frame ->
            frameInFlight = false
            if (frame == null) {
                // Nothing to show (secure window, or the grab timed out).
                if (active) endGesture()
                return@requestFrame
            }
            if (!active) return@requestFrame
            main.post { overlay.show(frame) }
        }
    }

    private fun endGesture() {
        active = false
        FoldEchoState.effectActive.value = false
        main.post { overlay.hide() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        bg.post {
            if (active) endGesture()
            capture?.updateDisplaySize()
        }
    }

    override fun onDestroy() {
        FoldEchoSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(tunablesChanged)
        tiltTracker?.stop()
        overlay.hide()
        projection?.stop()
        projection = null
        bg.post { capture?.release() }
        bgThread.quitSafely()
        FoldEchoState.reset()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FoldEcho", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val openPanel = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val recalibrate = PendingIntent.getService(
            this, 1,
            Intent(this, FoldEchoService::class.java).setAction(ACTION_RECALIBRATE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, FoldEchoService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoldEcho is watching for tilt")
            .setContentText("Tilt the phone to trigger the effect anywhere on the device.")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(openPanel)
            .setOngoing(true)
            .addAction(0, "Recalibrate", recalibrate)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.ibad.foldecho.action.STOP"
        const val ACTION_RECALIBRATE = "com.ibad.foldecho.action.RECALIBRATE"
        const val EXTRA_RESULT_CODE = "com.ibad.foldecho.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.ibad.foldecho.extra.RESULT_DATA"

        private const val CHANNEL_ID = "foldecho_service"
        private const val NOTIFICATION_ID = 42
        private const val MAX_ACTIVE_MS = 8_000L
        private const val DEVIATION_REPORT_STEP = 0.2f
    }
}
