package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InspectionRecordEntity::class,
        DetectedDefectEntity::class,
        CsvAuditOutboxEntity::class,
        CsvAuditLedgerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PcbInspectorDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao

    companion object {
        const val DATABASE_NAME = "bharat_pcb_inspector.db"

        fun build(context: Context): PcbInspectorDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PcbInspectorDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
}
