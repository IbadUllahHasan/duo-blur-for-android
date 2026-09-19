package com.ibad.foldecho

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * Corner radius is context-aware, not one flat constant: bigger surfaces read
 * as "slabs of glass" with a generous radius, small elements (chips, dots)
 * stay tight so they don't look like tiny cards.
 */
object GlassRadii {
    val card = 28.dp
    val control = 16.dp
    val chip = 10.dp
}

/**
 * Every value here is deliberately separate for light and dark — a
 * translucency/tint pair that reads correctly on a dark backdrop washes out
 * or muddies on a light one. Named `cardBackdropBlurRadius`, not "blur",
 * so it never collides with the Blur effect group's own parameters.
 */
data class GlassPalette(
    val cardTint: Color,
    val cardBackdropBlurRadius: Dp,
    val noiseFactor: Float,
    val borderColor: Color,
    val specularColor: Color,
    val specularAlpha: Float,
    val ambientColors: List<Color>
)

val DarkGlassPalette = GlassPalette(
    cardTint = Color(0xFF1B1C22).copy(alpha = 0.55f),
    cardBackdropBlurRadius = 28.dp,
    noiseFactor = 0.12f,
    borderColor = Color.White.copy(alpha = 0.10f),
    specularColor = Color.White,
    specularAlpha = 0.10f,
    ambientColors = listOf(
        Color(0xFF3D5AFE),
        Color(0xFF7C4DFF),
        Color(0xFF00BFA5),
        Color(0xFFFF6E40)
    )
)

val LightGlassPalette = GlassPalette(
    cardTint = Color.White.copy(alpha = 0.55f),
    cardBackdropBlurRadius = 28.dp,
    noiseFactor = 0.08f,
    borderColor = Color.White.copy(alpha = 0.6f),
    specularColor = Color.White,
    specularAlpha = 0.35f,
    ambientColors = listOf(
        Color(0xFF90CAF9),
        Color(0xFFCE93D8),
        Color(0xFFA5D6A7),
        Color(0xFFFFCC80)
    )
)

val LocalGlassPalette = compositionLocalOf { DarkGlassPalette }

/** Shared across the whole screen: one blur source (the ambient background), many blurred children (each glass card). */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * The reusable "pane of glass": real backdrop blur of whatever's behind it
 * (via Haze), a faint border to catch the edge like real glass would, and a
 * tilt-reactive specular highlight. [content] still owns its own padding —
 * this only draws the surface itself.
 */
@Composable
fun GlassSurface(
    shape: Shape = RoundedCornerShape(GlassRadii.card),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hazeState = LocalHazeState.current
    val palette = LocalGlassPalette.current
    Box(
        modifier
            .clip(shape)
            .let { base ->
                if (hazeState != null) {
                    base.hazeChild(
                        state = hazeState,
                        shape = shape,
                        style = HazeDefaults.style(
                            backgroundColor = palette.cardTint,
                            tint = palette.cardTint,
                            blurRadius = palette.cardBackdropBlurRadius,
                            noiseFactor = palette.noiseFactor
                        )
                    )
                } else {
                    base.background(palette.cardTint)
                }
            }
            .border(1.dp, palette.borderColor, shape)
    ) {
        SpecularOverlay(Modifier.matchParentSize())
        content()
    }
}

/**
 * A soft light gradient that shifts slightly with the same tilt reading that
 * drives the fold effect (FoldEchoState.tiltUpDeg/tiltRightDeg), so glass
 * surfaces read as reactive rather than a static image. Movement is small
 * and spring-eased — meant to be felt more than consciously noticed.
 */
@Composable
private fun SpecularOverlay(modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    val tiltUp by FoldEchoState.tiltUpDeg.collectAsState()
    val tiltRight by FoldEchoState.tiltRightDeg.collectAsState()

    val fracX by animateFloatAsState(
        targetValue = (tiltRight / 45f).coerceIn(-1f, 1f) * 0.18f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "specularX"
    )
    val fracY by animateFloatAsState(
        targetValue = (-tiltUp / 45f).coerceIn(-1f, 1f) * 0.18f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "specularY"
    )

    Box(
        modifier.drawWithCache {
            val brush = Brush.radialGradient(
                colors = listOf(palette.specularColor.copy(alpha = palette.specularAlpha), Color.Transparent),
                center = Offset(size.width * (0.5f + fracX), size.height * (0.35f + fracY)),
                radius = size.maxDimension * 0.7f
            )
            onDrawBehind { drawRect(brush) }
        }
    )
}

/**
 * Slow, continuous drift behind the whole screen so the backdrop blur on
 * glass cards always has visible motion to blur, not just when the phone is
 * tilted. Independent of the tilt sensor entirely — this loops forever.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    val transition = rememberInfiniteTransition(label = "ambient")
    val shapes = palette.ambientColors.mapIndexed { index, color ->
        val period = 14_000 + index * 3_000
        val dx by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(period, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambientDx$index"
        )
        val dy by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(period + 2_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambientDy$index"
        )
        Triple(color, dx, dy)
    }

    Box(modifier.fillMaxSize()) {
        shapes.forEachIndexed { index, (color, dx, dy) ->
            val baseX = if (index % 2 == 0) 0.15f else 0.75f
            val baseY = if (index < 2) 0.2f else 0.75f
            Box(
                Modifier
                    .size(220.dp)
                    .offset(
                        x = (baseX * 300 + dx * 60).dp,
                        y = (baseY * 500 + dy * 60).dp
                    )
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.35f))
                    .blur(90.dp)
            )
        }
    }
}

/** A spring-based ~0.95x press-down, for elements that build their own click handling (so they can share one [InteractionSource] between the scale and the actual click). */
@Composable
fun rememberPressScale(interactionSource: InteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return scale
}
