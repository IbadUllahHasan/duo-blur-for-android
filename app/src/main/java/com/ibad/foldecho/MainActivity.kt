package com.ibad.foldecho

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

/**
 * Control panel for the system-wide effect: turn it on, see what the sensor is
 * actually reading, and tune the parameters the spec said could only be
 * settled on real hardware — live, while the service is running.
 */
class MainActivity : ComponentActivity() {
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var haptics: HapticsController
    private var tunables by mutableStateOf(Tunables())
    private var canDrawOverlays by mutableStateOf(false)

    /** Set once Compose has painted a frame; see [retireWindowBackgroundAfterFirstFrame]. */
    private var windowBackgroundRetired = false

    private val requestCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, FoldEchoService::class.java).apply {
                        putExtra(FoldEchoService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(FoldEchoService.EXTRA_RESULT_DATA, data)
                    }
                )
            }
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        haptics = HapticsController(this)
        tunables = FoldEchoSettings.load(this)
        applyWindowBackground(tunables.themeMode)
        retireWindowBackgroundAfterFirstFrame()
        // Both bars transparent, so the mesh gradient is what shows behind the
        // status bar and the gesture pill instead of a flat block of colour
        // sitting on top of it. `SystemBarStyle.dark(TRANSPARENT)` is chosen
        // for the transparent scrim, not for the icon colour it implies:
        // `.auto()` would key the icons off the *system* night setting, which
        // is wrong the moment ThemeMode pins the opposite one. The icons are
        // set explicitly from the resolved theme below, in composition, which
        // runs after this and wins.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (tunables.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Dark icons on a light theme, light icons on a dark one. Keyed
            // on `darkTheme` — the *resolved* value — so pinning Light while
            // the system is in night mode still gets dark icons. DisposableEffect
            // rather than SideEffect so it re-runs only when the theme actually
            // flips, and nav-bar icons are set alongside since that bar is now
            // transparent too.
            DisposableEffect(darkTheme) {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                onDispose { }
            }

            MaterialTheme(colorScheme = if (darkTheme) FoldEchoDarkColors else FoldEchoLightColors) {
                val running by FoldEchoState.running.collectAsState()
                val effectActive by FoldEchoState.effectActive.collectAsState()
                val deviation by FoldEchoState.deviationDeg.collectAsState()

                CompositionLocalProvider(
                    LocalHaptics provides haptics,
                    LocalUiHapticsEnabled provides tunables.uiHapticsEnabled,
                    LocalGlassPalette provides if (darkTheme) DarkGlassPalette else LightGlassPalette
                ) {
                    // No Surface here any more. Surface(color = ...) paints an
                    // opaque full-screen rect, and MeshGradientBackground
                    // already paints its own opaque base over the identical
                    // area — two full-screen fills for one background. That
                    // was one of three stacked opaque fills every pixel paid
                    // for before a single card was drawn (see
                    // retireWindowBackground for the other). Surface's only
                    // other job here was LocalContentColor, which is provided
                    // directly instead.
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        GlassScene(glassEnabled = tunables.glassEffectsEnabled) { scrollState ->
                            ControlPanel(
                                scrollState = scrollState,
                                running = running,
                                effectActive = effectActive,
                                deviationDeg = deviation,
                                tunables = tunables,
                                canDrawOverlays = canDrawOverlays,
                                onToggle = ::toggleService,
                                onRecalibrate = ::recalibrate,
                                onGrantOverlay = ::requestOverlayPermission,
                                onTunablesChange = ::updateTunables,
                                onResetTunables = ::resetTunables
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canDrawOverlays = Settings.canDrawOverlays(this)
    }

    private fun toggleService() {
        if (FoldEchoState.running.value) {
            startService(Intent(this, FoldEchoService::class.java).setAction(FoldEchoService.ACTION_STOP))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        requestCapture.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun recalibrate() {
        startService(Intent(this, FoldEchoService::class.java).setAction(FoldEchoService.ACTION_RECALIBRATE))
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun updateTunables(updated: Tunables) {
        val themeChanged = updated.themeMode != tunables.themeMode
        tunables = updated
        FoldEchoSettings.save(this, updated)
        if (themeChanged) applyWindowBackground(updated.themeMode)
    }

    /**
     * values/values-night resolve @color/window_background from the *system*
     * night setting, which is right for ThemeMode.SYSTEM and wrong the moment
     * the user pins the opposite one — you'd get a flash of the other theme
     * behind Compose on every cold start. Re-point the window drawable to
     * match whatever the preference actually resolves to.
     */
    private fun applyWindowBackground(mode: ThemeMode) {
        // Once Compose has painted, this drawable is pure overdraw — see
        // retireWindowBackgroundAfterFirstFrame. Re-applying it on a later
        // theme change would silently put that cost back, and there is
        // nothing left for it to fix: the in-app theme flip repaints
        // immediately now (GlassSurface's key(palette)), so the flash this
        // guards against can only happen on a cold start, before that first
        // frame exists.
        if (windowBackgroundRetired) return
        val dark = when (mode) {
            ThemeMode.SYSTEM ->
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        window.setBackgroundDrawable(
            ColorDrawable(getColor(if (dark) R.color.window_background_dark else R.color.window_background_light))
        )
    }

    /**
     * The window's own `ColorDrawable` covers the gap between the window
     * appearing and Compose's first frame, which is real — it is what keeps a
     * cold start from flashing the wrong theme. After that frame it is a
     * full-screen opaque draw underneath another full-screen opaque draw
     * ([MeshGradientBackground]'s base), i.e. pure overdraw on every pixel,
     * forever. An on-device GPU overdraw capture came back red across the
     * entire screen — including areas with no card on them at all — which is
     * what three stacked full-screen fills look like before anything else is
     * drawn.
     *
     * Dropping it the moment it stops being useful is the documented Android
     * fix for exactly this. The pre-draw listener fires just before the first
     * traversal paints, and MeshGradientBackground's base is opaque and fills
     * the window edge to edge, so nothing is ever left showing through.
     */
    private fun retireWindowBackgroundAfterFirstFrame() {
        val decor = window.decorView
        decor.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    decor.viewTreeObserver.removeOnPreDrawListener(this)
                    windowBackgroundRetired = true
                    window.setBackgroundDrawable(null)
                    return true
                }
            }
        )
    }

    private fun resetTunables() {
        tunables = FoldEchoSettings.reset(this)
        applyWindowBackground(tunables.themeMode)
    }
}

/**
 * Hosts the backdrop source and the scrolling content as siblings in one Box:
 * the mesh gradient behind, fixed, and the scrolling panel in front of it. The
 * Box fills the whole window including the area behind the system bars, so the
 * gradient runs edge to edge; it is the *content* that gets inset, in
 * ControlPanel, not this.
 *
 * The background is deliberately *outside* the scroll container and carries no
 * transform of its own, so it stays put while the cards travel over it. That
 * is what makes the glass legible: each card refracts whatever part of the
 * fixed field it currently covers, and scrolling alone is what animates the
 * effect. No extra code drives it.
 *
 * This replaces an earlier arrangement that translated the whole Box —
 * background included — as a custom overscroll rubber-band. That moved the
 * grid along with the cards, which is exactly what must not happen now, so
 * both it and the accompanying stock-overscroll suppression are gone. The
 * scroll container's own overscroll is left alone.
 *
 * The bug that arrangement originally existed for was Haze-specific: Haze drew
 * each card's backdrop in the *background's* layer from layout-time
 * coordinates, so a draw-time overscroll stretch pulled a card away from its
 * own backdrop and exposed the punched-out rectangle as a "ghost" layer.
 * LayerBackdrop draws the sampled layer inside the card's own draw scope
 * instead, so card and backdrop transform together whatever the scroller does,
 * and that bug cannot recur here.
 *
 * [glassEnabled] is the performance escape hatch: every `GlassSurface` in the
 * tree already falls back to a flat tint+border (no blur, no shadow, no
 * offscreen render target) whenever [LocalBackdrop] is null — that path
 * exists for the pre-first-frame/no-backdrop-yet case, and it's exactly the
 * cheap rendering a "turn glass off" setting needs, for free. So disabling
 * glass here means literally handing every card `null` instead of a real
 * backdrop, rather than threading a flag through every composable that draws
 * one. The mesh background itself is left alone either way — it's already
 * cheap (one clipped opaque fill plus five clipped radial gradients, captured
 * once and not re-paid per frame) — but capturing it into a layer nothing
 * will sample is pointless work, so that capture is skipped too.
 */
@Composable
private fun GlassScene(glassEnabled: Boolean, content: @Composable (ScrollState) -> Unit) {
    val scrollState = rememberScrollState()
    // One backdrop for the whole screen, remembered here at the scaffold and
    // handed down: every card samples this same captured layer. Creating one
    // per card would discard the capture on each recomposition. Always
    // remembered, even with glass off — a composable call has to run
    // unconditionally on every recomposition for its remembered state to
    // behave predictably; only whether it's *handed out* below is
    // conditional.
    val layerBackdrop = rememberLayerBackdrop()
    val backdrop = if (glassEnabled) layerBackdrop else null

    Box(Modifier.fillMaxSize()) {
        MeshGradientBackground(
            Modifier
                .fillMaxSize()
                .then(if (glassEnabled) Modifier.layerBackdrop(layerBackdrop) else Modifier)
        )
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            content(scrollState)
        }
    }
}

/**
 * Dark palette, remapped to Apple's iOS system-color values. `systemBlue` is
 * live-verified (fetched a real iOS design-guideline dataset); the rest are
 * extremely stable, independently well-known standard values unchanged since
 * iOS 13, not each individually re-fetched here — the best third-party
 * hex-reference sites were blocked by this environment's network policy and
 * Apple's own docs are JS-rendered with no literal hex reachable.
 * `background`/`onBackground` are pure black/white, matching Apple's `label`
 * and unified with GlassPalette.backgroundBase rather
 * than adding a third near-but-not-quite-matching near-black to the app.
 * `onSurfaceVariant`/`outlineVariant` use Apple's real alpha-based label
 * hierarchy (a base colour + alpha, not a distinct hue) rather than a flat
 * hex. `primaryContainer`/`onPrimaryContainer`/`surfaceVariant` have no Apple
 * equivalent and aren't read anywhere in this app today (confirmed by grep) —
 * reasonable Material3-convention values, low risk either way.
 */
private val FoldEchoDarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),              // systemBlue dark
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF003C6B),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFF5E5CE6),            // systemIndigo dark
    tertiary = Color(0xFF64D2FF),             // systemTeal dark
    background = Color(0xFF05060A),           // DarkGlassPalette.backgroundBase — the mesh's opaque base
    onBackground = Color(0xFFFFFFFF),         // Apple `label` dark
    surface = Color(0xFF1C1C1E),              // Apple secondarySystemBackground dark
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFEBEBF5).copy(alpha = 0.78f), // Apple secondaryLabel dark, alpha raised — see the light scheme's note
    surfaceContainer = Color(0xFF2C2C2E),     // Apple tertiarySystemBackground dark
    surfaceContainerHigh = Color(0xFF3A3A3C),
    outline = Color(0xFF38383A),              // Apple opaqueSeparator dark
    outlineVariant = Color(0xFF545458).copy(alpha = 0.65f) // Apple separator dark
)

