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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.HighlightStyle
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val lensRefractionHeight: Dp,
    val lensRefractionAmount: Dp,
    val borderColor: Color,
    val specularColor: Color,
    val specularAlpha: Float,
    val highlightWidth: Dp,
    val dotColor: Color
)

/**
 * Dark glass: a dark pane over a dark field of light dots.
 *
 * The tint is kept well back (0.30) and the blur deliberately small. Both are
 * down sharply from the values this used to carry, because the point of the
 * glass now is that you can *see the dot grid bend* through it — a heavy blur
 * dissolves the dots into a flat wash and there is nothing left to refract.
 * The lens does the work instead; the blur only takes the hard edge off.
 */
val DarkGlassPalette = GlassPalette(
    cardTint = Color(0xFF14161C).copy(alpha = 0.30f),
    cardBackdropBlurRadius = 5.dp,
    lensRefractionHeight = 18.dp,
    lensRefractionAmount = 32.dp,
    borderColor = Color.White.copy(alpha = 0.14f),
    specularColor = Color.White,
    specularAlpha = 0.55f,
    highlightWidth = 0.75.dp,
    // Light dots on the dark background — the inverse of the light palette's
    // pairing, not the same colour at a different opacity.
    dotColor = Color(0xFFDCE4F5).copy(alpha = 0.30f)
)

/**
 * Light glass: a thinner, whiter pane over a light field of dark dots.
 *
 * Tint sits back further still (0.22 vs dark's 0.30) and the blur is tighter,
 * because a bright backdrop needs less help to stay legible — and the specular
 * has to be far stronger (0.75 vs 0.55) to register against it at all. Those
 * are the axes that have to differ for the two themes to read as different
 * materials rather than one palette with the lights turned up.
 */
val LightGlassPalette = GlassPalette(
    cardTint = Color.White.copy(alpha = 0.22f),
    cardBackdropBlurRadius = 4.dp,
    lensRefractionHeight = 16.dp,
    lensRefractionAmount = 28.dp,
    borderColor = Color.White.copy(alpha = 0.70f),
    specularColor = Color.White,
    specularAlpha = 0.75f,
    highlightWidth = 0.75.dp,
    // Dark dots on the light background.
    dotColor = Color(0xFF14181F).copy(alpha = 0.28f)
)

val LocalGlassPalette = compositionLocalOf { DarkGlassPalette }

/**
 * Shared across the whole screen: one captured backdrop layer (the dot grid),
 * many glass children sampling it. Held as a composition local so every card
 * samples the same capture — see the note on GlassScene about why this must be
 * remembered once at the scaffold rather than created per card.
 */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/** 45° is the library's own rest angle for HighlightStyle.Default. */
private const val REST_LIGHT_ANGLE_DEG = 45f

/** Tilt (in degrees) that counts as a full-scale lean for the highlight. */
private const val TILT_FULL_SCALE_DEG = 45f

/** How far a full-scale lean pulls the light away from its rest direction. */
private const val TILT_LIGHT_GAIN = 0.9f

// Not `const`: a const initializer has to be a compile-time constant, and
// `.toFloat()` is a call.
private val DEG_TO_RAD = (PI / 180.0).toFloat()

/**
 * The reusable "pane of glass": real backdrop refraction of whatever is behind
 * it, plus a tilt-reactive specular highlight. [content] still owns its own
 * padding — this only draws the surface itself.
 *
 * Effects run in the order the library documents as the iOS-style stack:
 * saturate, soften, then bend. `lens` is what actually distorts the dot grid;
 * `blur` alone would only fog it.
 */
