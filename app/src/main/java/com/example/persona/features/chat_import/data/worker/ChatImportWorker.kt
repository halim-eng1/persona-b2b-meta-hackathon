package com.example.persona.features.chat_import.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.persona.R
import com.example.persona.core.common.Constants
import com.example.persona.core.database.PersonaDatabase
import com.example.persona.core.database.dao.BusinessKnowledgeDao
import com.example.persona.core.database.dao.EmbeddingIndexDao
import com.example.persona.core.database.entity.BusinessKnowledgeEntity
import com.example.persona.core.database.entity.EmbeddingIndexEntity
import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.di.IoDispatcher
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.features.chat_import.data.embedding.EmbeddingEngine
import com.example.persona.features.chat_import.data.embedding.OnnxModelManager
import com.example.persona.features.chat_import.data.parser.KnowledgeChunk
import com.example.persona.features.chat_import.data.parser.MarkdownChunker
import com.example.persona.features.chat_import.data.parser.StoreTopicsExtractor
import com.example.persona.features.chat_import.data.parser.StoreWebsiteExtractor
import com.example.persona.features.chat_import.data.parser.WhatsAppTxtExportParser
import com.example.persona.features.chat_import.domain.model.ImportProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that ingests a Markdown, plain-text, or WhatsApp chat export
 * document into the global RAG knowledge base.
 *
 * **Pipeline:**
 * 1. READ_FILE    — Read raw text from content URI
 * 2. CHUNKING     — Detect format (WhatsApp vs Markdown) and split into [KnowledgeChunk]s
 * 3. EMBEDDING    — Generate embeddings first into memory to prevent partial DB corruption
 * 4. PERSISTING   — Atomic transaction: delete previous file chunks and persist texts + embeddings
 * 5. CHECKPOINT   — PRAGMA wal_checkpoint(TRUNCATE)
 * 6. COMPLETE     — Report summary output
 */
