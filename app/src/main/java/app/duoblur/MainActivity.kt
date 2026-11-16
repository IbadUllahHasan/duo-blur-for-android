package app.duoblur

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
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.duoblur.ui.ControlPanel
import app.duoblur.ui.DuoBlurDarkColors
import app.duoblur.ui.DuoBlurLightColors
import app.duoblur.ui.GlassScene
import app.duoblur.ui.LocalHaptics
import app.duoblur.ui.LocalUiHapticsEnabled

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
                    Intent(this, DuoBlurService::class.java).apply {
                        putExtra(DuoBlurService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(DuoBlurService.EXTRA_RESULT_DATA, data)
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
        tunables = DuoBlurSettings.load(this)
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

            MaterialTheme(colorScheme = if (darkTheme) DuoBlurDarkColors else DuoBlurLightColors) {
                val running by DuoBlurState.running.collectAsState()
                val effectActive by DuoBlurState.effectActive.collectAsState()
                val deviation by DuoBlurState.deviationDeg.collectAsState()

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
        if (DuoBlurState.running.value) {
            startService(Intent(this, DuoBlurService::class.java).setAction(DuoBlurService.ACTION_STOP))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        requestCapture.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun recalibrate() {
        startService(Intent(this, DuoBlurService::class.java).setAction(DuoBlurService.ACTION_RECALIBRATE))
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun updateTunables(updated: Tunables) {
        val themeChanged = updated.themeMode != tunables.themeMode
        tunables = updated
        DuoBlurSettings.save(this, updated)
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
        tunables = DuoBlurSettings.reset(this)
        applyWindowBackground(tunables.themeMode)
    }
}
