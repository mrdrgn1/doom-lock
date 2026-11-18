package com.lockin

import android.content.Context

enum class Phase { IDLE, FOCUS, BREAK, PAUSED, APPS, PANEL }

/** One stretch of a session: 'F' locked in, 'B' doomscroll break, 'P' pause, 'A' allowed app. */
class SessionSegment(val kind: Char, val startMs: Long, val endMs: Long)

/**
 * Single source of truth for the running session. Everything is timestamp based and saved to
 * SharedPreferences, so a process death, screen off or reboot cannot skew the timers.
 *
 * - FOCUS: the focus timer runs; the doomscroll bank starts at 10 minutes and earns 1 more
 *   minute for every 6 minutes of focus, with no cap. Only the minutes actually used are spent.
 * - BREAK: focus timer frozen, the bank drains in real time.
 * - PAUSED: focus timer frozen, no bank drain.
 * - APPS: an always-available app was opened; focus keeps running and nothing is charged.
 *
 * Every transition is also recorded as a [SessionSegment] so the end-of-session screen can draw
 * a timeline of when you were locked in and when you were not.
 */
object SessionStore {

    /** Doomscroll time every session starts with. */
    const val DOOM_START_MS = 10L * 60L * 1000L
    const val APPS_WINDOW_MS = 10L * 60L * 1000L

    /** How long the quick panel stays reachable before the lock screen pins itself again. */
    const val PANEL_WINDOW_MS = 60L * 1000L

    /** 1 minute of doomscroll for every 6 minutes of focus. */
    private const val EARN_DIVISOR = 6L
    private const val PREFS = "lockin_session"

    var phase: Phase = Phase.IDLE
        private set
    var startMs: Long = 0L
        private set

    /** Wall clock time the current Do Not Disturb stretch began (for the "since HH:MM" line). */
    var dndSince: Long = 0L
        private set

    /** Interruption filter that was active before we switched DND on, restored at the end. */
    var prevFilter: Int = -1
        private set

    /** Goal chosen on the start screen, 0 when the session is open ended. */
    var plannedMs: Long = 0L
        private set

    private var focusAccum = 0L
    private var focusSeg = 0L
    private var breakAccum = 0L
    private var breakSeg = 0L
    private var pauseAccum = 0L
    private var pauseSeg = 0L
    private var bank = DOOM_START_MS
    private var bankRef = 0L

    /** Leftover milliseconds towards the next earned minute, kept across breaks and pauses. */
    private var earnCarry = 0L
    private var appsStart = 0L

    private val timeline = ArrayList<SessionSegment>()
    private var openKind: Char? = null
    private var openAt = 0L

    val isActive: Boolean get() = phase != Phase.IDLE

    class Result(
        val startMs: Long,
        val endMs: Long,
        val focusMs: Long,
        val breakMs: Long,
        val pauseMs: Long,
        val prevFilter: Int,
        val segments: List<SessionSegment>
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        val sp = prefs(context)
        phase = runCatching { Phase.valueOf(sp.getString("phase", "IDLE") ?: "IDLE") }
            .getOrDefault(Phase.IDLE)
        startMs = sp.getLong("start", 0L)
        dndSince = sp.getLong("dndSince", 0L)
        prevFilter = sp.getInt("prevFilter", -1)
        plannedMs = sp.getLong("planned", 0L)
        focusAccum = sp.getLong("focusAccum", 0L)
        focusSeg = sp.getLong("focusSeg", 0L)
        breakAccum = sp.getLong("breakAccum", 0L)
        breakSeg = sp.getLong("breakSeg", 0L)
        pauseAccum = sp.getLong("pauseAccum", 0L)
        pauseSeg = sp.getLong("pauseSeg", 0L)
        bank = sp.getLong("bank", DOOM_START_MS)
        bankRef = sp.getLong("bankRef", 0L)
        earnCarry = sp.getLong("earnCarry", 0L)
        appsStart = sp.getLong("appsStart", 0L)
    }

    fun save(context: Context) {
        prefs(context).edit()
            .putString("phase", phase.name)
            .putLong("start", startMs)
            .putLong("dndSince", dndSince)
            .putInt("prevFilter", prevFilter)
            .putLong("planned", plannedMs)
            .putLong("focusAccum", focusAccum)
            .putLong("focusSeg", focusSeg)
            .putLong("breakAccum", breakAccum)
            .putLong("breakSeg", breakSeg)
            .putLong("pauseAccum", pauseAccum)
            .putLong("pauseSeg", pauseSeg)
            .putLong("bank", bank)
            .putLong("bankRef", bankRef)
            .putLong("earnCarry", earnCarry)
            .putLong("appsStart", appsStart)
            .apply()
    }

    fun focusMs(now: Long): Long =
        focusAccum + if (focusSeg > 0L) (now - focusSeg).coerceAtLeast(0L) else 0L

    fun breakMs(now: Long): Long =
        breakAccum + if (breakSeg > 0L) (now - breakSeg).coerceAtLeast(0L) else 0L

    fun pauseMs(now: Long): Long =
        pauseAccum + if (pauseSeg > 0L) (now - pauseSeg).coerceAtLeast(0L) else 0L

    /** Banked doomscroll time, including what the current focus stretch has earned. Uncapped. */
    fun bankMs(now: Long): Long {
        if (!isActive) return 0L
        var b = bank
        if (focusSeg > 0L && bankRef > 0L && now > bankRef) {
            b += (now - bankRef) / EARN_DIVISOR
        }
        return b.coerceAtLeast(0L)
    }

    fun breakLeftMs(now: Long): Long =
        if (phase == Phase.BREAK) (bank - (now - breakSeg)).coerceAtLeast(0L) else 0L

