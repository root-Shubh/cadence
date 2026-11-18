package com.cadence.patterns

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Today's walk drawing, shared by the step service (which adds a point per step) and the Pattern
 * tab (which draws it as it grows). Pure Kotlin. Thread-safe: every change is under one lock.
 *
 * Each step moves the pen one stride in the current heading. With the pen up the step moves the
 * pen without drawing, so letters can be written. [newDrawing] starts a fresh drawing at 0, 0.
 * Points not yet saved are kept for [drainPending].
 */
object LiveTrack {
    private val lock = Any()
    private var day: String? = null
    private var drawing = 0
    private var pending = ArrayList<TrackPoint>()

    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())

    /** Today's points, all drawings, oldest first. */
    val points: StateFlow<List<TrackPoint>> = _points.asStateFlow()

    private val _penDown = MutableStateFlow(true)

    /** True while steps draw; false while they only move the pen. */
    val penDown: StateFlow<Boolean> = _penDown.asStateFlow()

    /** The day the points belong to (yyyy-MM-dd), or null before anything is loaded. */
    fun day(): String? = synchronized(lock) { day }

    /** Replaces the state with the saved points of [forDay], for example when counting starts. */
    fun load(forDay: String, saved: List<TrackPoint>) = synchronized(lock) {
        if (day == forDay && _points.value.size >= saved.size) return@synchronized
        day = forDay
        drawing = saved.maxOfOrNull { it.drawing } ?: 0
        pending = ArrayList()
        _points.value = saved
    }

    /**
     * One step on [forDay] at [timeMillis], heading [headingRad] (null: direction unknown, the
     * step is skipped), stride [strideM]. A new day starts empty. The first step of a drawing also
     * records its starting point.
     */
    fun onStep(forDay: String, timeMillis: Long, headingRad: Double?, strideM: Double) = synchronized(lock) {
        if (day != forDay) {
            day = forDay
            drawing = 0
            pending = ArrayList()
            _points.value = emptyList()
        }
        if (headingRad == null || !strideM.isFinite() || strideM <= 0.0) return@synchronized
        val current = _points.value
        val last = current.lastOrNull { it.drawing == drawing }
        val added = ArrayList<TrackPoint>(2)
        val from = last ?: TrackPoint(timeMillis, 0.0, 0.0, pen = false, drawing = drawing).also { added.add(it) }
        added.add(stepFrom(from, headingRad, strideM, timeMillis, _penDown.value))
        pending.addAll(added)
        _points.value = current + added
    }

    fun setPenDown(down: Boolean) {
        _penDown.value = down
    }

    /** The next step starts a new drawing at 0, 0. Does nothing when the current drawing is empty. */
    fun newDrawing() = synchronized(lock) {
        if (_points.value.any { it.drawing == drawing }) drawing++
    }

    /** Points added since the last call, for saving. */
    fun drainPending(): List<TrackPoint> = synchronized(lock) {
        val out = pending
        pending = ArrayList()
        out
    }

    /** Puts points back after a failed save, ahead of newer ones. */
    fun requeue(points: List<TrackPoint>) = synchronized(lock) {
        pending.addAll(0, points)
    }

    /** Clears everything, for tests. */
    internal fun resetForTest() = synchronized(lock) {
        day = null
        drawing = 0
        pending = ArrayList()
        _points.value = emptyList()
        _penDown.value = true
    }
}
