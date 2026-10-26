package com.example.persona.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    // Bottom nav tabs
    @Serializable data object Home : Screen()
    @Serializable data object KnowledgeManagement : Screen()
    @Serializable data object AuditLog : Screen()
    @Serializable data object Orders : Screen()
    @Serializable data object Settings : Screen()

    // Full-screen destinations (no bottom bar)
    @Serializable data object Onboarding : Screen()
    @Serializable data object ChatImport : Screen()
    @Serializable data class ChatImportProgress(val contactName: String) : Screen()
    @Serializable data object PermissionSetup : Screen()
    @Serializable data class ContactDetail(val contactId: Long) : Screen()
}
