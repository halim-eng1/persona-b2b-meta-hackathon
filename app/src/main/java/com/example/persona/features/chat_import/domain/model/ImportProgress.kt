package com.example.persona.features.chat_import.domain.model

data class ImportProgress(
    val phase: ImportPhase,
    val currentBatch: Int,
    val totalBatches: Int,
    val chunksProcessed: Int,
    val totalChunks: Int,
    val errorCount: Int = 0
) {
    val progressFraction: Float
        get() = if (totalChunks > 0) chunksProcessed.toFloat() / totalChunks else 0f

    enum class ImportPhase(val displayName: String) {
        READING_FILE("قراءة الملف..."),
        CHUNKING("تقسيم المحتوى..."),
        PERSISTING("حفظ المعرفة..."),
        EMBEDDING("توليد التضمينات..."),
        CHECKPOINT("تحسين قاعدة البيانات..."),
        COMPLETE("اكتمل الاستيراد!")
    }
}
