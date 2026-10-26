package com.example.persona.features.chat_import.data.repository

import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.example.persona.features.chat_import.data.worker.ChatImportWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatImportRepository @Inject constructor(
    private val workManager: WorkManager
) {
    /**
     * Enqueues the chat import as a unique WorkManager job.
     * Returns a Flow of WorkInfo for progress observation.
     */
    fun startImport(
        fileUri: Uri,
        userName: String? = null
    ): Flow<WorkInfo?> {
        val inputData = workDataOf(
            ChatImportWorker.KEY_FILE_URI to fileUri.toString(),
            ChatImportWorker.KEY_USER_NAME to userName
        )

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val importRequest = OneTimeWorkRequestBuilder<ChatImportWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("chat_import")
            .build()

        workManager.enqueueUniqueWork(
            ChatImportWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP, // Don't restart if already running
            importRequest
        )

        return workManager.getWorkInfoByIdFlow(importRequest.id)
    }

    /**
     * Cancels any running import.
     */
    fun cancelImport() {
        workManager.cancelUniqueWork(ChatImportWorker.UNIQUE_WORK_NAME)
    }

    /**
     * Checks if an import is currently running.
     */
    fun getImportStatus(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosForUniqueWorkFlow(ChatImportWorker.UNIQUE_WORK_NAME)
    }
}