/** Light counterpart, same Apple remap — see [FoldEchoDarkColors]'s doc comment for sourcing. */
private val FoldEchoLightColors = lightColorScheme(
    primary = Color(0xFF007AFF),              // systemBlue light — live-verified
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF00305A),
    secondary = Color(0xFF5856D6),            // systemIndigo light
    tertiary = Color(0xFF5AC8FA),             // systemTeal light
    background = Color(0xFFFBF8F3),           // LightGlassPalette.backgroundBase — the mesh's warm off-white base
    onBackground = Color(0xFF000000),         // Apple `label` light
    surface = Color(0xFFF2F2F7),              // Apple secondarySystemBackground light
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    // Apple's real secondaryLabel is #3C3C43 @0.6, and that is what was here.
    // It is calibrated for opaque iOS backgrounds; over a *translucent* glass
    // card the effective contrast drops again, which is what made light-mode
    // body text "barely readable". Raised to 0.85 rather than kept nominally
    // Apple-accurate, and the two call sites that used to multiply this by a
    // further 0.8 no longer do.
    onSurfaceVariant = Color(0xFF3C3C43).copy(alpha = 0.85f),
    surfaceContainer = Color(0xFFF2F2F7),     // Apple's tertiarySystemBackground light is #FFFFFF, same as background — reuses secondarySystemBackground instead
    surfaceContainerHigh = Color(0xFFE5E5EA),
    outline = Color(0xFFC6C6C8),              // Apple opaqueSeparator light
    outlineVariant = Color(0xFF3C3C43).copy(alpha = 0.29f) // Apple separator light
)

