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
 *
 * Points live in one growing list that is never copied per step (STEPW-64: copying the whole list
 * on every step made long walks stutter). A step only bumps [version]; readers take a [snapshot]
 * when they redraw, at their own pace.
 */
object LiveTrack {
    private val lock = Any()
    private var day: String? = null
    private var drawing = 0
    private var pending = ArrayList<TrackPoint>()

    private val all = ArrayList<TrackPoint>()

    private val _version = MutableStateFlow(0L)

    /** Changes whenever the points change. Read the points with [snapshot]. */
    val version: StateFlow<Long> = _version.asStateFlow()

    /** Today's points, all drawings, oldest first. A copy: callers should not take it per step. */
    fun snapshot(): List<TrackPoint> = synchronized(lock) { ArrayList(all) }

    private fun changed() {
        _version.value = _version.value + 1
    }

    private val _penDown = MutableStateFlow(true)

    /** True while steps draw; false while they only move the pen. */
    val penDown: StateFlow<Boolean> = _penDown.asStateFlow()

    /** The day the points belong to (yyyy-MM-dd), or null before anything is loaded. */
    fun day(): String? = synchronized(lock) { day }

    /** Replaces the state with the saved points of [forDay], for example when counting starts. */
    fun load(forDay: String, saved: List<TrackPoint>) = synchronized(lock) {
        if (day == forDay && all.size >= saved.size) return@synchronized
        day = forDay
        drawing = saved.maxOfOrNull { it.drawing } ?: 0
        pending = ArrayList()
        all.clear()
        all.addAll(saved)
        changed()
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
            all.clear()
            changed()
        }
        if (headingRad == null || !strideM.isFinite() || strideM <= 0.0) return@synchronized
        val last = all.lastOrNull { it.drawing == drawing }
        val added = ArrayList<TrackPoint>(2)
        val from = last ?: TrackPoint(timeMillis, 0.0, 0.0, pen = false, drawing = drawing).also { added.add(it) }
        added.add(stepFrom(from, headingRad, strideM, timeMillis, _penDown.value))
        pending.addAll(added)
        all.addAll(added)
        changed()
    }

    fun setPenDown(down: Boolean) {
        _penDown.value = down
    }

    /** The next step starts a new drawing at 0, 0. Does nothing when the current drawing is empty. */
    fun newDrawing() = synchronized(lock) {
        if (all.any { it.drawing == drawing }) drawing++
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
        all.clear()
        changed()
        _penDown.value = true
    }
}
