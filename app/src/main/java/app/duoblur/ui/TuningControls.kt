package app.duoblur.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.duoblur.GlassRadii
import app.duoblur.GlassSlider
import app.duoblur.GlassSurface
import app.duoblur.GlassSwitch
import app.duoblur.rememberPressScale

/** The glass shell every tuning section shares — real refraction of the gradient behind it, not a flat translucent color. */
@Composable
internal fun TuningGroup(content: @Composable () -> Unit) {
    GlassSurface(shape = RoundedCornerShape(GlassRadii.card)) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/**
 * A collapsed-by-default section for the raw parameters behind a card's
 * simple dial (or, with a custom [title], a whole card's only content, e.g.
 * "Finishing touches"). Spring-based expand/collapse via AnimatedVisibility.
 */
@Composable
internal fun AdvancedSection(title: String = "Advanced", content: @Composable () -> Unit) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    Column {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scale(pressScale)
                .clip(RoundedCornerShape(GlassRadii.control))
                .clickable(interactionSource = interactionSource, indication = null) {
                    if (uiHapticsEnabled) haptics?.interfaceTick()
                    expanded = !expanded
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = expanded,
            // Was DampingRatioLowBouncy at StiffnessLow (200f) expanding — a
            // spring that slow reads as lag rather than as easing, and every
            // frame of it re-measures the card, which re-records the glass
            // backdrop underneath. Non-bouncy at StiffnessMediumLow settles in
            // roughly a third the time and does far less work getting there.
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

/**
 * A floating "i"/"?" dot that opens its help text on a single tap, with the
 * same interface-tick haptic every slider fires on release.
 *
 * Not built on Material3's `TooltipBox`: that trigger is long-press by
 * design (the doc comment here used to say so approvingly, before this was
 * reported as a defect), and `TooltipBox` does not publicly expose a way to
 * turn that off — the one parameter that looked like it
 * (`BasicTooltipBox`'s `enableUserInput`) lives one layer down in
 * `foundation`, isn't forwarded by material3's `TooltipBox`, and doesn't
 * appear in any released material3 API surface checked for this fix, so it
 * would have been betting on an unreleased signature — exactly the mistake
 * this session already made twice with this library. `Popup` and
 * `Modifier.clickable`, used directly, sidestep the question entirely: both
 * have been stable, unchanged public API for years, so there is nothing here
 * to get wrong against a specific pinned version.
 *
 * `onDismissRequest` is `Popup`'s own long-standing behaviour, not something
 * wired up by hand — a tap outside the bubble or the system back gesture both
 * close it via [PopupProperties]'s defaults.
 */
@Composable
internal fun InfoTooltip(text: String, glyph: String = "i") {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    var expanded by remember { mutableStateOf(false) }
    // Popup's own `offset` is raw pixels, not Dp — passing a bare dp-sized
    // int there would be a near-invisible gap on any high-density screen.
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val dotSizePx = with(density) { 18.dp.roundToPx() }

    Box {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(interactionSource = interactionSource, indication = null) {
                    if (uiHapticsEnabled) haptics?.interfaceTick()
                    expanded = !expanded
                },
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
            Popup(
                // BottomCenter aligns the *popup's* bottom edge with the
                // dot's bottom edge before any offset is applied; shifting up
                // by the dot's own height plus a gap is what actually clears
                // it and puts the bubble above, not overlapping it.
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, -(dotSizePx + gapPx)),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = false,
                    // Kept, but this was never the actual bug — corrected
                    // below. Popup content is measured with AT_MOST
                    // constraints against the full screen width regardless of
                    // this flag (verified against AndroidPopup.android.kt);
                    // it only affects the window's *starting* LayoutParams,
                    // which get overridden to match the measured content size
                    // right after anyway. Left false since it's the more
                    // correct setting for a compact popup, but it fixes
                    // nothing on its own.
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        // The real bug: Text has no built-in "stay narrow"
                        // behaviour. Given up to the full screen's AT_MOST
                        // width to work with, a multi-line paragraph fills
                        // that width with one long wrapped line before
                        // breaking, instead of wrapping into a compact block
                        // — and wrapContentSize() on this Box just reports
                        // whatever size that Text decided on, which was
                        // "nearly full screen". Capping the width forces the
                        // Text to actually wrap narrow, which is what makes
                        // this a small bubble instead of a banner — and once
                        // the popup's real measured width shrinks to this,
                        // onDismissRequest's outside-tap check (which compares
                        // against the view's actual width/height) starts
                        // working too, as a direct consequence of the same
                        // fix rather than a separate one.
                        .widthIn(max = 240.dp)
                        .wrapContentSize()
                        .clip(RoundedCornerShape(GlassRadii.chip))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** A floating capsule with a spring-animated sliding indicator, instead of a full-width flat segmented row. */
@Composable
internal fun ModeSelector(usingFold: Boolean, onSelect: (Boolean) -> Unit) {
    GlassSurface(shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
        ModeSelectorContent(usingFold, onSelect)
    }
}

@Composable
private fun ModeSelectorContent(usingFold: Boolean, onSelect: (Boolean) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        val optionWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (usingFold) 0.dp else optionWidth,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "pillIndicator"
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(optionWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Row(Modifier.fillMaxWidth()) {
            ModeOption(
                label = "Ray-traced fold",
                selected = usingFold,
                modifier = Modifier.weight(1f)
            ) { onSelect(true) }
            ModeOption(
                label = "Classic (lean/scale)",
                selected = !usingFold,
                modifier = Modifier.weight(1f)
            ) { onSelect(false) }
        }
    }
}

/**
 * One segment of the Ray-traced/Classic control.
 *
 * Second bug in this control, found the same way as the first — by reading
 * the actual resolved values, not assuming a fix landed because it compiled.
 * The previous round moved the unselected label from `onSurfaceVariant` to
 * `onSurface` specifically so selected/unselected weren't two shades of the
 * same colour. That works in light mode by coincidence: `onPrimary` (the
 * selected colour, white) and `onSurface` (the unselected colour, black) are
 * genuinely different there. In dark mode they are not — `DuoBlurDarkColors`
 * sets both `onPrimary` and `onSurface` to the same pure `#FFFFFFFF`. Once
 * the pill's own contrast against the glass card was the only thing carrying
 * the distinction, and font-weight alone didn't read as strongly as intended,
 * "which one is selected" stopped being answerable in dark mode — exactly
 * the report.
 *
 * Fixed by not depending on two roles happening to differ: unselected now
 * dims `onSurface` with its own alpha (`0.62`) rather than swapping to a
 * different role. That is a real, theme-independent brightness difference
 * from the selected label's full-opacity `onPrimary` in *every* theme, not
 * one that depends on `onPrimary` and `onSurface` resolving to different
 * colours — the exact assumption that broke in dark mode. Weight still
 * reinforces it (SemiBold selected, Medium unselected), and the colour still
 * crossfades on the same spring the sliding indicator uses.
 */
@Composable
private fun ModeOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            // Dimmed, not a different role — see the doc comment above:
            // onSurface itself is the same white as onPrimary in dark mode,
            // so the distinction has to come from opacity, not hue, and has
            // to work that way in every theme rather than happening to work
            // in one of them.
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "modeOptionForeground"
    )
    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) {
                if (uiHapticsEnabled) haptics?.interfaceTick()
                onClick()
            }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = foreground,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * A tuning row: label, live readout, and — for every slider except Activation
 * threshold and Full-tilt point, which always apply — a compact switch that
 * turns this one parameter off without discarding its dialed-in value.
 * [enabled] defaults to true and [onEnabledChange] to null so those two
 * sliders can call this without opting into a switch at all.
 */
@Composable
internal fun TuningSlider(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    help: String,
    enabled: Boolean = true,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onChange: (Float) -> Unit
) {
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    val contentAlpha = if (enabled) 1f else 0.4f
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Spacer(Modifier.width(6.dp))
                InfoTooltip(help)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    readout,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha)
                )
                if (onEnabledChange != null) {
                    Spacer(Modifier.width(8.dp))
                    GlassSwitch(checked = enabled, onCheckedChange = onEnabledChange)
                }
            }
        }
        GlassSlider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = { if (uiHapticsEnabled) haptics?.interfaceTick() },
            valueRange = range,
            enabled = enabled
        )
    }
}

@Composable
internal fun TuningToggle(
    label: String,
    help: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            InfoTooltip(help)
        }
        Spacer(Modifier.width(12.dp))
        GlassSwitch(checked = checked, onCheckedChange = onChange)
    }
}
