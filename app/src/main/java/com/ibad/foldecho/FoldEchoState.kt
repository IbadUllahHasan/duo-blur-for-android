package com.ibad.foldecho

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Live state shared between the service and the control panel. The service is
 * the only writer; the UI reads it so it can't drift out of sync when the
 * service stops on its own (projection revoked from the system UI, say).
 */
object FoldEchoState {
    val running = MutableStateFlow(false)
    val effectActive = MutableStateFlow(false)
    val deviationDeg = MutableStateFlow(0f)

    /** Signed tilt components, mirrored from the same sensor reading that drives the fold effect — for UI-only cosmetics (e.g. a specular highlight) that want direction, not just magnitude. */
    val tiltUpDeg = MutableStateFlow(0f)
    val tiltRightDeg = MutableStateFlow(0f)

    fun reset() {
        running.value = false
        effectActive.value = false
        deviationDeg.value = 0f
        tiltUpDeg.value = 0f
        tiltRightDeg.value = 0f
    }
}
