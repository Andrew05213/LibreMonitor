/* SPDX-License-Identifier: Apache-2.0 */
package org.libremonitor.app.data.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper

/** App-private, versioned storage. An unknown schema is never silently discarded. */
internal class BatteryHistoryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext ?: context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $TIMESTAMP INTEGER NOT NULL,
                $LEVEL INTEGER,
                $TEMPERATURE REAL,
                $VOLTAGE REAL,
                $CURRENT REAL,
                $POWER REAL,
                $STATUS TEXT NOT NULL
            )
            """.trimIndent(),
        )
        // The id makes both latest-N and export deterministic for equal timestamps.
        db.execSQL("CREATE INDEX battery_history_timestamp ON $TABLE ($TIMESTAMP, $ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("No battery-history migration from $oldVersion to $newVersion")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("No battery-history downgrade from $oldVersion to $newVersion")
    }

    companion object {
        private const val DATABASE_NAME = "battery_history.db"
        private const val DATABASE_VERSION = 1
        const val TABLE = "battery_history"
        const val ID = "_id"
        const val TIMESTAMP = "timestamp_epoch_ms"
        const val LEVEL = "level_percent"
        const val TEMPERATURE = "temperature_c"
        const val VOLTAGE = "voltage_v"
        const val CURRENT = "current_ma_raw"
        const val POWER = "power_w_estimated"
        const val STATUS = "status"

        val RECORD_COLUMNS = arrayOf(
            TIMESTAMP, LEVEL, TEMPERATURE, VOLTAGE, CURRENT, POWER, STATUS,
        )
    }
}
