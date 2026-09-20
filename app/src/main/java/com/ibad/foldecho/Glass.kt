package com.ibad.foldecho

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
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
/**
 * One radial blob of [MeshGradientBackground]. Position and radius are
 * *fractions*, not absolute pixels, so one palette describes the same
 * composition on any screen size: [centerX]/[centerY] are fractions of the
 * layer's width/height (values slightly outside 0..1 are deliberate — a blob
 * anchored just off-screen contributes only its soft outer falloff), and
 * [radiusFraction] is a fraction of the layer's longest side.
 *
 * [color]'s own alpha is the blob's peak opacity at its centre; the falloff to
 * fully transparent is applied on top of it.
 */
data class MeshBlob(
    val centerX: Float,
    val centerY: Float,
    val radiusFraction: Float,
    val color: Color
)

data class GlassPalette(
    val cardTint: Color,
    val cardBackdropBlurRadius: Dp,
    val lensRefractionHeight: Dp,
    val lensRefractionAmount: Dp,
    val specularColor: Color,
    val specularAlpha: Float,
    val highlightWidth: Dp,
    /** Opaque base [MeshGradientBackground] paints before any blob. Also what the window's `ColorDrawable` and `MaterialTheme.colorScheme.background` are pinned to, so there is one true app background per theme and no seam at the system bars. */
    val backgroundBase: Color,
    /** The blobs composited over [backgroundBase], in draw order. Fixed data — there is nothing here for an animation or a sensor to drive. */
    val meshBlobs: List<MeshBlob>,
    /** The thin uniform rim around every [GlassSurface]. Semi-transparent by design: an opaque stroke reads as a sticker outline, not as the lit edge of a pane. */
    val borderColor: Color,
    val borderWidth: Dp,
    /** [GlassSwitch]'s "on" fill — Apple's systemGreen, the real iOS UISwitch colour. */
    val switchOnTint: Color,
    /** [GlassSwitch]'s "off" fill — a neutral overlay so the off track isn't invisible against a varying backdrop. Not an Apple-sourced value; a reasoned approximation. */
    val switchOffTint: Color
)

/**
 * Dark glass over a near-black vertical gradient.
 *
 * `cardBackdropBlurRadius` and `cardTint` were both pinned to *diagnostic*
 * values (3dp / 0.15 alpha) while the backdrop was a 12dp-pitch micro-dot
 * matrix: any blur radius near that pitch averaged the dots into a flat wash
 * instead of softening them, so the radius had to stay well under it and the
 * tint had to stay low to keep any texture visible at all. That constraint is
 * gone with the dot grid: a smooth gradient has no fine pattern to destroy,
 * so the radius can go back up to a frosted value and the tint back up to
 * where a card reads as a real, legible surface rather than a barely-there
 * film. That legibility is the point — text over a 0.15-alpha card was the
 * "barely readable" complaint.
 */
val DarkGlassPalette = GlassPalette(
    cardTint = Color(0xFF0B0D14).copy(alpha = 0.30f),
    cardBackdropBlurRadius = 10.dp,
    lensRefractionHeight = 9.dp,
    lensRefractionAmount = 16.dp,
    specularColor = Color.White,
    specularAlpha = 0.55f,
    highlightWidth = 0.75.dp,
    backgroundBase = Color(0xFF05060A),
    // Vivid hues at moderate opacity, which is what reads as colour against a
    // near-black base — the same hex at a light-mode alpha would disappear.
    meshBlobs = listOf(
        MeshBlob(0.12f, 0.08f, 0.85f, Color(0xFF2E5FE8).copy(alpha = 0.50f)), // blue
        MeshBlob(0.92f, 0.18f, 0.80f, Color(0xFF7B3FE4).copy(alpha = 0.46f)), // purple
        MeshBlob(0.05f, 0.58f, 0.70f, Color(0xFF0E9B8A).copy(alpha = 0.38f)), // teal
        MeshBlob(0.88f, 0.72f, 0.78f, Color(0xFFD8417E).copy(alpha = 0.40f)), // pink
        MeshBlob(0.50f, 1.02f, 0.75f, Color(0xFF3D3BC4).copy(alpha = 0.30f))  // indigo, anchored just off the bottom edge
    ),
    borderColor = Color.White.copy(alpha = 0.20f),
    borderWidth = 0.75.dp,
    switchOnTint = Color(0xFF30D158).copy(alpha = 0.92f),
    switchOffTint = Color.White.copy(alpha = 0.14f)
)

/**
 * Light counterpart. Apple's own grouped-content convention: the page is the
 * grey (`systemGroupedBackground`, #F2F2F7) and the cards are the white, not
 * the other way round — which is also what makes near-black label text
 * readable on them. See [DarkGlassPalette] for why these values are no longer
 * the low diagnostic ones.
 */
