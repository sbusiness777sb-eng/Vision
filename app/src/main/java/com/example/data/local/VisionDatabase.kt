package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TaskEntity::class,
        GoalEntity::class,
        RoutineEntity::class,
        AlarmEntity::class,
        DistractingAppEntity::class,
        FocusSessionEntity::class,
        DailyStatEntity::class,
        AlfredLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(VisionConverters::class)
abstract class VisionDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun goalRoutineDao(): GoalRoutineDao
    abstract fun alarmDao(): AlarmDao
    abstract fun focusDao(): FocusDao
    abstract fun alfredDao(): AlfredDao

    companion object {
        @Volatile
        private var INSTANCE: VisionDatabase? = null

        fun getInstance(context: Context): VisionDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VisionDatabase::class.java,
                    "vision_offline_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
