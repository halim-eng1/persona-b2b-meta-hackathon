package com.example.persona.features.auto_reply.presentation.audit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Backward-compatible facade delegating to relocated [AuditLogScreen] in `features/audit_log`.
 */
@Composable
fun AuditLogScreen(
    modifier: Modifier = Modifier,
    viewModel: com.example.persona.features.audit_log.presentation.AuditLogViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    com.example.persona.features.audit_log.presentation.AuditLogScreen(
        modifier = modifier,
        viewModel = viewModel
    )
}
