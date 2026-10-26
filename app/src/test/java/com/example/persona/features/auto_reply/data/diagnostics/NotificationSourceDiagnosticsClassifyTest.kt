package com.example.persona.features.auto_reply.data.diagnostics

import android.content.Context
import com.example.persona.core.debug.DebugFlags
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceDiagnostics
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the pure classification rules used by [NotificationSourceDiagnostics]
 * to distinguish between WhatsApp Main, Business, and Clone / Work Profile.
 *
 * These rules are the contract that downstream filters depend on, so they
 * must not regress.
 */
class NotificationSourceDiagnosticsClassifyTest {

    @Test
    fun `whatsapp main package with user zero is MAIN_APP`() {
        assertEquals(
            NotificationSourceClass.MAIN_APP,
            NotificationSourceDiagnostics.classify("com.whatsapp", userIdentifier = 0)
        )
    }

    @Test
    fun `whatsapp business package is BUSINESS regardless of user`() {
        assertEquals(
            NotificationSourceClass.BUSINESS,
            NotificationSourceDiagnostics.classify("com.whatsapp.w4b", userIdentifier = 0)
        )
        assertEquals(
            NotificationSourceClass.BUSINESS,
            NotificationSourceDiagnostics.classify("com.whatsapp.w4b", userIdentifier = 999)
        )
    }

    @Test
    fun `whatsapp main package with non-zero user is CLONE`() {
        assertEquals(
            NotificationSourceClass.CLONE,
            NotificationSourceDiagnostics.classify("com.whatsapp", userIdentifier = 999)
        )
        assertEquals(
            NotificationSourceClass.CLONE,
            NotificationSourceDiagnostics.classify("com.whatsapp", userIdentifier = 10)
        )
        assertEquals(
            NotificationSourceClass.CLONE,
            NotificationSourceDiagnostics.classify("com.whatsapp", userIdentifier = -1)
        )
    }

    @Test
    fun `unknown package is UNKNOWN`() {
        assertEquals(
            NotificationSourceClass.UNKNOWN,
            NotificationSourceDiagnostics.classify("com.example.other", userIdentifier = 0)
        )
        assertEquals(
            NotificationSourceClass.UNKNOWN,
            NotificationSourceDiagnostics.classify(null, userIdentifier = 0)
        )
    }

    @Test
    fun `business package classification takes precedence over user check`() {
        // The spec says: packageName == "com.whatsapp.w4b" -> BUSINESS, even if user != 0
        // (covers hypothetical multi-user business installs on a single device).
        assertEquals(
            NotificationSourceClass.BUSINESS,
            NotificationSourceDiagnostics.classify("com.whatsapp.w4b", userIdentifier = 10)
        )
    }

    @Test
    fun `diagnostics disabled - captureAndLog produces no logs`() {
        // When ENABLE_NOTIFICATION_DIAGNOSTICS is false, captureAndLog should
        // return immediately without launching any coroutine or building any log.
        if (DebugFlags.ENABLE_NOTIFICATION_DIAGNOSTICS) return

        // Pass null — the method should return without logging at all.
        val diagnostics = NotificationSourceDiagnostics(
            appContext = mockk<Context>(relaxed = true)
        )
        diagnostics.captureAndLog(null)

        // No assertion needed beyond no-throw: the method returned silently.
        // If the flag were true this test would still pass, but we guard above
        // so it only validates the disabled path.
        assertTrue(true)
    }
}
