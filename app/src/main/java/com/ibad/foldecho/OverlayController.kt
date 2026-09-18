package com.ibad.foldecho

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.hypot

/**
 * Owns the full-screen TYPE_APPLICATION_OVERLAY window showing the captured
 * frame. The window only exists while the effect is running, which is also
 * what restores touch passthrough when it ends — while it's up it deliberately
 * swallows touches, since the user is looking at a frozen snapshot and tapping
 * "through" it would hit things they can't see.
 *
 * View hierarchy:
 *   root (black background, fills the screen)
 *     - card (the 3D-transformed "plane": rotationX/Y, scale, cameraDistance
 *       and the rounded-corner clip all live here, so image + edgeFade lean,
 *       recede and round together as one rigid unit)
 *         - image (the captured frame)
 *         - edgeFade (a radial gradient on top, alpha driven by tilt)
 *     - scrim (full-screen dim, deliberately NOT part of `card` — dimming the
 *       whole screen is a different thing from the plane fading at its edges)
 *
 * All methods must be called on the main thread.
 */
class OverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private var root: FrameLayout? = null
    private var card: FrameLayout? = null
    private var image: ImageView? = null
    private var edgeFade: View? = null
    private var scrim: View? = null

    private var rotationXSpring: SpringAnimation? = null
    private var rotationYSpring: SpringAnimation? = null
    private var scaleXSpring: SpringAnimation? = null
    private var scaleYSpring: SpringAnimation? = null

    // 0 disables damping (a light settle, no overshoot); 1 is a visible bounce.
    private val maxDampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
    private val minDampingRatio = SpringForce.DAMPING_RATIO_HIGH_BOUNCY
    private val springStiffness = SpringForce.STIFFNESS_MEDIUM

    private var cornerRadiusPx = 0f
    private val cardOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
        }
    }

    fun show(frame: Bitmap) {
        if (root == null) attach()
        image?.setImageBitmap(frame)
    }

    fun applyEffect(effect: FrameProcessor.Effect, tunables: Tunables) {
        val cardView = card ?: return
        val density = context.resources.displayMetrics.density

        applyMotion(cardView, DynamicAnimation.ROTATION_X, rotationXSpring, effect.rotationXDeg, tunables)
        applyMotion(cardView, DynamicAnimation.ROTATION_Y, rotationYSpring, effect.rotationYDeg, tunables)
        applyMotion(cardView, DynamicAnimation.SCALE_X, scaleXSpring, effect.scale, tunables)
        applyMotion(cardView, DynamicAnimation.SCALE_Y, scaleYSpring, effect.scale, tunables)

        // Recomputed every call (not just once at attach) so the "Perspective"
        // slider takes effect live while the service is running.
        cardView.cameraDistance = density * effect.cameraDistanceDp

        image?.setRenderEffect(
            if (effect.blurPx >= 1f) {
                RenderEffect.createBlurEffect(effect.blurPx, effect.blurPx, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )
        scrim?.alpha = effect.dim
        edgeFade?.alpha = effect.edgeFadeAlpha

        val targetRadiusPx = if (tunables.cornerRadiusEnabled) {
            tunables.cornerRadiusStrength.coerceIn(0f, 1f) * tunables.cornerRadiusBaseDp * density
        } else {
            0f
        }
        if (targetRadiusPx != cornerRadiusPx) {
            cornerRadiusPx = targetRadiusPx
            cardView.invalidateOutline()
        }
    }

    /** Drives [target] onto [property] of [view] via [spring] — or, if motion softness is off, sets it directly and cancels any animation in flight. */
    private fun applyMotion(
        view: View,
        property: DynamicAnimation.ViewProperty,
        spring: SpringAnimation?,
        target: Float,
        tunables: Tunables
    ) {
        if (spring == null) return
        if (!tunables.motionSoftnessEnabled) {
            spring.cancel()
            property.setValue(view, target)
            return
        }
        val softness = tunables.motionSoftness.coerceIn(0f, 1f)
        spring.spring.stiffness = springStiffness
        spring.spring.dampingRatio = maxDampingRatio - softness * (maxDampingRatio - minDampingRatio)
        spring.animateToFinalPosition(target)
    }

    fun hide() {
        val container = root ?: return
        rotationXSpring?.cancel()
        rotationYSpring?.cancel()
        scaleXSpring?.cancel()
        scaleYSpring?.cancel()
        windowManager.removeView(container)
        root = null
        card = null
        image = null
        edgeFade = null
        scrim = null
        rotationXSpring = null
        rotationYSpring = null
        scaleXSpring = null
        scaleYSpring = null
        cornerRadiusPx = 0f
    }

    private fun attach() {
        val container = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val cardView = FrameLayout(context).apply {
            outlineProvider = cardOutlineProvider
            clipToOutline = true
        }
        val frameView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
        }
        val edgeFadeView = View(context).apply {
            background = buildEdgeFadeDrawable()
            alpha = 0f
        }
        val scrimView = View(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }

        val matchParent = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        cardView.addView(frameView, FrameLayout.LayoutParams(matchParent))
        cardView.addView(edgeFadeView, FrameLayout.LayoutParams(matchParent))
        container.addView(cardView, FrameLayout.LayoutParams(matchParent))
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
        card = cardView
        image = frameView
        edgeFade = edgeFadeView
        scrim = scrimView

        rotationXSpring = SpringAnimation(cardView, DynamicAnimation.ROTATION_X).withSpring()
        rotationYSpring = SpringAnimation(cardView, DynamicAnimation.ROTATION_Y).withSpring()
        scaleXSpring = SpringAnimation(cardView, DynamicAnimation.SCALE_X).withSpring()
        scaleYSpring = SpringAnimation(cardView, DynamicAnimation.SCALE_Y).withSpring()
    }

    private fun SpringAnimation.withSpring(): SpringAnimation = apply {
        spring = SpringForce(0f).apply {
            stiffness = springStiffness
            dampingRatio = maxDampingRatio
        }
    }

    /** Transparent at the center, opaque black at the edge — equivalent to fading the content's own alpha, since the container behind it is already black. */
    private fun buildEdgeFadeDrawable(): GradientDrawable {
        val metrics = context.resources.displayMetrics
        val radius = hypot(metrics.widthPixels / 2.0, metrics.heightPixels / 2.0).toFloat()
        return GradientDrawable().apply {
            setGradientType(GradientDrawable.RADIAL_GRADIENT)
            setGradientRadius(radius)
            setColors(intArrayOf(Color.TRANSPARENT, Color.BLACK))
        }
    }
}
