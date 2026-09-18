package com.ibad.foldecho

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Owns the full-screen TYPE_APPLICATION_OVERLAY window showing the captured
 * frame. The window only exists while the effect is running, which is also
 * what restores touch passthrough when it ends — while it's up it deliberately
 * swallows touches, since the user is looking at a frozen snapshot and tapping
 * "through" it would hit things they can't see.
 *
 * All methods must be called on the main thread.
 */
class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var image: ImageView? = null
    private var scrim: View? = null

    fun show(frame: Bitmap) {
        if (root == null) attach()
        image?.setImageBitmap(frame)
    }

    fun applyEffect(effect: FrameProcessor.Effect) {
        val view = image ?: return
        view.rotationX = effect.rotationXDeg
        view.rotationY = effect.rotationYDeg
        view.scaleX = effect.scale
        view.scaleY = effect.scale
        // Recomputed every call (not just once at attach) so the "Perspective"
        // slider takes effect live while the service is running.
        view.cameraDistance = context.resources.displayMetrics.density * effect.cameraDistanceDp
        view.setRenderEffect(
            if (effect.blurPx >= 1f) {
                RenderEffect.createBlurEffect(effect.blurPx, effect.blurPx, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )
        scrim?.alpha = effect.dim
    }

    fun hide() {
        val container = root ?: return
        windowManager.removeView(container)
        root = null
        image = null
        scrim = null
    }

    private fun attach() {
        val container = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val frameView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
        }
        val scrimView = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }

        val matchParent = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(frameView, matchParent)
        container.addView(scrimView, FrameLayout.LayoutParams(matchParent))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // Without this the overlay renders in software, where
                // setRenderEffect does nothing and a full-screen redraw crawls.
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        windowManager.addView(container, params)
        root = container
        image = frameView
        scrim = scrimView
    }
}
