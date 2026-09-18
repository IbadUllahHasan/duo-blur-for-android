package com.ibad.foldecho

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.view.WindowManager

/**
 * Owns the one VirtualDisplay this MediaProjection is allowed to create.
 * Android permits a single createVirtualDisplay() per projection — calling it
 * again throws — so the display is built once, when consent is granted, and
 * then parked between gestures by detaching its surface. Re-attaching the
 * surface is what "starts capturing"; nothing here is ever rebuilt per tilt.
 *
 * Every method must be called on [handler]'s thread, which is also where
 * frames are decoded, so none of this touches the main thread.
 */
class CaptureSession(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val handler: Handler
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    // Reused across captures: a fresh full-screen bitmap per frame would churn
    // ~10MB a shot and drag the GC into the middle of the animation.
    private var paddedFrame: Bitmap? = null
    private var croppedFrame: Bitmap? = null

    private var pending: ((Bitmap?) -> Unit)? = null
    private val timeout = Runnable { finish(null) }

    private var width = 0
    private var height = 0
    private var densityDpi = 0

    fun create() {
        if (virtualDisplay != null) return
        readDisplaySize()
        imageReader = newImageReader()
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "FoldEchoCapture",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null, // parked until a gesture asks for a frame
            null,
            handler
        )
    }

    /**
     * Wakes the display just long enough to grab one frame, then parks it
     * again. The caller freezes that frame for the whole gesture — re-reading
     * the screen while the overlay is up would capture the overlay itself and
     * feed it back into the next frame.
     */
    fun requestFrame(onFrame: (Bitmap?) -> Unit) {
        val display = virtualDisplay
        val reader = imageReader
        if (display == null || reader == null || pending != null) {
            onFrame(null)
            return
        }
        pending = onFrame
        display.surface = reader.surface
        handler.postDelayed(timeout, FRAME_TIMEOUT_MS)
    }

    fun release() {
        finish(null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        paddedFrame = null
        croppedFrame = null
    }

    /** Rebuilds the reader at the new screen size after a rotation. */
    fun updateDisplaySize() {
        val display = virtualDisplay ?: return
        val previousWidth = width
        val previousHeight = height
        readDisplaySize()
        if (width == previousWidth && height == previousHeight) return

        finish(null)
        imageReader?.close()
        imageReader = newImageReader()
        display.resize(width, height, densityDpi)
        paddedFrame = null
        croppedFrame = null
    }

    private fun newImageReader(): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGES).apply {
            setOnImageAvailableListener({ reader -> onImageAvailable(reader) }, handler)
        }

    private fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            if (pending == null) return
            finish(copyToBitmap(image))
        } finally {
            image.close()
        }
    }

    private fun finish(bitmap: Bitmap?) {
        val callback = pending ?: return
        pending = null
        handler.removeCallbacks(timeout)
        virtualDisplay?.surface = null // park: no consumer, no producer, no drain
        callback(bitmap)
    }

    private fun copyToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val paddedWidth = plane.rowStride / plane.pixelStride

        val padded = paddedFrame?.takeIf { it.width == paddedWidth && it.height == height }
            ?: Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                .also { paddedFrame = it }
        padded.copyPixelsFromBuffer(plane.buffer)
        if (paddedWidth == width) return padded

        val cropped = croppedFrame?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { croppedFrame = it }
        Canvas(cropped).drawBitmap(padded, 0f, 0f, null)
        return cropped
    }

    private fun readDisplaySize() {
        val bounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        width = bounds.width()
        height = bounds.height()
        densityDpi = context.resources.configuration.densityDpi
    }

    private companion object {
        const val MAX_IMAGES = 2
        const val FRAME_TIMEOUT_MS = 250L
    }
}
