package com.ibad.foldecho

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.highlight.HighlightStyle
import com.kashif_e.backdrop.shadow.Shadow

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
 *
 * No border colour: a uniform stroke around the whole shape is what reads as
 * a flat sticker outline rather than glass. The rim is carried entirely by
 * the highlight now — see [GlassSurface].
 */
data class GlassPalette(
    val cardTint: Color,
    val cardBackdropBlurRadius: Dp,
    val lensRefractionHeight: Dp,
    val lensRefractionAmount: Dp,
    val specularColor: Color,
    val specularAlpha: Float,
    val highlightWidth: Dp,
    /** Base fill and dot colour of [DotGridBackground] — independent of MaterialTheme's background, per an exact design spec. */
    val dotGridBackground: Color,
    val dotColor: Color,
    /** Colour(s) of every 4th dot, cycled in order. One colour for a single accent; several to alternate between them. */
    val dotAccentColors: List<Color>,
    /** [GlassSwitch]'s "on" fill — Apple's systemGreen, the real iOS UISwitch colour. */
    val switchOnTint: Color,
    /** [GlassSwitch]'s "off" fill — a neutral overlay so the off track isn't invisible against a varying backdrop. Not an Apple-sourced value; a reasoned approximation. */
    val switchOffTint: Color
)

/**
 * Dark glass over a near-black micro-dot matrix.
 *
 * `cardBackdropBlurRadius` and `cardTint` alpha are both diagnostic values
 * right now, not final ones. The previous version of this palette (22dp
 * blur, 0.30 tint) shipped untested against a real device and turned out
 * to render every card as a flat, opaque rectangle — no visible dot-grid
 * texture at all. The cause: a 22dp blur radius against this grid's 12dp
 * dot pitch is larger than the pattern's own period, so it doesn't soften
 * the dots, it averages them into a spatially near-uniform wash before
 * `onDrawSurface`'s tint is even composited on top. 3dp keeps the blur
 * diameter well under the 12dp pitch so individual dots survive as soft,
 * distinct blobs; the tint is halved alongside it as a joint diagnostic
 * step, since blur homogenization would make any tint on top look equally
 * flat and a real device round-trip is expensive to spend confirming which
 * one mattered. Once a screenshot confirms texture is visibly present
 * again, both are expected to move back up toward a more frosted target —
 * this is deliberately the conservative end of the range, not the goal.
 */
val DarkGlassPalette = GlassPalette(
    cardTint = Color(0xFF14161C).copy(alpha = 0.15f),
    cardBackdropBlurRadius = 3.dp,
    lensRefractionHeight = 9.dp,
    lensRefractionAmount = 16.dp,
    specularColor = Color.White,
    specularAlpha = 0.55f,
    highlightWidth = 0.75.dp,
    dotGridBackground = Color(0xFF090A0F),
    dotColor = Color(0xFFE2E8F0).copy(alpha = 0.75f),
    dotAccentColors = listOf(Color(0xFF38BDF8), Color(0xFFF59E0B)),
    switchOnTint = Color(0xFF30D158).copy(alpha = 0.92f),
    switchOffTint = Color.White.copy(alpha = 0.14f)
)

/**
 * Light glass over a pure-white micro-dot matrix. Same diagnostic
 * blur/tint reasoning as dark — see its doc comment — scaled down slightly
 * further (2.5dp vs 3dp) since a bright backdrop needs even less blur to
 * stay legible.
 */
val LightGlassPalette = GlassPalette(
    cardTint = Color.White.copy(alpha = 0.11f),
    cardBackdropBlurRadius = 2.5.dp,
    lensRefractionHeight = 8.dp,
    lensRefractionAmount = 14.dp,
    specularColor = Color.White,
    specularAlpha = 0.75f,
    highlightWidth = 0.75.dp,
    dotGridBackground = Color(0xFFFFFFFF),
    dotColor = Color(0xFF0F172A),
    dotAccentColors = listOf(Color(0xFFEF4444)),
    switchOnTint = Color(0xFF34C759).copy(alpha = 0.92f),
    switchOffTint = Color.Black.copy(alpha = 0.06f)
)

val LocalGlassPalette = compositionLocalOf { DarkGlassPalette }

/**
 * Shared across the whole screen: one captured backdrop layer (the dot grid),
 * many glass children sampling it. Held as a composition local so every card
 * samples the same capture — see the note on GlassScene about why this must be
 * remembered once at the scaffold rather than created per card.
 */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * The one light-source angle every card's highlight uses, forever. 45° is the
 * library's own default for HighlightStyle.Default.
 *
 * Deliberately a constant and not derived from anything: the highlight must
 * look identical at all times and must not respond to device orientation, so
 * there is nothing here to read a sensor from.
 */