/** Resolved once in MainActivity.onCreate and handed down so any control can give a light tap without threading a parameter through every call site. Null only before composition ever runs. */
private val LocalHaptics = compositionLocalOf<HapticsController?> { null }

/** Mirrors Tunables.uiHapticsEnabled so controls can skip the tap without every caller checking it themselves. */
private val LocalUiHapticsEnabled = compositionLocalOf { true }

/**
 * Feedback for switch flips, routed through the platform instead of our own
 * Vibrator.
 *
 * HapticFeedbackConstants.TOGGLE_ON / TOGGLE_OFF (API 34) is what the OS maps
 * onto the device's dedicated toggle haptic — on a Samsung that is their
 * vibration HAL's own tuned toggle waveform, which is why it feels like the
 * rest of the system and a raw VibrationEffect primitive does not. Compose's
 * LocalHapticFeedback can't reach these (it only exposes LongPress and
 * TextHandleMove), so this goes through the host View directly.
 *
 * performHapticFeedback returning false means the system or the user has touch
 * feedback switched off, so there is deliberately no fallback in that case —
 * only pre-34 devices, which have no such constant at all, drop back to
 * HapticsController.
 *
 * Module-visible (not private) so GlassSwitch in Glass.kt can fire this
 * directly — LocalHaptics/LocalUiHapticsEnabled stay private, this function
 * still closes over them from within this same file.
 */
