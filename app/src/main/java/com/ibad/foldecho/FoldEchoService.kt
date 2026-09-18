package com.ibad.foldecho

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.SensorManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Foreground service owning the whole tilt-driven capture pipeline: keeps
 * the MediaProjection session alive for as long as the feature is enabled —
 * this is what keeps the system's recording indicator up and avoids
 * re-prompting for consent on every tilt — watches continuous tilt
 * deviation from a calibrated neutral pose, and only spins up the
 * VirtualDisplay/ImageReader/overlay window for the duration of an actual
 * tilt gesture.
 */
class FoldEchoService : Service(), TiltTracker.Listener {

    private lateinit var tiltTracker: TiltTracker
    private lateinit var overlay: OverlayController
    private var mediaProjection: MediaProjection? = null
    private var captureSession: CaptureSession? = null

    private var active = false
    @Volatile private var lastDeviationDeg = 0f
    @Volatile private var lastDPitch = 0f
    @Volatile private var lastDRoll = 0f

    private val frameThread = HandlerThread("FoldEchoFrames").apply { start() }
    private val frameHandler = Handler(frameThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var frameLoopRunning = false

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this)
        val sensorManager = getSystemService(SensorManager::class.java)
        tiltTracker = TiltTracker(sensorManager, this)
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RECALIBRATE -> {
                tiltTracker.calibrateToCurrentPose()
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (mediaProjection == null) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
            val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            if (resultData != null) {
                val projectionManager = getSystemService(MediaProjectionManager::class.java)
                val projection = projectionManager.getMediaProjection(resultCode, resultData)
                mediaProjection = projection
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() = stopSelf()
                }, mainHandler)
                captureSession = CaptureSession(this, projection)
                tiltTracker.start()
            }
        }

        return START_STICKY
    }

    override fun onTilt(deviationDeg: Float, dPitch: Float, dRoll: Float) {
        lastDeviationDeg = deviationDeg
        lastDPitch = dPitch
        lastDRoll = dRoll

        val shouldActivate = if (active) {
            deviationDeg >= FrameProcessor.DEACTIVATE_THRESHOLD_DEG
        } else {
            deviationDeg >= FrameProcessor.ACTIVATE_THRESHOLD_DEG
        }

        if (shouldActivate && !active) {
            active = true
            captureSession?.open()
            overlay.show()
            startFrameLoop()
        } else if (!shouldActivate && active) {
            active = false
            stopFrameLoop()
            overlay.hide()
            captureSession?.close()
        }
    }

    private fun startFrameLoop() {
        if (frameLoopRunning) return
        frameLoopRunning = true
        frameHandler.post(frameLoopRunnable)
    }

    private fun stopFrameLoop() {
        frameLoopRunning = false
        frameHandler.removeCallbacks(frameLoopRunnable)
    }

    private val frameLoopRunnable = object : Runnable {
        override fun run() {
            if (!frameLoopRunning) return
            val session = captureSession
            val frame = session?.pullLatestFrame()
            if (session != null && frame != null) {
                val intensity = FrameProcessor.intensity(lastDeviationDeg)
                val matrix = FrameProcessor.warpMatrix(
                    session.width.toFloat(), session.height.toFloat(), lastDPitch, lastDRoll, intensity
                )
                val blur = FrameProcessor.blurRadiusPx(intensity)
                val dim = FrameProcessor.dimAlpha(intensity)
                mainHandler.post { overlay.update(frame, matrix, blur, dim) }
            }
            frameHandler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    override fun onDestroy() {
        tiltTracker.stop()
        stopFrameLoop()
        overlay.hide()
        captureSession?.close()
        mediaProjection?.stop()
        mediaProjection = null
        frameThread.quitSafely()
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

        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FoldEchoService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val recalibratePendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FoldEchoService::class.java).setAction(ACTION_RECALIBRATE),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FoldEcho is watching for tilt")
            .setContentText("Tilt the phone to trigger the effect anywhere on the device.")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setOngoing(true)
            .addAction(0, "Recalibrate", recalibratePendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.ibad.foldecho.action.STOP"
        const val ACTION_RECALIBRATE = "com.ibad.foldecho.action.RECALIBRATE"
        const val EXTRA_RESULT_CODE = "com.ibad.foldecho.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.ibad.foldecho.extra.RESULT_DATA"
        private const val CHANNEL_ID = "foldecho_service"
        private const val NOTIFICATION_ID = 42

        /** ~5fps. Spec: start low, raise only if the effect looks laggy in testing. */
        private const val FRAME_INTERVAL_MS = 200L
    }
}
