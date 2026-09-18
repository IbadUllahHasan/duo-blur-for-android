package com.ibad.foldecho

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.view.WindowManager

/**
 * Owns the VirtualDisplay + ImageReader pair used to pull frames from the
 * live MediaProjection session. Created on [open] and torn down on [close]
 * per activation window — per the spec, don't keep pulling frames at full
 * rate while idle — while the MediaProjection object itself, and the
 * system's recording indicator it carries, stays alive for the whole
 * service lifetime regardless of how many times this opens and closes.
 */
class CaptureSession(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    var width = 0
        private set
    var height = 0
        private set

    fun open() {
        if (virtualDisplay != null) return

        val windowManager = context.getSystemService(WindowManager::class.java)
        val bounds = windowManager.currentWindowMetrics.bounds
        width = bounds.width()
        height = bounds.height()
        val densityDpi = context.resources.configuration.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "FoldEchoCapture",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )
    }

    /** Grabs the most recent frame as an ARGB_8888 bitmap, or null if nothing new arrived since the last pull. */
    fun pullLatestFrame(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            return if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } finally {
            image.close()
        }
    }

    fun close() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }
}