    fun appsLeftMs(now: Long): Long =
        if (phase == Phase.APPS) (appsStart + APPS_WINDOW_MS - now).coerceAtLeast(0L) else 0L

    // ------------------------------------------------------------------ timeline

    private fun openSegment(kind: Char, now: Long) {
        closeSegment(now)
        openKind = kind
        openAt = now
    }

    private fun closeSegment(now: Long) {
        val kind = openKind ?: return
        if (now > openAt) timeline.add(SessionSegment(kind, openAt, now))
        openKind = null
    }

    // ------------------------------------------------------------------ transitions

    fun start(context: Context, now: Long, planned: Long = 0L) {
        phase = Phase.FOCUS
        startMs = now
        dndSince = now
        plannedMs = planned.coerceAtLeast(0L)
        prevFilter = Dnd.currentFilter(context)
        focusAccum = 0L
        focusSeg = now
        breakAccum = 0L
        breakSeg = 0L
        pauseAccum = 0L
        pauseSeg = 0L
        bank = DOOM_START_MS
        bankRef = now
        earnCarry = 0L
        appsStart = 0L
        timeline.clear()
        openKind = null
        openAt = now
        openSegment('F', now)
        save(context)
    }

    /** Starts a doomscroll break. Returns false when nothing is banked. */
    fun beginBreak(context: Context, now: Long): Boolean {
        if (phase != Phase.FOCUS) return false
        val available = bankMs(now)
        if (available <= 0L) return false
        focusAccum = focusMs(now)
        focusSeg = 0L
        bank = available
        earnCarry = if (bankRef > 0L) (now - bankRef) % EARN_DIVISOR else 0L
        bankRef = 0L
        breakSeg = now
        phase = Phase.BREAK
        openSegment('B', now)
        save(context)
        return true
    }

    /**
     * Ends the break, charging only the time actually used. A break can never cost more than the
     * balance it started with, so a countdown the phone killed cannot drain anything extra.
     */
    fun endBreak(context: Context, now: Long): Long {
        if (phase != Phase.BREAK) return 0L
        val elapsed = (now - breakSeg).coerceAtLeast(0L)
        val used = minOf(elapsed, bank)
        breakAccum += used
        bank = (bank - used).coerceAtLeast(0L)
        breakSeg = 0L
        phase = Phase.FOCUS
        focusSeg = now
        bankRef = now - earnCarry
        earnCarry = 0L
        dndSince = now
        openSegment('F', now)
        save(context)
        return used
    }

    fun beginPause(context: Context, now: Long) {
        if (phase != Phase.FOCUS) return
        focusAccum = focusMs(now)
        focusSeg = 0L
        bank = bankMs(now)
        earnCarry = if (bankRef > 0L) (now - bankRef) % EARN_DIVISOR else 0L
        bankRef = 0L
        pauseSeg = now
        phase = Phase.PAUSED
        openSegment('P', now)
        save(context)
    }

    fun endPause(context: Context, now: Long) {
        if (phase != Phase.PAUSED) return
        pauseAccum += (now - pauseSeg).coerceAtLeast(0L)
        pauseSeg = 0L
        phase = Phase.FOCUS
        focusSeg = now
        bankRef = now - earnCarry
        earnCarry = 0L
        dndSince = now
        openSegment('F', now)
        save(context)
    }

    /**
     * A one minute look at the quick panel: the screen unpins, the focus timer freezes and the
     * minute counts as unlocked time, exactly like a pause.
     */
    fun beginPanelPass(context: Context, now: Long) {
        if (phase != Phase.FOCUS) return
        focusAccum = focusMs(now)
        focusSeg = 0L
        bank = bankMs(now)
        earnCarry = if (bankRef > 0L) (now - bankRef) % EARN_DIVISOR else 0L
        bankRef = 0L
        pauseSeg = now
        phase = Phase.PANEL
        openSegment('P', now)
        save(context)
    }

    fun endPanelPass(context: Context, now: Long) {
        if (phase != Phase.PANEL) return
        pauseAccum += (now - pauseSeg).coerceAtLeast(0L)
        pauseSeg = 0L
        phase = Phase.FOCUS
        focusSeg = now
        bankRef = now - earnCarry
        earnCarry = 0L
        openSegment('F', now)
        save(context)
    }

    fun panelLeftMs(now: Long): Long =
        if (phase == Phase.PANEL && pauseSeg > 0L) {
            (pauseSeg + PANEL_WINDOW_MS - now).coerceAtLeast(0L)
        } else {
            0L
        }

    /** Always-available app window: focus keeps running, nothing is charged. */
    fun beginApps(context: Context, now: Long) {
        if (phase != Phase.FOCUS) return
        appsStart = now
        phase = Phase.APPS
        openSegment('A', now)
        save(context)
    }

    fun endApps(context: Context, now: Long) {
        if (phase != Phase.APPS) return
        appsStart = 0L
        phase = Phase.FOCUS
        openSegment('F', now)
        save(context)
    }

    fun end(context: Context, now: Long): Result? {
        if (!isActive) return null
        closeSegment(now)
        val segments = ArrayList(timeline)
        val result = Result(startMs, now, focusMs(now), breakMs(now), pauseMs(now), prevFilter, segments)
        timeline.clear()
        phase = Phase.IDLE
        startMs = 0L
        dndSince = 0L
        prevFilter = -1
        plannedMs = 0L
        focusAccum = 0L
        focusSeg = 0L
        breakAccum = 0L
        breakSeg = 0L
        pauseAccum = 0L
        pauseSeg = 0L
        bank = DOOM_START_MS
        bankRef = 0L
        earnCarry = 0L
        appsStart = 0L
        save(context)
        return result
    }
}
