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
import androidx.compose.ui.geometry.Size
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
 * Dark glass over a mesh gradient.
 *
 * `cardTint` and `cardBackdropBlurRadius` went through two rounds already:
 * raised from a 3dp/0.15-alpha diagnostic pair (tuned for a since-removed
 * dot grid) up to 10dp/0.30 for text legibility over a flat gradient, then
 * *down* again here — 7dp/0.20 — once real on-device screenshots against the
 * mesh showed the opposite problem: cards reading as flat, opaque slabs with
 * the blob colours barely diffusing through, which is the direct complaint
 * this round answers. 10dp on top of a mesh built from soft, already-
 * overlapping radial gradients was double-softening — it smeared multiple
 * blobs' colours into a grey average under a card instead of letting one or
 * two read through distinctly, and 0.30 tint was covering another third of
 * whatever survived that. Legibility is now carried by the raised text
 * alphas from the previous round instead of a heavy tint, and by
 * [GlassSurface]'s restored `vibrancy()` pass on refractive surfaces.
 */
val DarkGlassPalette = GlassPalette(
    cardTint = Color(0xFF0B0D14).copy(alpha = 0.20f),
    cardBackdropBlurRadius = 7.dp,
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
    cardTint = Color.White.copy(alpha = 0.24f),
    cardBackdropBlurRadius = 7.dp,
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
    //
    // Alphas raised from an initial 0.12-0.20 pass: a real light-mode
    // screenshot showed the mesh nearly invisible even in the open
    // background, well before any card sat on top of it to dim it further —
    // "lower opacity than dark" had overshot into "not really there". Still
    // clearly lower than dark's 0.30-0.50 range, which is what keeps this
    // reading as tinted light rather than saturated colour patches.
    meshBlobs = listOf(
        MeshBlob(0.85f, 0.06f, 0.80f, Color(0xFF4C7DF0).copy(alpha = 0.32f)), // blue
        MeshBlob(0.10f, 0.20f, 0.75f, Color(0xFF8A5CF0).copy(alpha = 0.29f)), // purple
        MeshBlob(0.92f, 0.55f, 0.72f, Color(0xFF2FB3A3).copy(alpha = 0.24f)), // teal
        MeshBlob(0.18f, 0.82f, 0.80f, Color(0xFFEC6A9E).copy(alpha = 0.27f)), // pink
        MeshBlob(0.55f, 1.05f, 0.75f, Color(0xFF6366F1).copy(alpha = 0.19f))  // indigo, anchored just off the bottom edge
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
 * ### What the three flags actually cost, and what each buys
 *
 * A real, on-device GPU overdraw capture (not the raw surface count guessed
 * at previously) showed the true picture: every `GlassSurface` stacks up to
 * four translucent full-area draws (shadow, the blurred backdrop-sample
 * layer, the tint fill, the border), and small controls are *nested inside*
 * a card that already paints that same four-layer stack — so the pixels
 * under, say, the "Flip tilt direction" switch were getting drawn on the
 * order of eight to ten times a frame, not four. That is what "the whole
 * page is red" actually was: nesting depth at shared pixels, which a fix to
 * the mesh background alone (a separate, real, but much smaller contributor)
 * could not touch. It is also most of the scroll smoothness cost: the
 * backdrop library's `LayerBackdrop.isCoordinatesDependent = true` means
 * every one of those stacked surfaces re-samples and re-runs its RenderEffect
 * on every frame a card's on-screen position changes, i.e. every scroll
 * frame — confirmed by reading the library's own `DrawBackdropModifier.kt`.
 * More stacked surfaces means more of that cost paid per frame while
 * scrolling, not just more static overdraw.
 *
 * [refractive] (default on) gates `vibrancy()` and `lens()` — the two
 * effects that make a card read as colour-saturated, bending glass rather
 * than a plain frosted pane. Off for anything that does not need to visibly
 * refract: a 28–31dp control's lens distortion was sub-pixel anyway.
 *
 * [elevated] (default on) gates `shadow` and `highlight` — the drop shadow
 * and specular rim that make a card read as raised off the page. Both are
 * real, separate modifier nodes and real, separate draws. A control nested
 * inside an already-shadowed, already-rimmed card does not need its own
 * copy of either, so small controls turn this off — two fewer stacked
 * layers at exactly the pixels that were reading deepest red.
 *
 * [flat] (default off) skips the backdrop pipeline entirely — no blur, no
 * shadow, no highlight, just the tint and border every surface gets either
 * way. [GlassSwitch] uses this: its track's own sampled colour is almost
 * entirely covered by the near-opaque [GlassPalette.switchOnTint]/
 * `switchOffTint` overlay drawn on top of it regardless, so an independent
 * real backdrop sample there was paying full shader cost for something the
 * next draw call mostly hides. [GlassSlider]'s track deliberately does *not*
 * use this — its unfilled portion is the whole point of "a glassy
 * remainder" from the original reference, so it keeps [refractive] off and
 * [elevated] off, but stays real glass.
 *
 * `chromaticAberration` stays off everywhere: it triples the lens shader's
 * per-pixel sample count, and it existed for the old dot grid's accent dots
 * to split into colour at the refraction rim. Worth reconsidering later if
 * the mesh's colour needs to visibly fringe at a card's edge, not turned on
 * speculatively here.
 */
@Composable
fun GlassSurface(
    shape: Shape = RoundedCornerShape(GlassRadii.card),
    modifier: Modifier = Modifier,
    refractive: Boolean = true,
    elevated: Boolean = true,
    flat: Boolean = false,
    content: @Composable () -> Unit
) {
    val backdrop = LocalBackdrop.current
    val palette = LocalGlassPalette.current

    val glass = if (backdrop != null && !flat) {
        Modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(palette.cardBackdropBlurRadius.toPx())
                    if (refractive) {
                        vibrancy()
                        lens(
                            refractionHeight = palette.lensRefractionHeight.toPx(),
                            refractionAmount = palette.lensRefractionAmount.toPx(),
                            chromaticAberration = false
                        )
                    }
                },
                // Static: a fixed angle, and colour/alpha that vary only by
                // theme. Nothing in here changes once the theme is resolved.
                highlight = if (elevated) {
                    {
                        Highlight(
                            width = palette.highlightWidth,
                            alpha = palette.specularAlpha,
                            style = HighlightStyle.Default(
                                color = palette.specularColor,
                                angle = HIGHLIGHT_ANGLE_DEG
                            )
                        )
                    }
                } else null,
                shadow = if (elevated) {
                    { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.15f)) }
                } else null,
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
        // Flat, not just non-refractive: this track's own sampled colour is
        // almost entirely covered by trackTint (0.92 alpha when on, drawn
        // right below) regardless of what GlassSurface would have shown, so
        // an independent real backdrop sample here was full shader cost for
        // something the very next draw call mostly hides. See GlassSurface's
        // doc comment for the overdraw math this answers.
        flat = true,
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
        // Real glass stays on: the unfilled remainder showing genuine
        // refraction is the whole point of this control, per the original
        // reference. refractive off (no lens — a dragging track recomposes
        // every frame, the single worst place to run that shader) and
        // elevated off (no shadow/highlight — redundant this deep inside an
        // already-shadowed card) are what it sheds instead. See
        // GlassSurface's doc comment for why that split, not "flat", is
        // correct here.
        refractive = false,
        elevated = false,
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
 * Cost is one full-screen opaque `drawRect` plus five radial-gradient
 * `drawRect`s clipped to each blob's own bounding square — not full-screen —
 * against the ~1700 `drawCircle` calls the dot-grid version needed. That clip
 * is deliberate, not incidental: an earlier version painted every blob across
 * the entire canvas regardless of how little of it the gradient actually
 * reached, which an on-device GPU overdraw capture showed as 4x+ (red) across
 * the whole screen. It also lands in the captured backdrop layer, which only
 * re-records when something invalidates it, so none of this is paid again
 * per scroll frame.
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
                    val center = Offset(
                        x = size.width * blob.centerX,
                        y = size.height * blob.centerY
                    )
                    val radius = longestSide * blob.radiusFraction

                    // Constrained to the blob's own bounding square instead of
                    // the old fillMaxSize() drawRect. The shader's centre/
                    // radius above are in absolute canvas coordinates, so this
                    // only crops which pixels get painted — identical output —
                    // but it is what took a GPU overdraw capture from red
                    // (4x+) across the whole screen to roughly 1x outside the
                    // blob overlaps: previously every one of the 5 blobs
                    // painted the *entire* screen, alpha-blending a fully
                    // transparent outer ring over areas the gradient never
                    // visibly reaches at all.
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
                            center = center,
                            radius = radius
                        ),
                        topLeft = center - Offset(radius, radius),
                        size = Size(radius * 2f, radius * 2f)
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
