package app.duoblur.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duoblur.GlassSurface
import app.duoblur.ThemeMode
import app.duoblur.rememberPressScale

private fun nextThemeMode(mode: ThemeMode): ThemeMode = when (mode) {
    ThemeMode.SYSTEM -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.SYSTEM
}

private fun glyphFor(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "◐" // U+25D0 half-filled circle
    ThemeMode.DARK -> "☾"   // U+263E
    ThemeMode.LIGHT -> "☀"  // U+2600
}

/**
 * Small circular glass button, leading the [ThemeToggleButton] pair in the
 * header — the performance escape hatch for [Tunables.glassEffectsEnabled].
 *
 * Deliberately no icon-morph animation the way [ThemeToggleButton] has one:
 * the glyph here is static, and the button's *own chrome* is what shows the
 * state, since this is itself a [GlassSurface] — flip the setting off and
 * this button goes flat right along with every card, switch, and slider on
 * screen, which is a more honest demonstration of "glass is off" than an
 * icon swap would be. The glyph dims to match, the same `0.4f` alpha
 * [GlassSwitch]/[GlassSlider] already use for a disabled control.
 *
 * A genuine boolean, unlike [ThemeToggleButton]'s faked one from a 3-way
 * cycle — so this fires [rememberToggleHaptic] with the real value rather
 * than a derived signal.
 */
@Composable
internal fun GlassEffectsToggleButton(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val toggleHaptic = rememberToggleHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)

    GlassSurface(
        shape = CircleShape,
        modifier = Modifier
            .size(40.dp)
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = null) {
                val next = !enabled
                toggleHaptic(next)
                onToggle(next)
            }
            .semantics {
                contentDescription = if (enabled) {
                    "Glass effects on, tap to turn off for better performance"
                } else {
                    "Glass effects off, tap to turn on"
                }
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "✦", // U+2726 BLACK FOUR POINTED STAR
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f)
            )
        }
    }
}

/**
 * Small circular glass button, trailing [GlassEffectsToggleButton] in the
 * header — replaces the old three-way Appearance pill. Cycles ThemeMode in the
 * order System -&gt; Dark -&gt; Light -&gt; System on tap, morphing its glyph with
 * AnimatedContent. onTunablesChange already handles persistence and the
 * window-background flash fix on any themeMode change, so this only needs
 * to hand it the next mode.
 */
@Composable
internal fun ThemeToggleButton(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    val toggleHaptic = rememberToggleHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)
    val next = nextThemeMode(mode)

    GlassSurface(
        shape = CircleShape,
        modifier = Modifier
            .size(40.dp)
            .scale(pressScale)
            .clickable(interactionSource = interactionSource, indication = null) {
                // A 3-way cycle has no natural boolean the way a real switch
                // does — pinning to Light/Dark reads as "the override turning
                // on", returning to System as it turning back off.
                toggleHaptic(next != ThemeMode.SYSTEM)
                onModeChange(next)
            }
            .semantics {
                contentDescription = "Theme: ${mode.name.lowercase()}, tap for ${next.name.lowercase()}"
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.6f))
                },
                label = "themeModeGlyph"
            ) { m ->
                Text(
                    text = glyphFor(m),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
