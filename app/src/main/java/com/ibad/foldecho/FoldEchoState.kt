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

    fun reset() {
        running.value = false
        effectActive.value = false
        deviationDeg.value = 0f
    }
}