@Composable
internal fun rememberToggleHaptic(): (Boolean) -> Unit {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val uiHapticsEnabled = LocalUiHapticsEnabled.current
    return { on ->
        if (uiHapticsEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                view.performHapticFeedback(
                    if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
                )
            } else {
                haptics?.toggle(on)
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
private fun inverseLerp(a: Float, b: Float, v: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

// Each pair of functions below is a "simple" perceptual dial: [*T] reads the
// dial's 0..1 position back out of the real, already-persisted parameters
// (so Advanced always stays the source of truth), and [apply*] writes a
// dragged dial position back into those same parameters. No new state is
// stored for the dial itself — it's purely a friendlier way to move values
// that already existed.

private fun sensitivityT(t: Tunables) = inverseLerp(22f, 6f, t.activateDeg)
private fun applySensitivity(t: Tunables, dial: Float) = t.copy(
    activateDeg = lerp(22f, 6f, dial),
    fullTiltDeg = lerp(48f, 18f, dial)
)

private fun blurT(t: Tunables) = inverseLerp(15f, 65f, t.maxBlurPx)
private fun applyBlur(t: Tunables, dial: Float, rayTraced: Boolean): Tunables {
    val updated = t.copy(maxBlurPx = lerp(15f, 65f, dial))
    return if (rayTraced) {
        updated.copy(
            maxBlurRadiusPx = lerp(20f, 80f, dial),
            blurPerMm = lerp(0.8f, 3.2f, dial)
        )
    } else {
        updated
    }
}

private fun shadowT(t: Tunables) = inverseLerp(0.25f, 0.75f, t.maxDim)
private fun applyShadow(t: Tunables, dial: Float, rayTraced: Boolean): Tunables {
    val updated = t.copy(maxDim = lerp(0.25f, 0.75f, dial))
    return if (rayTraced) {
        updated.copy(
            maxDarken = lerp(0.35f, 0.95f, dial),
            darkenPerMm = lerp(0.008f, 0.045f, dial)
        )
    } else {
        updated
    }
}

private fun depthT(t: Tunables, rayTraced: Boolean) = if (rayTraced) {
    inverseLerp(450f, 150f, t.viewDistanceMm)
} else {
    inverseLerp(0.15f, 0.75f, t.perspectiveStrength)
}

private fun applyDepth(t: Tunables, dial: Float, rayTraced: Boolean): Tunables = if (rayTraced) {
    t.copy(viewDistanceMm = lerp(450f, 150f, dial))
} else {
    t.copy(
        perspectiveStrength = lerp(0.15f, 0.75f, dial),
        maxShrink = lerp(0.04f, 0.22f, dial)
    )
}

private fun hapticStrengthT(t: Tunables) = t.blurHapticsEngageStrength
private fun applyHapticStrength(t: Tunables, dial: Float) = t.copy(
    blurHapticsEngageStrength = dial,
    blurHapticsReleaseStrength = dial,
    blurHapticsTimeoutStrength = dial
)

@Composable
private fun ControlPanel(
    scrollState: ScrollState,
    running: Boolean,
    effectActive: Boolean,
    deviationDeg: Float,
    tunables: Tunables,
    canDrawOverlays: Boolean,
    onToggle: () -> Unit,
    onRecalibrate: () -> Unit,
    onGrantOverlay: () -> Unit,
    onTunablesChange: (Tunables) -> Unit,
    onResetTunables: () -> Unit
) {
    // Ray-traced fold is AGSL, which is API 33. On 31/32 OverlayController
    // silently runs the classic renderer no matter what this preference says,
    // so the panel has to show *classic's* tunables there — otherwise the card
    // offers sliders that cannot reach the renderer actually drawing.
    val rayTraced = tunables.foldShaderEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Ahead of .verticalScroll, so the inset shrinks the scroll
            // *viewport* rather than becoming scrollable padding: the wordmark
            // starts below the status bar/notch and the last card clears the
            // gesture pill, at every scroll position. safeDrawing rather than
            // systemBars because systemBars alone excludes the display cutout,
            // and the spec names the notch explicitly. Only MeshGradientBackground,
            // which is a sibling outside this Column, extends behind the bars.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                DuoFlowWordmark()
                Text(
                    "Tilt-driven Duo effect, system-wide",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassEffectsToggleButton(
                    enabled = tunables.glassEffectsEnabled,
                    onToggle = { onTunablesChange(tunables.copy(glassEffectsEnabled = it)) }
                )
                ThemeToggleButton(
                    mode = tunables.themeMode,
                    onModeChange = { onTunablesChange(tunables.copy(themeMode = it)) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        StatusCard(
            running = running,
            effectActive = effectActive,
            deviationDeg = deviationDeg,
            tunables = tunables,
            canDrawOverlays = canDrawOverlays,
            onToggle = onToggle,
            onRecalibrate = onRecalibrate,
            onGrantOverlay = onGrantOverlay
        )

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tuning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.width(6.dp))
                InfoTooltip(
                    text = "Adjustments apply immediately, even while the effect is running. " +
                        "Each slider's switch turns that parameter off without losing its value.",
                    glyph = "?"
                )
            }
            TextButton(onClick = onResetTunables) { Text("Reset to defaults") }
        }
        Spacer(Modifier.height(14.dp))

        TuningGroup {
            ModeSelector(
                usingFold = tunables.foldShaderEnabled,
                onSelect = { onTunablesChange(tunables.copy(foldShaderEnabled = it)) }
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "This device is on Android 12 — ray-traced fold needs 13 or newer, " +
                        "so the classic renderer runs regardless of which is selected here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9500) // Apple systemOrange
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Sensitivity",
                readout = "${(sensitivityT(tunables) * 100).roundToInt()}%",
                value = sensitivityT(tunables),
                range = 0f..1f,
                help = "How easily the effect triggers — deliberate (needs a firm tilt) to easy " +
                    "(fires at the slightest tilt). Moves the activation threshold and full-tilt " +
                    "point together."
            ) { onTunablesChange(applySensitivity(tunables, it)) }

            Spacer(Modifier.height(4.dp))
            TuningToggle(
                label = "Flip tilt direction",
                help = "Turn this on if the frame ever leans the opposite way from how you tilt the phone.",
                checked = tunables.flipTiltDirection
            ) { onTunablesChange(tunables.copy(flipTiltDirection = it)) }

            AdvancedSection {
                TuningSlider(
                    label = "Activation threshold",
                    readout = "${tunables.activateDeg.roundToInt()}°",
                    value = tunables.activateDeg,
                    range = 4f..30f,
                    help = "How far from neutral before the effect kicks in."
                ) { onTunablesChange(tunables.copy(activateDeg = it)) }

                TuningSlider(
                    label = "Full-tilt point",
                    readout = "${tunables.fullTiltDeg.roundToInt()}°",
                    value = tunables.fullTiltDeg,
                    range = 15f..60f,
                    help = "Deviation at which the effect reaches full strength."
                ) { onTunablesChange(tunables.copy(fullTiltDeg = it)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Blur",
                readout = "${(blurT(tunables) * 100).roundToInt()}%",
                value = blurT(tunables),
                range = 0f..1f,
                help = "How out-of-focus the tilted content gets, from a light haze to a heavy " +
                    "frost. Moves every blur-related parameter together."
            ) { onTunablesChange(applyBlur(tunables, it, rayTraced)) }

            AdvancedSection {
                TuningSlider(
                    label = "Peak blur radius",
                    readout = "${tunables.maxBlurPx.roundToInt()}px",
                    value = tunables.maxBlurPx,
                    range = 0f..80f,
                    help = "Peak blur radius at full tilt, graded from none at the hinge edge to full strength at the far edge.",
                    enabled = tunables.maxBlurPxEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(maxBlurPxEnabled = it)) }
                ) { onTunablesChange(tunables.copy(maxBlurPx = it)) }

                if (rayTraced) {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader("Ray-traced fold")

                    TuningSlider(
                        label = "Blur per mm",
                        readout = "${"%.1f".format(tunables.blurPerMm)}px",
                        value = tunables.blurPerMm,
                        range = 0f..6f,
                        help = "Blur radius gained per mm of gap between the glass and the plane.",
                        enabled = tunables.blurPerMmEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(blurPerMmEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(blurPerMm = it)) }

                    TuningSlider(
                        label = "Max blur radius",
                        readout = "${tunables.maxBlurRadiusPx.roundToInt()}px",
                        value = tunables.maxBlurRadiusPx,
                        range = 0f..96f,
                        help = "Ceiling on that radius, so a steep tilt can't melt the whole frame.",
                        enabled = tunables.maxBlurRadiusEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(maxBlurRadiusEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(maxBlurRadiusPx = it)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Shadow",
                readout = "${(shadowT(tunables) * 100).roundToInt()}%",
                value = shadowT(tunables),
                range = 0f..1f,
                help = "How dark the tilted content gets, from barely dimmed to nearly black. " +
                    "Moves every darkening parameter together."
            ) { onTunablesChange(applyShadow(tunables, it, rayTraced)) }

            AdvancedSection {
                TuningSlider(
                    label = "Dim",
                    readout = "${(tunables.maxDim * 100).roundToInt()}%",
                    value = tunables.maxDim,
                    range = 0f..0.9f,
                    help = "How dark the frame goes at full tilt.",
                    enabled = tunables.maxDimEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(maxDimEnabled = it)) }
                ) { onTunablesChange(tunables.copy(maxDim = it)) }

                if (rayTraced) {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader("Ray-traced fold")

                    TuningSlider(
                        label = "Darken per mm",
                        readout = "${"%.1f".format(tunables.darkenPerMm * 100)}%",
                        value = tunables.darkenPerMm,
                        range = 0f..0.1f,
                        help = "Light lost per mm of that same gap — frosted glass absorbing as it scatters.",
                        enabled = tunables.darkenPerMmEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(darkenPerMmEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(darkenPerMm = it)) }

                    TuningSlider(
                        label = "Max darken",
                        readout = "${(tunables.maxDarken * 100).roundToInt()}%",
                        value = tunables.maxDarken,
                        range = 0f..1f,
                        help = "Ceiling on that loss, so the far edge keeps some detail before it goes black.",
                        enabled = tunables.maxDarkenEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(maxDarkenEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(maxDarken = it)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            TuningSlider(
                label = "Depth",
                readout = "${(depthT(tunables, rayTraced) * 100).roundToInt()}%",
                value = depthT(tunables, rayTraced),
                range = 0f..1f,
                help = "How much 3D depth the tilt reveals — flat and subtle, or a steep, dramatic recede."
            ) { onTunablesChange(applyDepth(tunables, it, rayTraced)) }

            AdvancedSection {
                if (rayTraced) {
                    TuningSlider(
                        label = "View distance",
                        readout = "${tunables.viewDistanceMm.roundToInt()}mm",
                        value = tunables.viewDistanceMm,
                        range = 100f..600f,
                        help = "How far the eye sits from the content plane. Closer is a more extreme perspective.",
                        enabled = tunables.viewDistanceEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(viewDistanceEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(viewDistanceMm = it)) }
                } else {
                    TuningSlider(
                        label = "Perspective",
                        readout = "${(tunables.perspectiveStrength * 100).roundToInt()}%",
                        value = tunables.perspectiveStrength,
                        range = 0f..1f,
                        help = "How much depth the tilt reveals (camera distance), not how far the frame leans.",
                        enabled = tunables.perspectiveEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(perspectiveEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(perspectiveStrength = it)) }

                    TuningSlider(
                        label = "Recede",
                        readout = "${(tunables.maxShrink * 100).roundToInt()}%",
                        value = tunables.maxShrink,
                        range = 0f..0.3f,
                        help = "How much the frame shrinks at full tilt, paired with the lean — " +
                            "sells a plane receding into distance rather than a flat zoom.",
                        enabled = tunables.maxShrinkEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(maxShrinkEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(maxShrink = it)) }

                    TuningSlider(
                        label = "Motion Softness",
                        readout = "${(tunables.motionSoftness * 100).roundToInt()}%",
                        value = tunables.motionSoftness,
                        range = 0f..1f,
                        help = "Spring damping on the lean/recede motion. Low is a light settle; high overshoots and bounces.",
                        enabled = tunables.motionSoftnessEnabled,
                        onEnabledChange = { onTunablesChange(tunables.copy(motionSoftnessEnabled = it)) }
                    ) { onTunablesChange(tunables.copy(motionSoftness = it)) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            AdvancedSection(title = "Finishing touches") {
                TuningSlider(
                    label = "Edge Fade",
                    readout = "${(tunables.edgeFadeStrength * 100).roundToInt()}%",
                    value = tunables.edgeFadeStrength,
                    range = 0f..1f,
                    help = "Alpha gradient from opaque center to transparent edge, strength scaling with tilt.",
                    enabled = tunables.edgeFadeEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(edgeFadeEnabled = it)) }
                ) { onTunablesChange(tunables.copy(edgeFadeStrength = it)) }

                TuningSlider(
                    label = "Corner Radius",
                    readout = "${(tunables.cornerRadiusStrength * 100).roundToInt()}% · " +
                        "${(tunables.cornerRadiusStrength * tunables.cornerRadiusBaseDp).roundToInt()}dp",
                    value = tunables.cornerRadiusStrength,
                    range = 0f..1f,
                    help = "Fraction of this device's actual screen-corner radius " +
                        "(${tunables.cornerRadiusBaseDp.roundToInt()}dp detected). 100% matches the real corners.",
                    enabled = tunables.cornerRadiusEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(cornerRadiusEnabled = it)) }
                ) { onTunablesChange(tunables.copy(cornerRadiusStrength = it)) }
            }
        }

        Spacer(Modifier.height(16.dp))
        TuningGroup {
            SectionHeader("Haptics")

            TuningToggle(
                label = "Interface haptics",
                help = "Taps when you drag sliders or flip switches in this app. Switches use the system's own toggle feedback. Doesn't affect the fold effect itself.",
                checked = tunables.uiHapticsEnabled
            ) { onTunablesChange(tunables.copy(uiHapticsEnabled = it)) }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(4.dp))

            TuningToggle(
                label = "Fold haptics",
                help = "A tactile cue when the fold effect engages, releases, or auto-resets after being held too long.",
                checked = tunables.blurHapticsEnabled
            ) { onTunablesChange(tunables.copy(blurHapticsEnabled = it)) }

            TuningSlider(
                label = "Haptic strength",
                readout = "${(hapticStrengthT(tunables) * 100).roundToInt()}%",
                value = hapticStrengthT(tunables),
                range = 0f..1f,
                help = "Overall intensity of the fold haptics, applied across engage, release, and the auto-reset alert together.",
                enabled = tunables.blurHapticsEnabled
            ) { onTunablesChange(applyHapticStrength(tunables, it)) }

            AdvancedSection {
                TuningSlider(
                    label = "Engage",
                    readout = "${(tunables.blurHapticsEngageStrength * 100).roundToInt()}%",
                    value = tunables.blurHapticsEngageStrength,
                    range = 0f..1f,
                    help = "Cue when the fold effect engages, as you tilt past the activation threshold.",
                    enabled = tunables.blurHapticsEnabled && tunables.blurHapticsEngageEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(blurHapticsEngageEnabled = it)) }
                ) { onTunablesChange(tunables.copy(blurHapticsEngageStrength = it)) }

                TuningSlider(
                    label = "Release",
                    readout = "${(tunables.blurHapticsReleaseStrength * 100).roundToInt()}%",
                    value = tunables.blurHapticsReleaseStrength,
                    range = 0f..1f,
                    help = "Cue when the effect lets go, as you tilt back toward neutral.",
                    enabled = tunables.blurHapticsEnabled && tunables.blurHapticsReleaseEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(blurHapticsReleaseEnabled = it)) }
                ) { onTunablesChange(tunables.copy(blurHapticsReleaseStrength = it)) }

                TuningSlider(
                    label = "Auto-release alert",
                    readout = "${(tunables.blurHapticsTimeoutStrength * 100).roundToInt()}%",
                    value = tunables.blurHapticsTimeoutStrength,
                    range = 0f..1f,
                    help = "A distinct cue when the safety timeout releases a fold that's been held too long.",
                    enabled = tunables.blurHapticsEnabled && tunables.blurHapticsTimeoutEnabled,
                    onEnabledChange = { onTunablesChange(tunables.copy(blurHapticsTimeoutEnabled = it)) }
                ) { onTunablesChange(tunables.copy(blurHapticsTimeoutStrength = it)) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "While enabled, the system's screen-recording indicator stays visible — " +
                "that's the cost of holding one capture session open instead of asking " +
                "for consent on every tilt. Apps that block screenshots (banking, " +
                "password managers, DRM video) will show black instead of their content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** The glass shell every tuning section shares — real refraction of the gradient behind it, not a flat translucent color. */
@Composable
private fun TuningGroup(content: @Composable () -> Unit) {
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
private fun AdvancedSection(title: String = "Advanced", content: @Composable () -> Unit) {
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
private fun InfoTooltip(text: String, glyph: String = "i") {
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

/**
 * "Duo" solid, then "Flow" typed out letter by letter with a small blur
 * radius growing across it — the same "none at the near edge, full strength
 * at the far edge" grading the app's own Blur slider applies to the frame,
 * just borrowed for the wordmark instead of a captured screenshot.
 */
@Composable
private fun DuoFlowWordmark() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "Duo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        "Flow".forEachIndexed { index, letter ->
            Text(
                letter.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.blur((index * 0.6f).dp)
            )
        }
    }
}

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
private fun GlassEffectsToggleButton(enabled: Boolean, onToggle: (Boolean) -> Unit) {
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
private fun ThemeToggleButton(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
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

@Composable
private fun SectionHeader(label: String) {
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
private fun ModeSelector(usingFold: Boolean, onSelect: (Boolean) -> Unit) {
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
 * genuinely different there. In dark mode they are not — `FoldEchoDarkColors`
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

@Composable
private fun StatusCard(
    running: Boolean,
    effectActive: Boolean,
    deviationDeg: Float,
    tunables: Tunables,
    canDrawOverlays: Boolean,
    onToggle: () -> Unit,
    onRecalibrate: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    val (label, dot) = when {
        effectActive -> "Effect active" to MaterialTheme.colorScheme.primary
        running -> "Watching for tilt" to Color(0xFF34C759) // Apple systemGreen
        else -> "Off" to MaterialTheme.colorScheme.outline
    }

    GlassSurface(shape = RoundedCornerShape(GlassRadii.card)) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                GlassSwitch(
                    checked = running,
                    onCheckedChange = { onToggle() }
                )
            }

            if (!canDrawOverlays) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "\"Display over other apps\" is off — the effect can't draw without it.",
                        style = MaterialTheme.typography.bodySmall,
                        // Was a hardcoded #FFD79A — a pale cream picked for a
                        // dark card, effectively invisible on a light one.
                        // Found while auditing item 4's claim that the
                        // segmented control was hardcoded (it wasn't); this
                        // one actually was, and is the same defect class, so
                        // it is fixed here rather than left to be re-reported.
                        // Apple systemOrange resolves per theme instead.
                        // NOT isSystemInDarkTheme(): ThemeMode can pin Light
                        // while the system is in night mode, and this has to
                        // follow the *resolved* theme. colorScheme.background
                        // is pinned per theme, so its luminance is that signal
                        // without threading a new parameter or composition
                        // local through for one string.
                        color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                            Color(0xFFFF9F0A) // Apple systemOrange dark
                        } else {
                            Color(0xFFC2410C) // darkened orange — systemOrange itself is ~2.5:1 on white
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onGrantOverlay) { Text("Grant") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Tilt from neutral",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (running) "${"%.1f".format(deviationDeg)}°" else "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = (deviationDeg / tunables.fullTiltDeg).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = dot,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Triggers past ${tunables.activateDeg.roundToInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onRecalibrate, enabled = running) { Text("Recalibrate") }
            }
        }
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
private fun TuningSlider(
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
private fun TuningToggle(
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
