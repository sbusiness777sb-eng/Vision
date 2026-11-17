package com.example.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtil {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val offset = cal.timeZone.getOffset(cal.timeInMillis)
        return (cal.timeInMillis + offset) / MILLIS_PER_DAY
    }

    fun epochDayOffset(daysOffset: Int): Long = todayEpochDay() + daysOffset

    fun epochDayToStartMillis(epochDay: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochDay * MILLIS_PER_DAY
        val offset = cal.timeZone.getOffset(cal.timeInMillis)
        return (epochDay * MILLIS_PER_DAY) - offset
    }

    fun formatEpochDayLabel(epochDay: Long): String {
        val today = todayEpochDay()
        return when (epochDay) {
            today -> "Today"
            today + 1 -> "Tomorrow"
            today - 1 -> "Yesterday"
            else -> {
                val cal = Calendar.getInstance()
                val daysDiff = (epochDay - today).toInt()
                cal.add(Calendar.DAY_OF_YEAR, daysDiff)
                SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(cal.time)
            }
        }
    }

    fun formatShortWeekday(epochDay: Long): String {
        val today = todayEpochDay()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, (epochDay - today).toInt())
        return SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
    }

    fun formatTodayFullDate(): String {
        return SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    fun formatTime12Hour(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm)
    }

    fun formatDurationSeconds(totalSeconds: Int): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    fun currentHourMinute(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }
}
