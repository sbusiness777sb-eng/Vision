package com.example.data.local

import androidx.room.TypeConverter

class VisionConverters {
    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority =
        runCatching { TaskPriority.valueOf(value) }.getOrDefault(TaskPriority.MEDIUM)

    @TypeConverter
    fun fromTaskCategory(value: TaskCategory): String = value.name

    @TypeConverter
    fun toTaskCategory(value: String): TaskCategory =
        runCatching { TaskCategory.valueOf(value) }.getOrDefault(TaskCategory.WORK)

    @TypeConverter
    fun fromRoutineType(value: RoutineType): String = value.name

    @TypeConverter
    fun toRoutineType(value: String): RoutineType =
        runCatching { RoutineType.valueOf(value) }.getOrDefault(RoutineType.DAILY_HABIT)

    @TypeConverter
    fun fromAlarmType(value: AlarmType): String = value.name

    @TypeConverter
    fun toAlarmType(value: String): AlarmType =
        runCatching { AlarmType.valueOf(value) }.getOrDefault(AlarmType.WAKE_UP)

    @TypeConverter
    fun fromFocusSessionStatus(value: FocusSessionStatus): String = value.name

    @TypeConverter
    fun toFocusSessionStatus(value: String): FocusSessionStatus =
        runCatching { FocusSessionStatus.valueOf(value) }.getOrDefault(FocusSessionStatus.COMPLETED)
}