val LightGlassPalette = GlassPalette(
    cardTint = Color.White.copy(alpha = 0.40f),
    cardBackdropBlurRadius = 10.dp,
    lensRefractionHeight = 8.dp,
    lensRefractionAmount = 14.dp,
    specularColor = Color.White,
    specularAlpha = 0.75f,
    highlightWidth = 0.75.dp,
    backgroundBase = Color(0xFFFBF8F3),
    // Deliberately *not* the dark palette's blobs re-alpha'd: the composition
    // is mirrored (blue moves to the top-right, purple to the top-left) and
    // each hue is lightened before the alpha drop, so these read as tinted
    // light falling across an off-white page rather than as colour patches.
    meshBlobs = listOf(
        MeshBlob(0.85f, 0.06f, 0.80f, Color(0xFF4C7DF0).copy(alpha = 0.20f)), // blue
        MeshBlob(0.10f, 0.20f, 0.75f, Color(0xFF8A5CF0).copy(alpha = 0.18f)), // purple
        MeshBlob(0.92f, 0.55f, 0.72f, Color(0xFF2FB3A3).copy(alpha = 0.15f)), // teal
        MeshBlob(0.18f, 0.82f, 0.80f, Color(0xFFEC6A9E).copy(alpha = 0.17f)), // pink
        MeshBlob(0.55f, 1.05f, 0.75f, Color(0xFF6366F1).copy(alpha = 0.12f))  // indigo, anchored just off the bottom edge
    ),
    // Light grey rather than white: a white rim is invisible against the
    // off-white base between blobs, which is most of a light-mode screen.
    borderColor = Color(0xFF9CA3AF).copy(alpha = 0.38f),
    borderWidth = 0.75.dp,
    switchOnTint = Color(0xFF34C759).copy(alpha = 0.92f),
    switchOffTint = Color.Black.copy(alpha = 0.10f)
)

val LocalGlassPalette = compositionLocalOf { DarkGlassPalette }

/**
 * Shared across the whole screen: one captured backdrop layer (the background),
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
 * ### Why [refractive] exists
 *
 * Every `drawBackdrop` call is its own offscreen layer plus an AGSL shader
 * pass per frame. That is affordable for the handful of cards it was designed
 * for; it is not affordable once every switch track and every slider track is
 * also one, which is what the previous change made them — roughly thirty
 * shader-backed surfaces on one scrolling screen, and the direct cause of the
 * "laggy/sluggish overall" report. Small controls therefore pass
 * `refractive = false` and get blur only: one pass instead of two, and no lens
 * distortion, which at a 28–31dp control height was sub-pixel anyway. They
 * still sample the real backdrop, so the "same blur thingy" the controls were
 * asked for is intact — it is the part that was costing frames without being
 * visible that is gone.
 *
 * Two more effects were dropped outright for the same reason:
 * - `vibrancy()`, a full saturation pass over the sampled layer — least
 *   visible of the three, and now pointless over a near-neutral gradient.
 * - `chromaticAberration`, which triples the lens shader's per-pixel sample
 *   count. It existed specifically so the dot grid's accent dots would split
 *   into colour at the refraction rim; with the grid gone there is no chroma
 *   in the backdrop left for it to separate.
 */
@Composable
fun GlassSurface(
    shape: Shape = RoundedCornerShape(GlassRadii.card),
    modifier: Modifier = Modifier,
    refractive: Boolean = true,
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
                    blur(palette.cardBackdropBlurRadius.toPx())
                    if (refractive) {
                        lens(
                            refractionHeight = palette.lensRefractionHeight.toPx(),
                            refractionAmount = palette.lensRefractionAmount.toPx(),
                            chromaticAberration = false
                        )
                    }
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
            // Before .clip, not after: `border` strokes centred on the shape's
            // outline, so a clip *preceding* it would swallow the outer half
            // and render a 0.75dp rim as an uneven ~0.4dp one. Here the border
            // node sits outside the clip node, draws its content first and the
            // stroke over the top, and keeps its full declared width.
            .border(palette.borderWidth, palette.borderColor, shape)
            .clip(shape)
    } else {
        Modifier
            .border(palette.borderWidth, palette.borderColor, shape)
            .clip(shape)
            .background(palette.cardTint)
    }

    // key(palette), and not a plain recomposition, is what makes a theme
    // switch repaint the glass *immediately* rather than on the next touch.
    // DrawBackdropNode declares `shouldAutoInvalidate = false`, and its
    // element's `update()` only calls `invalidateDrawCache()` — which
    // recomputes the RenderEffect but never calls `invalidateDraw()`
    // (verified by reading the library's own DrawBackdropModifier.kt, not
    // assumed). So a card whose tint/blur parameters changed keeps replaying
    // its previously recorded draw until something unrelated invalidates the
    // layer — which on a real device is the user's next touch. That is
    // exactly the reported "boxes don't change colour until I interact with
    // the screen". Re-keying forces `create()` instead of `update()`, and a
    // brand-new node has nothing stale to replay.
    //
    // Cost, stated rather than hidden: this disposes and recreates [content]'s
    // subtree, so any `rememberSaveable` inside a glass card resets on a theme
    // change — in this app that means expanded "Advanced" sections collapse.
    // A rare, deliberate button press trading for a correct repaint.
    key(palette) {
        Box(glass.then(modifier)) {
            content()
        }
    }
}

