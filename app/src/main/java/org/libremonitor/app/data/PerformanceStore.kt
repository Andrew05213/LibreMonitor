/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libremonitor.core.PerformanceCodec
import org.libremonitor.core.PerformancePoint
import org.libremonitor.core.PerformanceReview
import org.libremonitor.core.PerformanceSession

/** Separate private database: battery schema v1 and its CSV contract are untouched. */
internal class PerformanceStore(context: Context, databaseName: String = "performance.db") {
    private val helper = Database(context, databaseName)
    private val mutex = Mutex()

    suspend fun initialize() = database { db ->
        db.execSQL("UPDATE sessions SET status = ?, duration_ms = COALESCE((SELECT MAX(elapsed_ms) FROM samples WHERE session_id = sessions._id), 0) WHERE status = ?",
            arrayOf(PerformanceSession.INTERRUPTED, PerformanceSession.RECORDING))
    }

    suspend fun start(name: String, epoch: Long, interval: Long): PerformanceSession = database { db ->
        val id = db.insertOrThrow("sessions", null, ContentValues().apply {
            put("name", name.take(80)); put("started_ms", epoch); put("interval_ms", interval)
            put("status", PerformanceSession.RECORDING)
        })
        prune(db)
        PerformanceSession(id, name.take(80), epoch, sampleIntervalMs = interval)
    }

    /** Returns false if stopped or capped; the status guard prevents writes after Finish. */
    suspend fun append(id: Long, point: PerformancePoint): Boolean = database { db ->
        db.beginTransaction()
        try {
            val allowed = db.rawQuery("SELECT sample_count FROM sessions WHERE _id = ? AND status = ?",
                arrayOf(id.toString(), PerformanceSession.RECORDING)).use { it.moveToFirst() && it.getInt(0) < MAX_POINTS }
            if (!allowed) return@database false
            db.insertOrThrow("samples", null, ContentValues().apply {
                put("session_id", id); put("elapsed_ms", point.elapsedMillis); put("payload", PerformanceCodec.encode(point))
            })
            db.execSQL("UPDATE sessions SET sample_count = sample_count + 1, duration_ms = ? WHERE _id = ?", arrayOf(point.elapsedMillis, id))
            prune(db)
            db.setTransactionSuccessful()
            true
        } finally { db.endTransaction() }
    }

    suspend fun finish(id: Long, epoch: Long, duration: Long, interrupted: Boolean = false) = database { db ->
        db.update("sessions", ContentValues().apply {
            put("ended_ms", epoch); put("duration_ms", duration.coerceAtLeast(0))
            put("status", if (interrupted) PerformanceSession.INTERRUPTED else PerformanceSession.COMPLETED)
        }, "_id = ? AND status = ?", arrayOf(id.toString(), PerformanceSession.RECORDING))
        Unit
    }

    suspend fun sessions(): List<PerformanceSession> = database { db ->
        db.rawQuery("SELECT * FROM sessions ORDER BY _id DESC LIMIT 20", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.session()) }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) { mutex.withLock { helper.close() } }

    suspend fun review(id: Long): PerformanceReview = database { db ->
        val session = db.rawQuery("SELECT * FROM sessions WHERE _id = ?", arrayOf(id.toString())).use {
            check(it.moveToFirst()) { "记录已不存在" }; it.session()
        }
        val caller = currentCoroutineContext()
        db.rawQuery("SELECT payload FROM samples WHERE session_id = ? ORDER BY elapsed_ms, _id", arrayOf(id.toString())).use { cursor ->
            PerformanceReview.build(session, sequence {
                while (cursor.moveToNext()) {
                    caller.ensureActive()
                    yield(PerformanceCodec.decode(cursor.getBlob(0)))
                }
            })
        }
    }

    private suspend fun <T> database(block: suspend (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(helper.writableDatabase) }
    }

    private fun prune(db: SQLiteDatabase) {
        // Delete only the oldest ended sessions; never trim the active session silently.
        while (true) {
            val excess = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(sample_count), 0) FROM sessions", null).use {
                it.moveToFirst(); it.getInt(0) > 20 || it.getInt(1) > MAX_POINTS
            }
            if (!excess) return
            val oldest = db.rawQuery("SELECT _id FROM sessions WHERE status != ? ORDER BY _id LIMIT 1",
                arrayOf(PerformanceSession.RECORDING)).use { if (it.moveToFirst()) it.getLong(0) else null } ?: return
            db.delete("sessions", "_id = ?", arrayOf(oldest.toString()))
        }
    }

    private fun Cursor.session() = PerformanceSession(
        getLong(getColumnIndexOrThrow("_id")), getString(getColumnIndexOrThrow("name")),
        getLong(getColumnIndexOrThrow("started_ms")),
        getColumnIndexOrThrow("ended_ms").let { if (isNull(it)) null else getLong(it) },
        getLong(getColumnIndexOrThrow("duration_ms")), getLong(getColumnIndexOrThrow("interval_ms")),
        getInt(getColumnIndexOrThrow("sample_count")), getString(getColumnIndexOrThrow("status")),
    )

    private class Database(context: Context, name: String) : SQLiteOpenHelper(context.applicationContext ?: context, name, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE sessions (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, started_ms INTEGER NOT NULL, ended_ms INTEGER, duration_ms INTEGER NOT NULL DEFAULT 0, interval_ms INTEGER NOT NULL, sample_count INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)")
            db.execSQL("CREATE TABLE samples (_id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES sessions(_id) ON DELETE CASCADE, elapsed_ms INTEGER NOT NULL, payload BLOB NOT NULL)")
            db.execSQL("CREATE INDEX performance_samples_session ON samples(session_id, elapsed_ms, _id)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("No performance migration from $oldVersion to $newVersion")
        }
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            throw SQLiteException("No performance downgrade from $oldVersion to $newVersion")
        }
    }

    companion object { const val MAX_POINTS = 100_000 }
}