private const val HIGHLIGHT_ANGLE_DEG = 45f

/**
 * The reusable "pane of glass": real backdrop refraction of whatever is behind
 * it, plus a fixed specular highlight. [content] still owns its own padding —
 * this only draws the surface itself.
 *
 * Effects run in the order the library documents as the iOS-style stack:
 * saturate, soften, then bend. `lens` is what actually distorts the dot grid;
 * `blur` alone would only fog it. The shadow is what separates a card from
 * whatever sits below it — without it, glass reads as a flat tinted layer
 * rather than something elevated.
 */
@Composable
fun GlassSurface(
    shape: Shape = RoundedCornerShape(GlassRadii.card),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backdrop = LocalBackdrop.current
    val palette = LocalGlassPalette.current

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
                        refractionAmount = palette.lensRefractionAmount.toPx(),
                        // The accent dots exist specifically to make this
                        // visible at the refraction rim. Not verified on a
                        // real scrolling frame — if it reads as janky on
                        // device, this is the first thing to drop.
                        chromaticAberration = true
                    )
                },
                // Static: a fixed angle, and colour/alpha that vary only by
                // theme. Nothing in here changes once the theme is resolved.
                highlight = {
                    Highlight(
                        width = palette.highlightWidth,
                        alpha = palette.specularAlpha,
                        style = HighlightStyle.Default(
                            color = palette.specularColor,
                            angle = HIGHLIGHT_ANGLE_DEG
                        )
                    )
                },
                shadow = {
                    Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.15f))
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

    Box(glass.then(modifier)) {
        content()
    }
}

