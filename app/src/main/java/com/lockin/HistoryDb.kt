package com.lockin

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

class SessionRow(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val focusMs: Long,
    val breakMs: Long,
    val pauseMs: Long
)

/** A saved copy of one session, used to undo a delete or an edit. */
class SessionSnapshot(
    val day: String,
    val startMs: Long,
    val endMs: Long,
    val focusMs: Long,
    val breakMs: Long,
    val pauseMs: Long,
    val segments: List<SessionSegment>
)

class SessionDetail(
    val startMs: Long,
    val endMs: Long,
    val focusMs: Long,
    val breakMs: Long,
    val pauseMs: Long,
    val segments: List<SessionSegment>
)

class HistoryDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "lockin.db", null, 2) {

    companion object {
        @Volatile
        private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb =
            instance ?: synchronized(this) {
                instance ?: HistoryDb(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE sessions (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "day TEXT NOT NULL," +
                "start_ms INTEGER NOT NULL," +
                "end_ms INTEGER NOT NULL," +
                "focus_ms INTEGER NOT NULL," +
                "break_ms INTEGER NOT NULL," +
                "pause_ms INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX sessions_day ON sessions(day)")
        db.execSQL(
            "CREATE TABLE notes (" +
                "day TEXT PRIMARY KEY," +
                "note TEXT NOT NULL," +
                "updated_ms INTEGER NOT NULL)"
        )
        createSegments(db)
    }

    /**
     * Version 1 shipped without the segments table. Upgrades only ever add tables and columns,
     * so sessions and notes written by older builds stay exactly where they are.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createSegments(db)
    }

    private fun createSegments(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS segments (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "session_id INTEGER NOT NULL," +
                "start_ms INTEGER NOT NULL," +
                "end_ms INTEGER NOT NULL," +
                "kind TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS segments_session ON segments(session_id)")
    }

    fun addSession(result: SessionStore.Result) {
        runCatching {
            val values = ContentValues().apply {
                put("day", Fmt.dayKey(result.startMs))
                put("start_ms", result.startMs)
                put("end_ms", result.endMs)
                put("focus_ms", result.focusMs.coerceAtLeast(0L))
                put("break_ms", result.breakMs.coerceAtLeast(0L))
                put("pause_ms", result.pauseMs.coerceAtLeast(0L))
            }
            val id = writableDatabase.insert("sessions", null, values)
            if (id <= 0L) return@runCatching
            result.segments.forEach { segment ->
                val row = ContentValues().apply {
                    put("session_id", id)
                    put("start_ms", segment.startMs)
                    put("end_ms", segment.endMs)
                    put("kind", segment.kind.toString())
                }
                writableDatabase.insert("segments", null, row)
            }
        }
    }

    /** A session typed in by hand on the calendar: start to end with no break or pause. */
    fun addManualSession(startMs: Long, endMs: Long): Long {
        var id = -1L
        runCatching {
            val values = ContentValues().apply {
                put("day", Fmt.dayKey(startMs))
                put("start_ms", startMs)
                put("end_ms", endMs)
                put("focus_ms", (endMs - startMs).coerceAtLeast(0L))
                put("break_ms", 0L)
                put("pause_ms", 0L)
            }
            id = writableDatabase.insert("sessions", null, values)
        }
        return id
    }

    /** Changes a session's times; focus becomes end minus start and the timeline is dropped. */
    fun updateSession(id: Long, startMs: Long, endMs: Long) {
        runCatching {
            val values = ContentValues().apply {
                put("day", Fmt.dayKey(startMs))
                put("start_ms", startMs)
                put("end_ms", endMs)
                put("focus_ms", (endMs - startMs).coerceAtLeast(0L))
                put("break_ms", 0L)
                put("pause_ms", 0L)
            }
            writableDatabase.update("sessions", values, "_id = ?", arrayOf(id.toString()))
            writableDatabase.delete("segments", "session_id = ?", arrayOf(id.toString()))
        }
    }

    /** Reads a session back out so it can be put straight back if the user undoes a delete. */
    fun snapshot(id: Long): SessionSnapshot? {
        var out: SessionSnapshot? = null
        runCatching {
            readableDatabase.rawQuery(
                "SELECT day, start_ms, end_ms, focus_ms, break_ms, pause_ms FROM sessions" +
                    " WHERE _id = ?",
                arrayOf(id.toString())
            ).use { c: Cursor ->
                if (c.moveToFirst()) {
                    out = SessionSnapshot(
                        c.getString(0), c.getLong(1), c.getLong(2),
                        c.getLong(3), c.getLong(4), c.getLong(5), segmentsFor(id)
                    )
                }
            }
        }
        return out
    }

    fun restore(snapshot: SessionSnapshot): Long {
        var id = -1L
        runCatching {
            val values = ContentValues().apply {
                put("day", snapshot.day)
                put("start_ms", snapshot.startMs)
                put("end_ms", snapshot.endMs)
                put("focus_ms", snapshot.focusMs)
                put("break_ms", snapshot.breakMs)
                put("pause_ms", snapshot.pauseMs)
            }
            id = writableDatabase.insert("sessions", null, values)
            if (id <= 0L) return@runCatching
            snapshot.segments.forEach { segment ->
                val row = ContentValues().apply {
                    put("session_id", id)
                    put("start_ms", segment.startMs)
                    put("end_ms", segment.endMs)
                    put("kind", segment.kind.toString())
                }
                writableDatabase.insert("segments", null, row)
            }
        }
        return id
    }

    fun deleteSession(id: Long) {
        runCatching {
            writableDatabase.delete("sessions", "_id = ?", arrayOf(id.toString()))
            writableDatabase.delete("segments", "session_id = ?", arrayOf(id.toString()))
        }
    }

    /** The session that was just ended, with its timeline. */
    fun lastSession(): SessionDetail? {
        var out: SessionDetail? = null
        runCatching {
            readableDatabase.rawQuery(
                "SELECT _id, start_ms, end_ms, focus_ms, break_ms, pause_ms FROM sessions" +
                    " ORDER BY start_ms DESC LIMIT 1",
                null
            ).use { c: Cursor ->
                if (c.moveToFirst()) {
                    out = SessionDetail(
                        c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5),
                        segmentsFor(c.getLong(0))
                    )
                }
            }
        }
        return out
    }

    /** The timeline of a single session, empty for sessions recorded before 1.3. */
    fun segmentsFor(sessionId: Long): List<SessionSegment> {
        val out = ArrayList<SessionSegment>()
        runCatching {
            readableDatabase.rawQuery(
                "SELECT kind, start_ms, end_ms FROM segments WHERE session_id = ?" +
                    " ORDER BY start_ms ASC",
                arrayOf(sessionId.toString())
            ).use { c: Cursor ->
                while (c.moveToNext()) {
                    val kind = c.getString(0).firstOrNull() ?: 'F'
                    out.add(SessionSegment(kind, c.getLong(1), c.getLong(2)))
                }
            }
        }
        return out
    }

    /** {focusMs, breakMs, sessionCount} for a single day. */
    fun dayTotals(day: String): LongArray {
        val out = longArrayOf(0L, 0L, 0L)
        runCatching {
            readableDatabase.rawQuery(
                "SELECT COALESCE(SUM(focus_ms),0), COALESCE(SUM(break_ms),0), COUNT(*)" +
                    " FROM sessions WHERE day = ?",
                arrayOf(day)
            ).use { c: Cursor ->
                if (c.moveToFirst()) {
                    out[0] = c.getLong(0)
                    out[1] = c.getLong(1)
                    out[2] = c.getLong(2)
                }
            }
        }
        return out
    }

    /** day key -> focus milliseconds for one calendar month. */
    fun monthFocus(year: Int, month: Int): Map<String, Long> {
        val out = HashMap<String, Long>()
        val from = String.format(Locale.US, "%04d-%02d-01", year, month + 1)
        val to = String.format(Locale.US, "%04d-%02d-31", year, month + 1)
        runCatching {
            readableDatabase.rawQuery(
                "SELECT day, SUM(focus_ms) FROM sessions WHERE day >= ? AND day <= ? GROUP BY day",
                arrayOf(from, to)
            ).use { c ->
                while (c.moveToNext()) out[c.getString(0)] = c.getLong(1)
            }
        }
        return out
    }

    /** day key -> focus milliseconds between two day keys, inclusive. */
    fun focusByDay(fromDay: String, toDay: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        runCatching {
            readableDatabase.rawQuery(
                "SELECT day, SUM(focus_ms) FROM sessions WHERE day >= ? AND day <= ?" +
                    " GROUP BY day ORDER BY day",
                arrayOf(fromDay, toDay)
            ).use { c: Cursor ->
                while (c.moveToNext()) out[c.getString(0)] = c.getLong(1)
            }
        }
        return out
    }

    fun noteDays(year: Int, month: Int): Set<String> {
        val out = HashSet<String>()
        val from = String.format(Locale.US, "%04d-%02d-01", year, month + 1)
        val to = String.format(Locale.US, "%04d-%02d-31", year, month + 1)
        runCatching {
            readableDatabase.rawQuery(
                "SELECT day FROM notes WHERE day >= ? AND day <= ?",
                arrayOf(from, to)
            ).use { c ->
                while (c.moveToNext()) out.add(c.getString(0))
            }
        }
        return out
    }

    /** Sessions that started between two wall clock times, oldest first. */
    fun sessionsBetween(fromMs: Long, toMs: Long): List<SessionRow> {
        val out = ArrayList<SessionRow>()
        runCatching {
            readableDatabase.rawQuery(
                "SELECT _id, start_ms, end_ms, focus_ms, break_ms, pause_ms FROM sessions" +
                    " WHERE start_ms >= ? AND start_ms < ? ORDER BY start_ms ASC",
                arrayOf(fromMs.toString(), toMs.toString())
            ).use { c: Cursor ->
                while (c.moveToNext()) {
                    out.add(
                        SessionRow(
                            c.getLong(0), c.getLong(1), c.getLong(2),
                            c.getLong(3), c.getLong(4), c.getLong(5)
                        )
                    )
                }
            }
        }
        return out
    }

    fun sessionsForDay(day: String): List<SessionRow> {
        val out = ArrayList<SessionRow>()
        runCatching {
            readableDatabase.rawQuery(
                "SELECT _id, start_ms, end_ms, focus_ms, break_ms, pause_ms FROM sessions" +
                    " WHERE day = ? ORDER BY start_ms ASC",
                arrayOf(day)
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        SessionRow(
                            c.getLong(0), c.getLong(1), c.getLong(2),
                            c.getLong(3), c.getLong(4), c.getLong(5)
                        )
                    )
                }
            }
        }
        return out
    }

    fun note(day: String): String {
        runCatching {
            readableDatabase.rawQuery("SELECT note FROM notes WHERE day = ?", arrayOf(day)).use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return ""
    }

    fun saveNote(day: String, text: String) {
        runCatching {
            if (text.trim().isEmpty()) {
                writableDatabase.delete("notes", "day = ?", arrayOf(day))
                return
            }
            val values = ContentValues().apply {
                put("day", day)
                put("note", text)
                put("updated_ms", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                "notes", null, values, SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }
}
