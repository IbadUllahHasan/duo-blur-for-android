package com.ibad.foldecho

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * The ray-traced fold model (res/raw/duo_fold.agsl), ported from
 * github.com/Atomicx7/Duo-animation.
 *
 * Every RuntimeShader reference lives in this class so it is only ever
 * class-loaded from behind an SDK_INT check: AGSL is API 33, and minSdk here
 * is 31. On 31/32 this class is never touched and OverlayController keeps
 * using the lean/scale transform renderer.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class FoldShader private constructor(private val shader: RuntimeShader) {

    /**
     * Uniforms are in device pixels — the millimetre-based tunables are
     * converted by the caller, which is the side that knows the display's
     * physical density.
     */
    fun renderEffect(
        widthPx: Float,
        heightPx: Float,
        tiltDegrees: Float,
        hingeAxis: Float,
        hingeSide: Float,
        eyeDistancePx: Float,
        blurSpread: Float,
        maxBlurRadiusPx: Float,
        darkenPerPx: Float,
        maxDarken: Float
    ): RenderEffect {
        shader.setFloatUniform("resolution", widthPx, heightPx)
        shader.setFloatUniform("tiltDegrees", tiltDegrees)
        shader.setFloatUniform("hingeAxis", hingeAxis)
        shader.setFloatUniform("hingeSide", hingeSide)
        shader.setFloatUniform("eyeDistancePx", eyeDistancePx)
        shader.setFloatUniform("blurSpread", blurSpread)
        shader.setFloatUniform("maxBlurRadius", maxBlurRadiusPx)
        shader.setFloatUniform("darkenPerPx", darkenPerPx)
        shader.setFloatUniform("maxDarken", maxDarken)
        return RenderEffect.createRuntimeShaderEffect(shader, CONTENT_UNIFORM)
    }

    companion object {
        private const val CONTENT_UNIFORM = "content"
        private const val TAG = "FoldShader"

        /**
         * Null when the AGSL won't compile, which is a real outcome rather
         * than a defensive flourish: the original's author documents it
         * failing on-device for shaders that use anything beyond the most
         * basic constructs. A null here means the caller falls back to the
         * transform renderer instead of the screen going black.
         */
        fun createOrNull(context: Context): FoldShader? = try {
            val source = context.resources.openRawResource(R.raw.duo_fold)
                .bufferedReader()
                .use { it.readText() }
            FoldShader(RuntimeShader(source))
        } catch (error: Throwable) {
            Log.w(TAG, "Fold shader unavailable; using the transform renderer", error)
            null
        }
    }
}
