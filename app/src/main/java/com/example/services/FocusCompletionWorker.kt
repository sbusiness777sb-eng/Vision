package com.example.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.VisionApplication
import java.util.concurrent.TimeUnit

class FocusCompletionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VisionApplication ?: return Result.success()
        val settings = app.preferences.getSettingsOnce()
        if (settings.activeFocusRunning && System.currentTimeMillis() >= settings.activeFocusEndEpochMillis - 1000L) {
            app.focusController.stopFocusSession(completedNormally = true)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "vision_focus_completion_work"

        fun schedule(context: Context, durationMinutes: Int) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<FocusCompletionWorker>()
                    .setInitialDelay(durationMinutes.toLong().coerceAtLeast(1L), TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