/**
 * iOS-style pill switch: a [GlassSurface] track — so it gets the same backdrop
 * sampling and highlight the cards get, not a flat Material `Switch` — a white
 * circular thumb, and an animated tint that shifts between
 * [GlassPalette.switchOffTint] and [GlassPalette.switchOnTint] on check.
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
        refractive = false,
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
        // offset, not padding: `thumbOffset` is driven by a *bouncy* spring,
        // which by definition overshoots its target on the way in. Toggling
        // on overshoots past 22dp, which is harmless; toggling off overshoots
        // past the 2dp inset and goes briefly negative — and
        // Modifier.padding throws IllegalArgumentException on a negative Dp,
        // which is precisely why the app crashed on switching any toggle
        // *off* and not on. Modifier.offset accepts negative values by
        // design, so the bounce renders as the intended slight overshoot
        // instead of a crash.
        Box(
            Modifier
                .offset(x = thumbOffset, y = SWITCH_THUMB_INSET)
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
 * fill up to the current value and a glassy remainder, plus a plain white
 * circular thumb with a drop shadow.
 *
 * Corrected once already: the first version of this composable was built
 * against `Slider(state=, thumb=, track=)` from androidx's `androidx-main`
 * dev branch (fetched live, but that branch is ahead of any tagged
 * release) and against a `rememberSliderState(trackRange=)` that doesn't
 * exist in this project's actual pinned material3 (1.4.0, per compose-bom
 * 2025.12.01). Both assumptions were wrong and CI caught it immediately —
 * a real compiler error, not a guess, is what this version is built from.
 * The stable 1.4.0 `Slider` has a third overload the dev-branch source
 * didn't show me: `value=`/`onValueChange=` *combined* with `thumb=`/
 * `track=` slots, still fully caller-controlled. That removes the whole
 * problem the first version's `LaunchedEffect` sync existed for — there's
 * no separate remembered `SliderState` to fall out of sync with `value` in
 * the first place, since there isn't one; `thumb`/`track` are still handed
 * a `SliderState` per call for reading `coercedValueAsFraction`, just one
 * the framework owns internally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        thumb = { GlassSliderThumb(enabled = enabled) },
        track = { state -> GlassSliderTrack(state = state, enabled = enabled) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassSliderTrack(state: SliderState, enabled: Boolean) {
    val fillColor = MaterialTheme.colorScheme.primary
    val fraction = state.coercedValueAsFraction

    GlassSurface(
        shape = CircleShape,
        // Blur only — see GlassSurface's doc comment. A dragging slider
        // recomposes its track every frame, so this is the single worst place
        // in the app to run a lens pass.
        refractive = false,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLIDER_TRACK_HEIGHT)
            .alpha(if (enabled) 1f else 0.4f)
    ) {
        // Fully opaque, so it completely covers the glass beneath it in the
        // filled region — that's what makes the fill read as solid colour
        // while the remainder stays glassy.
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
 * The backdrop every glass card samples: an opaque base plus four or five soft,
 * overlapping radial blobs — a mesh gradient — and nothing else.
 *
 * It is deliberately motionless. There is no animation driving it, no sensor
 * read anywhere in this file, and no scroll value reaching it: the blob set
 * comes straight out of [GlassPalette.meshBlobs], which is fixed data resolved
 * once per theme. The glass effect is demonstrated by the cards moving over
 * this field as the panel scrolls, so the field itself staying put is the
 * whole point — it is the fixed reference the refraction distorts against.
 *
 * Drawn as a sibling *behind* the scrolling content, never inside the scroll
 * container, so scrolling moves the cards across it rather than dragging it
 * along with them. This is the same structural rule the dot grid and the plain
 * gradient before it were held to.
 *
 * Cost is five full-screen `drawRect`s with a radial shader each, against the
 * ~1700 `drawCircle` calls the dot-grid version needed. It also lands in the
 * captured backdrop layer, which only re-records when something invalidates
 * it, so it is not paid again per scroll frame.
 *
 * This paints its own opaque base — [GlassPalette.backgroundBase], not
 * MaterialTheme's background — rather than letting the Surface behind it show
 * through: the backdrop layer captures only what this composable draws, and a
 * transparent capture would leave the glass sampling nothing.
 */
@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier) {
    val palette = LocalGlassPalette.current

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(palette.backgroundBase)

                // Radius is a fraction of the *longest* side so a blob keeps
                // its shape relative to the screen rather than stretching with
                // the aspect ratio.
                val longestSide = size.maxDimension

                palette.meshBlobs.forEach { blob ->
                    val peak = blob.color
                    drawRect(
                        brush = Brush.radialGradient(
                            // Four stops, not two: a straight colour-to-
                            // transparent ramp falls off linearly and leaves a
                            // visible disc edge. Front-loading the decay keeps
                            // the centre solid and lets the rim vanish.
                            0f to peak,
                            0.35f to peak.copy(alpha = peak.alpha * 0.62f),
                            0.70f to peak.copy(alpha = peak.alpha * 0.22f),
                            1f to Color.Transparent,
                            center = Offset(
                                x = size.width * blob.centerX,
                                y = size.height * blob.centerY
                            ),
                            radius = longestSide * blob.radiusFraction
                        )
                    )
                }
            }
    )
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