/**
 * iOS-style pill switch: a real [GlassSurface] track — so it gets the same
 * backdrop refraction/highlight every card gets, not a flat Material
 * `Switch` — a white circular thumb, and an animated tint that shifts
 * between [GlassPalette.switchOffTint] and [GlassPalette.switchOnTint] on
 * check.
 *
 * Built on [Modifier.toggleable] rather than Material3's `Switch`: `Switch`
 * exposes no swappable track slot, only colour tinting, and this needs the
 * track to be a real glass surface. `toggleable` gives the same
 * `Role.Switch` accessibility semantics and click target around the fully
 * custom-drawn shape. Fires the platform toggle haptic itself via
 * [rememberToggleHaptic] — callers only ever wire `checked`/`onCheckedChange`.
 *
 * Track/thumb sizing (51×31 track, 27dp thumb, 2dp inset) matches iOS's own
 * `UISwitch` intrinsic size.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val palette = LocalGlassPalette.current
    val toggleHaptic = rememberToggleHaptic()
    val interactionSource = remember { MutableInteractionSource() }

    val trackTint by animateColorAsState(
        targetValue = if (checked) palette.switchOnTint else palette.switchOffTint,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "glassSwitchTrackTint"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            SWITCH_TRACK_WIDTH - SWITCH_THUMB_SIZE - SWITCH_THUMB_INSET
        } else {
            SWITCH_THUMB_INSET
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "glassSwitchThumbOffset"
    )

    GlassSurface(
        shape = CircleShape,
        modifier = modifier
            .size(width = SWITCH_TRACK_WIDTH, height = SWITCH_TRACK_HEIGHT)
            .alpha(if (enabled) 1f else 0.4f)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    toggleHaptic(it)
                    onCheckedChange(it)
                }
            )
    ) {
        // Layered on top of GlassSurface's own cardTint, under the thumb:
        // the on/off colour this control needs is specific to it, not
        // something every card should carry.
        Box(Modifier.fillMaxSize().background(trackTint))
        Box(
            Modifier
                .padding(start = thumbOffset, top = SWITCH_THUMB_INSET)
                .size(SWITCH_THUMB_SIZE)
                .shadow(elevation = 1.5.dp, shape = CircleShape, clip = false)
                .background(Color.White, CircleShape)
        )
    }
}

private val SWITCH_TRACK_WIDTH = 51.dp
private val SWITCH_TRACK_HEIGHT = 31.dp
private val SWITCH_THUMB_SIZE = 27.dp
private val SWITCH_THUMB_INSET = 2.dp

/**
 * iOS "Liquid Glass" style slider: a thick glass pill track with a solid
 * fill up to the current value and a glassy/refractive remainder, plus a
 * plain white circular thumb with a drop shadow.
 *
 * Built on Material3's non-deprecated `Slider(state=, thumb=, track=)`
 * overload rather than the simpler `Slider(value=, onValueChange=, ...)`
 * one `TuningSlider` used before — the deprecated overload has no track
 * slot to customise, only `SliderColors` tinting, which can't produce the
 * solid-fill/glassy-remainder split this needs.
 *
 * [rememberSliderState] only reads `value` on first composition (it's
 * backed by `rememberSaveable`) and does not react to the external `value`
 * parameter changing afterward. This app's "Reset to defaults" rewrites
 * `Tunables` — and therefore this composable's `value` parameter — well
 * after first composition, so without the `LaunchedEffect` below the thumb
 * would visually freeze at its pre-reset position until dragged again.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val sliderState = rememberSliderState(value = value, trackRange = valueRange)

    LaunchedEffect(value) {
        if (sliderState.value != value) {
            sliderState.value = value
        }
    }

    Slider(
        state = sliderState,
        onValueChange = {
            // Belt-and-braces: a harmless no-op if Slider's own internals
            // already wrote this, a required assignment if they didn't —
            // the exact internal contract wasn't fully pinned down against
            // the library's source, so this covers either case.
            sliderState.value = it
            onValueChange(it)
        },
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        thumb = { GlassSliderThumb(enabled = enabled) },
        track = { state -> GlassSliderTrack(state = state, enabled = enabled) }
    )
}

@Composable
private fun GlassSliderTrack(state: SliderState, enabled: Boolean) {
    val fillColor = MaterialTheme.colorScheme.primary
    val fraction = state.coercedValueAsFraction

    GlassSurface(
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLIDER_TRACK_HEIGHT)
            .alpha(if (enabled) 1f else 0.4f)
    ) {
        // Fully opaque, so it completely covers the glass beneath it in the
        // filled region — that's what makes the fill read as solid colour
        // while the remainder stays glassy and refractive.
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(fillColor)
        )
    }
}

@Composable
private fun GlassSliderThumb(enabled: Boolean) {
    // Deliberately larger than the track (32dp thumb on a 28dp track) —
    // Slider's layout measures and positions the thumb slot independently
    // of the track's own bounds, so an oversized thumb isn't clipped.
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(SLIDER_THUMB_SIZE)
            .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
            .background(Color.White, CircleShape)
    )
}

private val SLIDER_TRACK_HEIGHT = 28.dp
private val SLIDER_THUMB_SIZE = 32.dp

/**
 * The backdrop every glass card samples: a high-density micro-dot matrix —
 * 2dp dots on a 12dp grid — over its own exact background colour, with every
 * 4th node in an accent colour so the refraction rim has something with real
 * chroma to visibly separate as cards pass over it.
 *
 * It is deliberately motionless — no drift animation, nothing driven by the
 * tilt sensor. The glass effect is demonstrated by the cards moving over this
 * field as the panel scrolls, so the field itself staying put is the whole
 * point: it is the fixed reference the refraction distorts against. A
 * background that also moved would muddy that.
 *
 * Drawn as a sibling *behind* the scrolling content, never inside the scroll
 * container, so scrolling moves the cards across it rather than dragging it
 * along with them.
 *
 * This paints its own opaque base — [GlassPalette.dotGridBackground], not
 * MaterialTheme's background — rather than letting the Surface behind it show
 * through: the backdrop layer captures only what this composable draws, and a
 * transparent capture would leave the glass sampling nothing.
 */
@Composable
fun DotGridBackground(modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(palette.dotGridBackground)

                val step = DOT_SPACING.toPx()
                val radius = DOT_RADIUS.toPx()
                val columns = (size.width / step).toInt()
                val rows = (size.height / step).toInt()

                // Half a cell in from the edge, so the field is inset evenly on
                // both sides instead of clipping a row flush against one edge.
                val originX = step / 2f
                val originY = step / 2f

                // A running count over every node drawn, in the same order as
                // the loop below: every 4th one is an accent, and successive
                // accents cycle through dotAccentColors — the "alternating"
                // part when there's more than one.
                var nodeIndex = 0

                for (column in 0..columns) {
                    val x = originX + column * step
                    for (row in 0..rows) {
                        val isAccent = nodeIndex % DOT_ACCENT_EVERY == 0
                        val color = if (isAccent) {
                            val accents = palette.dotAccentColors
                            accents[(nodeIndex / DOT_ACCENT_EVERY) % accents.size]
                        } else {
                            palette.dotColor
                        }
                        drawCircle(
                            color = color,
                            radius = radius,
                            center = Offset(x, originY + row * step)
                        )
                        nodeIndex++
                    }
                }
            }
    )
}

private val DOT_SPACING = 12.dp
private val DOT_RADIUS = 2.dp
private const val DOT_ACCENT_EVERY = 4

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
