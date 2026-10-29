package com.example.persona.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    // Nested navigation graphs
    @Serializable data object ChatImportGraph : Screen()

    // Bottom nav tabs
    @Serializable data object Home : Screen()
    @Serializable data object Chats : Screen()
    @Serializable data object Orders : Screen()
    @Serializable data object KnowledgeManagement : Screen()

    // Additional full destinations
    @Serializable data object AuditLog : Screen()
    @Serializable data object Settings : Screen()
    @Serializable data object Onboarding : Screen()
    @Serializable data object ChatImport : Screen()
    @Serializable data class ChatImportProgress(val contactName: String = "") : Screen()
    @Serializable data object PermissionSetup : Screen()
    @Serializable data class ChatDetail(val contactName: String, val sourceClass: String = "BUSINESS") : Screen()
}