@HiltWorker
class ChatImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val markdownChunker: MarkdownChunker,
    private val whatsAppTxtExportParser: WhatsAppTxtExportParser,
    private val embeddingEngine: EmbeddingEngine,
    private val modelManager: OnnxModelManager,
    private val businessKnowledgeDao: BusinessKnowledgeDao,
    private val embeddingIndexDao: EmbeddingIndexDao,
    private val database: PersonaDatabase,
    private val userPreferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(context, params) {

    companion object {
        // Input
        const val KEY_FILE_URI = "file_uri"

        // Progress
        const val KEY_PHASE = "phase"
        const val KEY_CURRENT_BATCH = "current_batch"
        const val KEY_TOTAL_BATCHES = "total_batches"
        const val KEY_MESSAGES_PROCESSED = "messages_processed"
        const val KEY_TOTAL_MESSAGES = "total_messages"

        // Output
        const val KEY_RESULT_CONTACT = "result_contact"
        const val KEY_RESULT_TOTAL = "result_total"
        const val KEY_RESULT_USER_MSGS = "result_user_msgs"
        const val KEY_RESULT_FORMAT = "result_format"
        const val KEY_ERROR_MESSAGE = "error_message"

        const val UNIQUE_WORK_NAME = "chat_import"

        private const val FOREGROUND_CHUNK_THRESHOLD = 50
    }

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        PersonaLogger.d(PersonaLogTags.IMPORT, "Knowledge import started")

        val fileUriString = inputData.getString(KEY_FILE_URI)
            ?: return@withContext failWithError("No file URI provided")
        val fileUri = Uri.parse(fileUriString)

        // Read the raw file text first (lightweight) so we can decide on foreground promotion
        val rawText = try {
            applicationContext.contentResolver.openInputStream(fileUri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                ?: return@withContext failWithError("Cannot open file for reading")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return@withContext failWithError("File read error: ${e.message}")
        }

        if (rawText.isBlank()) {
            return@withContext failWithError("File is empty or contains only whitespace")
        }

        // Promote to foreground for large files
        if (rawText.length > FOREGROUND_CHUNK_THRESHOLD * Constants.EMBEDDING_MAX_CHUNK_CHARS) {
            try { setForeground(getForegroundInfo()) } catch (e: Exception) {
                if (e is CancellationException) throw e
                PersonaLogger.e(PersonaLogTags.IMPORT, "setForeground failed", e)
            }
        }

        // Infer the source filename from the URI (best-effort)
        val sourceFileName = fileUri.lastPathSegment?.substringAfterLast('/')

        try {
            // ── Phase 1: Read file ─────────────────────────────────────────────
            reportProgress(ImportProgress.ImportPhase.READING_FILE, 0, 0, 0, 0)
            PersonaLogger.d(PersonaLogTags.IMPORT, "File read: ${rawText.length} chars, file=$sourceFileName")

            // ── Phase 2: Chunking ──────────────────────────────────────────────
            reportProgress(ImportProgress.ImportPhase.CHUNKING, 0, 0, 0, 0)
            val isWhatsApp = whatsAppTxtExportParser.isSupported(rawText)
            val chunks: List<KnowledgeChunk> = if (isWhatsApp) {
                PersonaLogger.i(PersonaLogTags.IMPORT, "Detected WhatsApp chat export. Using WhatsAppTxtExportParser.")
                whatsAppTxtExportParser.extractKnowledgeChunks(rawText, sourceFileName)
            } else {
                PersonaLogger.i(PersonaLogTags.IMPORT, "Processing document using MarkdownChunker.")
                markdownChunker.chunk(
                    documentText = rawText,
                    sourceFileName = sourceFileName,
                    sourceType = "CORE_DOCUMENT"
                )
            }

            if (chunks.isEmpty()) {
                return@withContext failWithError(
                    "Document produced no chunks after splitting. " +
                        "Ensure the file contains readable text content."
                )
            }

            PersonaLogger.d(PersonaLogTags.IMPORT, "Chunking complete: ${chunks.size} chunks (isWhatsApp=$isWhatsApp)")

            // Extract topics and website (prompt enhancement)
            val extractedTopics = StoreTopicsExtractor.extract(rawText)
            try {
                userPreferencesRepository.setStoreKnowledgeTopics(extractedTopics)
                PersonaLogger.i(PersonaLogTags.IMPORT, "Extracted and saved ${extractedTopics.size} store knowledge topics.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                PersonaLogger.w(PersonaLogTags.IMPORT, "Failed to persist store knowledge topics: ${e.message}")
            }

            val extractedWebsite = StoreWebsiteExtractor.extract(rawText)
            try {
                userPreferencesRepository.setStoreWebsite(extractedWebsite)
                PersonaLogger.i(PersonaLogTags.IMPORT, "Extracted and saved store website: $extractedWebsite")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                PersonaLogger.w(PersonaLogTags.IMPORT, "Failed to persist store website: ${e.message}")
            }

            // ── Phase 3: Load model & Generate Embeddings in Memory ─────────────
            // We generate embeddings BEFORE mutating the database. This guarantees atomic resilience:
            // if cancelled or stopped midway, the database remains in its previous valid state.
            if (!modelManager.isLoaded()) {
                val loaded = modelManager.loadModel()
                if (!loaded) return@withContext failWithError("Failed to load AI embedding model")
            }

            val totalChunks = chunks.size
            val totalBatches = (totalChunks + Constants.EMBEDDING_BATCH_SIZE - 1) / Constants.EMBEDDING_BATCH_SIZE
            var processedCount = 0
            val allEmbeddingEntities = ArrayList<EmbeddingIndexEntity>(totalChunks)

            for (batchIndex in 0 until totalBatches) {
                ensureActive()
                if (isStopped) {
                    PersonaLogger.w(PersonaLogTags.IMPORT, "Import worker stopped during embedding phase. Aborting without mutating database.")
                    return@withContext Result.failure()
                }

                val start = batchIndex * Constants.EMBEDDING_BATCH_SIZE
                val end = minOf(start + Constants.EMBEDDING_BATCH_SIZE, totalChunks)
                val batchChunks = chunks.subList(start, end)

                reportProgress(
                    ImportProgress.ImportPhase.EMBEDDING,
                    batchIndex + 1, totalBatches,
                    processedCount, totalChunks
                )

                val textsToEmbed = batchChunks.map { it.chunkText }

                ensureActive()
                val embeddings = embeddingEngine.embedBatch(textsToEmbed)

                for (i in batchChunks.indices) {
                    val vec = embeddings.getOrNull(i) ?: continue
                    allEmbeddingEntities.add(
                        EmbeddingIndexEntity(
                            chunkId = batchChunks[i].chunkId,
                            vectorBlob = vec,
                            modelVersion = EmbeddingIndexEntity("", FloatArray(0)).modelVersion
                        )
                    )
                }

                processedCount += batchChunks.size
            }

            PersonaLogger.d(PersonaLogTags.IMPORT, "Embeddings generated: $processedCount")

            if (allEmbeddingEntities.isEmpty()) {
                return@withContext failWithError("Failed to generate vector embeddings for catalog chunks.")
            }

            // ── Phase 4: Atomic Persist (Transaction) ───────────────────────────
            reportProgress(ImportProgress.ImportPhase.PERSISTING, totalBatches, totalBatches, totalChunks, totalChunks)

            database.withTransaction {
                // Delete previous CORE_DOCUMENT entries for this filename or all if unspecific
                if (sourceFileName != null) {
                    val oldChunks = businessKnowledgeDao.getChunksBySourceType("CORE_DOCUMENT")
                        .filter { it.sourceFileName == sourceFileName }
                    for (c in oldChunks) {
                        embeddingIndexDao.deleteByChunkId(c.chunkId)
                        businessKnowledgeDao.deleteByChunkId(c.chunkId)
                    }
                } else {
                    val oldChunks = businessKnowledgeDao.getChunksBySourceType("CORE_DOCUMENT")
                    for (c in oldChunks) {
                        embeddingIndexDao.deleteByChunkId(c.chunkId)
                    }
                    businessKnowledgeDao.deleteBySourceType("CORE_DOCUMENT")
                }

                val entities = chunks.map { chunk ->
                    BusinessKnowledgeEntity(
                        chunkId = chunk.chunkId,
                        headerContext = chunk.headerContext,
                        chunkText = chunk.chunkText,
                        sourceType = chunk.sourceType,
                        sourceFileName = chunk.sourceFileName
                    )
                }
                businessKnowledgeDao.insertAll(entities)
                embeddingIndexDao.insertAll(allEmbeddingEntities)
            }

            PersonaLogger.d(PersonaLogTags.IMPORT, "Chunks and embeddings atomically persisted: ${chunks.size}")

            // ── Phase 5: WAL Checkpoint ───────────────────────────────────────
            reportProgress(ImportProgress.ImportPhase.CHECKPOINT, totalBatches, totalBatches, totalChunks, totalChunks)
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                    if (cursor.moveToFirst()) {
                        Log.i("ChatImportWorker",
                            "WAL checkpoint: busy=${cursor.getInt(0)}, log=${cursor.getInt(1)}, " +
                                "checkpointed=${cursor.getInt(2)}")
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ChatImportWorker", "WAL Checkpoint failed: ${e.message}")
            }

            // ── Phase 6: Complete ─────────────────────────────────────────────
            reportProgress(ImportProgress.ImportPhase.COMPLETE, totalBatches, totalBatches, totalChunks, totalChunks)
            PersonaLogger.d(PersonaLogTags.IMPORT, "Knowledge import completed: $totalChunks chunks")

            Result.success(
                workDataOf(
                    KEY_RESULT_TOTAL to totalChunks,
                    KEY_RESULT_USER_MSGS to processedCount,
                    KEY_RESULT_FORMAT to if (isWhatsApp) "WHATSAPP" else "MARKDOWN"
                )
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.javaClass.name == "androidx.work.impl.WorkerStoppedException") {
                PersonaLogger.w(PersonaLogTags.IMPORT, "Import worker stopped cleanly")
                return@withContext Result.failure()
            }
            Log.e("ChatImportWorker", "Import failure: ${e.javaClass.simpleName} | ${e.message}")
            failWithError(e.message ?: "Unknown error")
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = "knowledge_import_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Knowledge Import Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of importing business knowledge documents"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(applicationContext.getString(R.string.notification_import_title))
            .setContentText(applicationContext.getString(R.string.notification_import_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(2002, notification)
    }

    private fun failWithError(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))

    private suspend fun reportProgress(
        phase: ImportProgress.ImportPhase,
        currentBatch: Int,
        totalBatches: Int,
        chunksProcessed: Int,
        totalChunks: Int
    ) {
        setProgress(
            workDataOf(
                KEY_PHASE to phase.name,
                KEY_CURRENT_BATCH to currentBatch,
                KEY_TOTAL_BATCHES to totalBatches,
                KEY_MESSAGES_PROCESSED to chunksProcessed,
                KEY_TOTAL_MESSAGES to totalChunks
            )
        )
    }
}
