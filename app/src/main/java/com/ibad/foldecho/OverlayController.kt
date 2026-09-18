package com.ibad.foldecho

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Owns the full-screen TYPE_APPLICATION_OVERLAY window that shows the
 * processed (warped/blurred/dimmed) frame on top of the real content. The
 * window exists only while the effect is active — added in [show], removed
 * in [hide] — which is also what restores real touch passthrough to the
 * app underneath once tilt returns to neutral, with no FLAG_NOT_TOUCHABLE
 * juggling needed. While shown, the window is not FLAG_NOT_FOCUSABLE so it
 * still consumes touches (it's a processed snapshot, not the live view —
 * touching "through" it to the real app underneath would be confusing), but
 * IS not-focusable-for-keys so it doesn't steal keyboard/back handling from
 * whatever's underneath.
 */
class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var imageView: ImageView? = null
    private var scrim: View? = null

    fun show() {
        if (root != null) return

        val container = FrameLayout(context)
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.MATRIX
        }
        val scrimView = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }
        container.addView(
            image,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        container.addView(
            scrimView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(container, params)
        root = container
        imageView = image
        scrim = scrimView
    }

    fun update(frame: Bitmap, warpMatrix: Matrix, blurRadiusPx: Float, dimAlpha: Float) {
        val image = imageView ?: return
        image.setImageBitmap(frame)
        image.imageMatrix = warpMatrix
        image.setRenderEffect(
            if (blurRadiusPx > 0.5f) RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP) else null
        )
        scrim?.alpha = dimAlpha
    }

    fun hide() {
        val container = root ?: return
        windowManager.removeView(container)
        root = null
        imageView = null
        scrim = null
    }
}
