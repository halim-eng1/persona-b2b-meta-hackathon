package com.example.persona.core.debug

/**
 * Single source of truth for debug feature flags.
 *
 * All flags default to false — zero overhead in production.
 * Set a flag to true at compile time to enable the corresponding
 * diagnostic subsystem.
 */
object DebugFlags {

    /**
     * Enables the NotificationSourceDiagnostics subsystem.
     * When true, NLS_DIAG log lines are emitted for every WhatsApp notification.
     * When false, the entire diagnostics pipeline is skipped with zero overhead.
     */
    const val ENABLE_NOTIFICATION_DIAGNOSTICS = false

    /**
     * Enables AI processing timing logs.
     */
    const val ENABLE_AI_TIMING_LOGS = false

    /**
     * Enables queue depth / throughput diagnostics.
     */
    const val ENABLE_QUEUE_DIAGNOSTICS = false

    /**
     * Enables full notification trace logging.
     */
    const val ENABLE_NOTIFICATION_TRACE = false
}
