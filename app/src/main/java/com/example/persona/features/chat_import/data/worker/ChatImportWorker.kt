package com.example.persona.features.chat_import.data.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.persona.core.common.Constants
import com.example.persona.core.database.PersonaDatabase
import com.example.persona.core.database.dao.ChatMessageDao
import com.example.persona.core.database.dao.EmbeddingDao
import com.example.persona.core.database.dao.LinkedContactDao
import com.example.persona.core.database.entity.ChatMessageEntity
import com.example.persona.core.database.entity.EmbeddingEntity
import com.example.persona.core.database.entity.LinkedContactEntity
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.di.IoDispatcher
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.embedding.OnnxModelManager
import com.example.persona.features.chat_import.data.parser.ChatFileParser
import com.example.persona.features.chat_import.data.parser.ParseFileResult
import com.example.persona.features.chat_import.data.processor.MessageProcessor
import com.example.persona.features.chat_import.domain.model.ImportProgress
import com.example.persona.features.chat_import.domain.model.ParsedMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

@HiltWorker
class ChatImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatFileParser: ChatFileParser,
    private val messageProcessor: MessageProcessor,
    private val embeddingEngine: EmbeddingEngine,
    private val modelManager: OnnxModelManager,
    private val chatMessageDao: ChatMessageDao,
    private val embeddingDao: EmbeddingDao,
    private val linkedContactDao: LinkedContactDao,
    private val database: PersonaDatabase,
    private val prefsRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(context, params) {

    companion object {
        // Input data keys
        const val KEY_FILE_URI = "file_uri"

        // Progress data keys
        const val KEY_PHASE = "phase"
        const val KEY_CURRENT_BATCH = "current_batch"
        const val KEY_TOTAL_BATCHES = "total_batches"
        const val KEY_MESSAGES_PROCESSED = "messages_processed"
        const val KEY_TOTAL_MESSAGES = "total_messages"

        // Output data keys
        const val KEY_RESULT_CONTACT = "result_contact"
        const val KEY_RESULT_TOTAL = "result_total"
        const val KEY_RESULT_USER_MSGS = "result_user_msgs"
        const val KEY_RESULT_FORMAT = "result_format"
        const val KEY_ERROR_MESSAGE = "error_message"

        const val UNIQUE_WORK_NAME = "chat_import"
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        PersonaLogger.d(PersonaLogTags.IMPORT, "Import started")
        val fileUriString = inputData.getString(KEY_FILE_URI)
            ?: return@withContext failWithError("No file URI provided")
        val ownerName = prefsRepository.getOwnerNameSync()
        if (ownerName.isBlank()) {
            return@withContext failWithError("Owner name not configured. Please set your WhatsApp name in Settings.")
        }

        try {
            // ── Phase 1: Read file ──────────────────────────────
            reportProgress(ImportProgress.ImportPhase.READING_FILE, 0, 0, 0, 0)

            val fileUri = Uri.parse(fileUriString)
            val lineSequence = applicationContext.contentResolver
                .openInputStream(fileUri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.lineSequence()
                ?: return@withContext failWithError("Cannot read file")

            // ── Phase 2: Parse ──────────────────────────────────
            reportProgress(ImportProgress.ImportPhase.DETECTING_FORMAT, 0, 0, 0, 0)

            val parseResult = chatFileParser.parse(lineSequence, ownerName)

            when (parseResult) {
                is ParseFileResult.FormatError ->
                    return@withContext failWithError("Unsupported chat format")
                is ParseFileResult.Success -> { /* continue */ }
            }

            val parsed = (parseResult as ParseFileResult.Success)
            val messages = parsed.messages
            PersonaLogger.d(PersonaLogTags.IMPORT, "Messages parsed")

            // ── Data Quality Gate ───────────────────────────────
            if (messages.size < Constants.MIN_MESSAGES_REQUIRED) {
                return@withContext failWithError(
                    "Insufficient messages: ${messages.size} found, ${Constants.MIN_MESSAGES_REQUIRED} required"
                )
            }

            // ── Phase 3: Process (normalize + intent + group) ──
            reportProgress(ImportProgress.ImportPhase.PARSING_MESSAGES, 0, 0, 0, messages.size)

            val processed = messageProcessor.process(messages)
            PersonaLogger.d(PersonaLogTags.IMPORT, "Messages grouped")

            // ── Phase 4: Load model ─────────────────────────────
            if (!modelManager.isLoaded()) {
                val loaded = modelManager.loadModel()
                if (!loaded) {
                    return@withContext failWithError("Failed to load AI model")
                }
            }

            // Identify the contact name (the unique non-owner sender name)
            val contactName = parsed.messages
                .firstOrNull { !it.isUserMessage }?.senderName ?: "Unknown"

            // ── Phase 5: Clear old data ─────────────────────────
            embeddingDao.deleteEmbeddingsForContact(contactName)
            chatMessageDao.deleteMessagesForContact(contactName)

            // ── Phase 6: Persist all messages to DB ─────────────
            reportProgress(ImportProgress.ImportPhase.PERSISTING, 0, 0, 0, processed.size)

            val messageEntities = processed.map { it.toEntity(contactName) }
            chatMessageDao.insertAll(messageEntities)

            // Re-query user messages to get database auto-generated IDs and their corresponding triggers
            val storedUserMessagesWithTriggers = chatMessageDao.getUserMessagesWithTriggersForContact(contactName)
            val totalBatches = (storedUserMessagesWithTriggers.size + Constants.EMBEDDING_BATCH_SIZE - 1) /
                Constants.EMBEDDING_BATCH_SIZE

            // ── Phase 7: Batched embedding generation ───────────
            var processedCount = 0
            for (batchIndex in 0 until totalBatches) {
                if (isStopped) return@withContext Result.failure()

                val start = batchIndex * Constants.EMBEDDING_BATCH_SIZE
                val end = minOf(start + Constants.EMBEDDING_BATCH_SIZE, storedUserMessagesWithTriggers.size)
                val batch = storedUserMessagesWithTriggers.subList(start, end)

                reportProgress(
                    ImportProgress.ImportPhase.EMBEDDING,
                    batchIndex + 1, totalBatches,
                    processedCount, storedUserMessagesWithTriggers.size
                )

                // Generate embeddings (Embed the TRIGGER text, fallback to message text if no trigger)
                val textsToEmbed = batch.map { it.triggerText ?: (it.message.normalizedText ?: it.message.messageText) }
                val embeddings = embeddingEngine.embedBatch(textsToEmbed)

                // Save embeddings
                val embeddingEntities = batch.zip(embeddings).mapNotNull { (item, vec) ->
                    vec?.let {
                        EmbeddingEntity(
                            messageId = item.message.id,
                            vector = it,
                            modelVersion = EmbeddingEntity.CURRENT_MODEL_VERSION,
                            createdAt = Clock.System.now()
                        )
                    }
                }
                embeddingDao.insertAll(embeddingEntities)

                processedCount += batch.size

                // Free memory
                System.gc()
            }
            PersonaLogger.d(PersonaLogTags.IMPORT, "Embeddings generated")

            // ── Phase 8: Register linked contact ────────────────
            linkedContactDao.insert(
                LinkedContactEntity(
                    whatsappDisplayName = contactName,
                    isActive = true,
                    autoReplyEnabled = true,
                    cooldownCount = 0,
                    cooldownResetAt = null,
                    linkedAt = Clock.System.now(),
                    totalMessagesParsed = processed.size
                )
            )

            // ── Phase 9: SQLite WAL Checkpoint (Exactly Once) ──
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                    if (cursor.moveToFirst()) {
                        Log.i("ChatImportWorker", "WAL checkpoint executed successfully: busy=${cursor.getInt(0)}, log=${cursor.getInt(1)}, checkpointed=${cursor.getInt(2)}")
                    } else {
                        Log.i("ChatImportWorker", "WAL checkpoint executed successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e("PersonaImport", "WAL Checkpoint Failed -> Exception: ${e.javaClass.simpleName} | Message: ${e.message}")
            }

            reportProgress(
                ImportProgress.ImportPhase.COMPLETE,
                totalBatches, totalBatches,
                storedUserMessagesWithTriggers.size, storedUserMessagesWithTriggers.size
            )
            PersonaLogger.d(PersonaLogTags.IMPORT, "Import completed")

            val outputData = workDataOf(
                KEY_RESULT_CONTACT to contactName,
                KEY_RESULT_TOTAL to processed.size,
                KEY_RESULT_USER_MSGS to storedUserMessagesWithTriggers.size,
                KEY_RESULT_FORMAT to parsed.format.name
            )

            Result.success(outputData)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PersonaImport", "Global Import Core Failure -> Exception: ${e.javaClass.simpleName} | Message: ${e.message}")
            failWithError(e.message ?: "Unknown error")
        }
    }

    private fun failWithError(message: String): Result {
        return Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
    }

    private suspend fun reportProgress(
        phase: ImportProgress.ImportPhase,
        currentBatch: Int,
        totalBatches: Int,
        messagesProcessed: Int,
        totalMessages: Int
    ) {
        setProgress(workDataOf(
            KEY_PHASE to phase.name,
            KEY_CURRENT_BATCH to currentBatch,
            KEY_TOTAL_BATCHES to totalBatches,
            KEY_MESSAGES_PROCESSED to messagesProcessed,
            KEY_TOTAL_MESSAGES to totalMessages
        ))
    }

    private fun ParsedMessage.toEntity(contact: String) = ChatMessageEntity(
        contactName = contact,
        messageText = messageText,
        isUserMessage = isUserMessage,
        timestamp = timestamp,
        intentType = context.intent.name,
        topicType = context.topic.name,
        sentimentType = context.sentiment.name,
        contextConfidence = context.confidence,
        isGrouped = isGrouped,
        sourceFormat = sourceFormat.name,
        normalizedText = normalizedText
    )
}
