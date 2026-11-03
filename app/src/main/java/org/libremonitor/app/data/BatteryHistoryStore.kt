/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libremonitor.app.data.storage.BatteryHistoryDatabase
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.CURRENT
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.ID
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.LEVEL
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.POWER
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.RECORD_COLUMNS
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.STATUS
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.TABLE
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.TEMPERATURE
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.TIMESTAMP
import org.libremonitor.app.data.storage.BatteryHistoryDatabase.Companion.VOLTAGE
import org.libremonitor.core.BatteryCsv
import org.libremonitor.core.BatteryRecord

/**
 * All database and stream IO, including close, is serialized on Dispatchers.IO.
 * Only app-private storage is opened; the caller supplies any export destination.
 */
class BatteryHistoryStore(context: Context) : AutoCloseable {
    private val helper = BatteryHistoryDatabase(context)
    private val mutex = Mutex()
    private var closed = false // Guarded by mutex, including during close.

    suspend fun insert(record: BatteryRecord) {
        withDatabase { db ->
            db.inTransaction {
                val values = ContentValues().apply {
                    put(TIMESTAMP, record.timestampEpochMillis)
                    if (record.levelPercent == null) putNull(LEVEL) else put(LEVEL, record.levelPercent)
                    putMeasurement(TEMPERATURE, record.temperatureC)
                    putMeasurement(VOLTAGE, record.voltageV)
                    putMeasurement(CURRENT, record.currentMa)
                    putMeasurement(POWER, record.powerW)
                    put(STATUS, record.status)
                }
                db.insertOrThrow(TABLE, null, values)
                db.enforceRetention()
            }
        }
    }

    /** Select newest N, but return the selected window oldest to newest. */
    suspend fun latest(limit: Int = 360): List<BatteryRecord> = withDatabase { db ->
        db.inTransaction { db.enforceRetention() }
        val boundedLimit = limit.coerceIn(0, MAX_RECORDS)
        if (boundedLimit == 0) {
            emptyList()
        } else {
            db.query(
                TABLE, RECORD_COLUMNS, null, null, null, null,
                "$TIMESTAMP DESC, $ID DESC", boundedLimit.toString(),
            ).use { cursor ->
                val records = ArrayList<BatteryRecord>(minOf(boundedLimit, 360))
                while (cursor.moveToNext()) records.add(cursor.readRecord())
                records.reverse()
                records
            }
        }
    }

    suspend fun pruneOlderThan(cutoffEpochMillis: Long) {
        withDatabase { db ->
            db.inTransaction {
                db.delete(TABLE, "$TIMESTAMP < ?", arrayOf(cutoffEpochMillis.toString()))
                db.enforceRetention()
            }
        }
    }

    suspend fun clear() {
        withDatabase { db -> db.delete(TABLE, null, null) }
    }

    /**
     * Bounded-memory UTF-8 export. Cursor/writer ownership is deliberately different:
     * the cursor belongs to us; the output stream belongs to the caller and stays open.
     */
    suspend fun exportCsv(output: OutputStream) {
        val callerContext = currentCoroutineContext()
        withDatabase { db ->
            db.inTransaction { db.enforceRetention() }
            val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
            db.query(
                TABLE, RECORD_COLUMNS, null, null, null, null,
                "$TIMESTAMP ASC, $ID ASC",
            ).use { cursor ->
                BatteryCsv.writeHeader(writer)
                while (cursor.moveToNext()) {
                    callerContext.ensureActive()
                    BatteryCsv.writeRecord(writer, cursor.readRecord())
                }
                writer.flush()
            }
            // Do not use writer.use()/close(): either would close the caller stream.
        }
    }

    /** Idempotent; waits for any in-flight operation without closing its stream. */
    override fun close() {
        runBlocking(Dispatchers.IO) {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    helper.close()
                }
            }
        }
    }

    private suspend fun <T> withDatabase(block: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!closed) { "BatteryHistoryStore is closed" }
                block(helper.writableDatabase)
            }
        }

    private fun SQLiteDatabase.enforceRetention() {
        val cutoff = System.currentTimeMillis() - RETENTION_MILLIS
        delete(TABLE, "$TIMESTAMP < ?", arrayOf(cutoff.toString()))
        val excess = DatabaseUtils.queryNumEntries(this, TABLE) - MAX_RECORDS
        if (excess > 0L) {
            execSQL(
                "DELETE FROM $TABLE WHERE $ID IN " +
                    "(SELECT $ID FROM $TABLE ORDER BY $TIMESTAMP ASC, $ID ASC LIMIT ?)",
                arrayOf<Any>(excess),
            )
        }
    }

    private fun <T> SQLiteDatabase.inTransaction(block: () -> T): T {
        beginTransaction()
        try {
            val result = block()
            setTransactionSuccessful()
            return result
        } finally {
            endTransaction()
        }
    }

    private fun ContentValues.putMeasurement(key: String, value: Double?) {
        // Non-finite measurements are invalid data too, never substitute zero.
        if (value == null || !value.isFinite()) putNull(key) else put(key, value)
    }

    private fun Cursor.readRecord(): BatteryRecord = BatteryRecord(
        timestampEpochMillis = getLong(0),
        levelPercent = if (isNull(1)) null else getInt(1),
        temperatureC = nullableDouble(2),
        voltageV = nullableDouble(3),
        currentMa = nullableDouble(4),
        powerW = nullableDouble(5),
        status = getString(6),
    )

    private fun Cursor.nullableDouble(column: Int): Double? =
        if (isNull(column)) null else getDouble(column).takeIf { it.isFinite() }

    private companion object {
        const val MAX_RECORDS = 50_000
        const val RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}