@Composable
fun GlassSurface(
    shape: Shape = RoundedCornerShape(GlassRadii.card),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backdrop = LocalBackdrop.current
    val palette = LocalGlassPalette.current
    val highlightAngle = rememberHighlightAngle()

    val glass = if (backdrop != null) {
        Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(palette.cardBackdropBlurRadius.toPx())
                    lens(
                        refractionHeight = palette.lensRefractionHeight.toPx(),
                        refractionAmount = palette.lensRefractionAmount.toPx()
                    )
                },
                // Read inside the lambda on purpose: the library re-evaluates
                // these per frame, so the angle tracks the sensor without
                // rebuilding the modifier chain on every tilt reading.
                highlight = {
                    Highlight(
                        width = palette.highlightWidth,
                        alpha = palette.specularAlpha,
                        style = HighlightStyle.Default(
                            color = palette.specularColor,
                            angle = highlightAngle
                        )
                    )
                },
                // How the library wants glass tinted: over the effects, under
                // the children, so text stays at full contrast.
                onDrawSurface = { drawRect(palette.cardTint) }
            )
            .clip(shape)
    } else {
        Modifier
            .clip(shape)
            .background(palette.cardTint)
    }

    Box(
        glass
            .then(modifier)
            .border(1.dp, palette.borderColor, shape)
    ) {
        content()
    }
}

/**
 * Maps the same tilt reading that drives the fold effect
 * (FoldEchoState.tiltUpDeg/tiltRightDeg) onto the light-source angle of every
 * card's highlight, so leaning the phone swings the sheen around the glass.
 *
 * The lean is sprung as a *vector* and only then converted to an angle.
 * Springing the angle itself would take the long way round whenever it wrapped
 * past ±180°, sending the highlight spinning all the way around the card on
 * what was physically a tiny movement. Adding a fixed rest vector at
 * [REST_LIGHT_ANGLE_DEG] also keeps a still phone stable: without it, a lean of
 * almost exactly zero leaves the angle at the mercy of sensor noise.
 */
@Composable
private fun rememberHighlightAngle(): Float {
    val tiltUp by FoldEchoState.tiltUpDeg.collectAsState()
    val tiltRight by FoldEchoState.tiltRightDeg.collectAsState()

    val leanX by animateFloatAsState(
        targetValue = (tiltRight / TILT_FULL_SCALE_DEG).coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "highlightLeanX"
    )
    val leanY by animateFloatAsState(
        targetValue = (-tiltUp / TILT_FULL_SCALE_DEG).coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "highlightLeanY"
    )

    val restRadians = REST_LIGHT_ANGLE_DEG * DEG_TO_RAD
    val x = cos(restRadians) + leanX * TILT_LIGHT_GAIN
    val y = sin(restRadians) + leanY * TILT_LIGHT_GAIN
    return atan2(y, x) / DEG_TO_RAD
}

/**
 * The backdrop every glass card samples: a field of small dots over the theme
 * background, drifting continuously so there is always motion for the glass to
 * refract even when the phone is perfectly still.
 *
 * The drift runs 0..1 over exactly one cell and restarts, so the field wraps
 * seamlessly and can loop forever with no visible jump. A row and column of
 * dots beyond each edge cover what the offset pulls in.
 *
 * This paints its own opaque background rather than letting the Surface behind
 * it show through: the backdrop layer captures only what this composable
 * draws, and a transparent capture would leave the glass sampling nothing.
 */
@Composable
fun DotGridBackground(modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current
    val baseColor = MaterialTheme.colorScheme.background
    val transition = rememberInfiniteTransition(label = "dotGrid")

    val driftX by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(19_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotDriftX"
    )
    val driftY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Deliberately not a multiple of the X period, so the field never
            // settles into an obvious repeating diagonal.
            animation = tween(27_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotDriftY"
    )

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(baseColor)

                val step = DOT_SPACING.toPx()
                val radius = DOT_RADIUS.toPx()
                val offsetX = driftX * step
                val offsetY = driftY * step
                val columns = (size.width / step).toInt() + 2
                val rows = (size.height / step).toInt() + 2

                for (column in 0..columns) {
                    val x = column * step - step + offsetX
                    for (row in 0..rows) {
                        drawCircle(
                            color = palette.dotColor,
                            radius = radius,
                            center = Offset(x, row * step - step + offsetY)
                        )
                    }
                }
            }
    )
}

private val DOT_SPACING = 22.dp
private val DOT_RADIUS = 1.6.dp

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
