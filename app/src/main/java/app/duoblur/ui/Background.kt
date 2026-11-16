package app.duoblur.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.duoblur.LocalBackdrop
import app.duoblur.MeshGradientBackground
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop

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
internal fun GlassScene(glassEnabled: Boolean, content: @Composable (ScrollState) -> Unit) {
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
