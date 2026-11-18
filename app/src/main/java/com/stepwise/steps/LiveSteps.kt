package com.stepwise.steps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide live step count for today. The step service updates it on every sensor event,
 * with no database work, so the UI can show the count without lag. Storage is handled
 * separately and throttled.
 */
object LiveSteps {

    private val _steps = MutableStateFlow(0)

    /** Steps counted today. */
    val steps: StateFlow<Int> = _steps.asStateFlow()

    private val _tracking = MutableStateFlow(false)

    /** True while the step service is counting. */
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    /** Sets today's total, for example the stored total when counting starts. */
    fun reset(total: Int) {
        _steps.value = total.coerceAtLeast(0)
    }

    /** Adds [delta] steps. Safe to call from any thread. */
    fun add(delta: Int) {
        if (delta <= 0) return
        _steps.update { (it.toLong() + delta).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    }

    fun setTracking(on: Boolean) {
        _tracking.value = on
    }
}
