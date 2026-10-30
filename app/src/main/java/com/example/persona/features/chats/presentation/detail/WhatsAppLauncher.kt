package com.example.persona.features.chats.presentation.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppLauncher {

    fun normalizePhoneNumber(phone: String): String {
        val clean = phone.replace(Regex("[^0-9+]"), "").trim()
        return if (clean.startsWith("+")) {
            clean.substring(1)
        } else if (clean.startsWith("01") && clean.length == 11) {
            // Egyptian local mobile numbers (e.g. 01012345678 -> 201012345678)
            "2" + clean
        } else if (clean.startsWith("201") && clean.length == 12) {
            clean
        } else {
            clean
        }
    }

    fun openChat(
        context: Context,
        phoneNumber: String? = null,
        contactName: String? = null,
        preferBusiness: Boolean = false
    ): Boolean {
        val phoneCandidate = phoneNumber?.takeIf { it.isNotBlank() }
            ?: contactName?.takeIf { 
                it.startsWith("+") || it.startsWith("01") || 
                (it.all { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' } && it.length >= 8) 
            }

        if (!phoneCandidate.isNullOrBlank()) {
            val normalized = normalizePhoneNumber(phoneCandidate)
            if (normalized.length >= 8) {
                val directUri = Uri.parse("whatsapp://send?phone=$normalized")
                val pkg = if (preferBusiness) "com.whatsapp.w4b" else "com.whatsapp"

                // 1. Direct native whatsapp:// deep link with preferred package
                try {
                    val directIntent = Intent(Intent.ACTION_VIEW, directUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (preferBusiness) setPackage(pkg)
                    }
                    context.startActivity(directIntent)
                    return true
                } catch (_: Exception) {}

                // 2. Direct native whatsapp:// deep link without explicit package constraint
                try {
                    val anyDirectIntent = Intent(Intent.ACTION_VIEW, directUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(anyDirectIntent)
                    return true
                } catch (_: Exception) {}

                // 3. Web https://wa.me/ deep link
                try {
                    val webUri = Uri.parse("https://wa.me/$normalized")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (preferBusiness) setPackage(pkg)
                    }
                    context.startActivity(webIntent)
                    return true
                } catch (_: Exception) {}
            }
        }

        // Fallback: Launch WhatsApp or WhatsApp Business application directly
        try {
            val pkg = if (preferBusiness) "com.whatsapp.w4b" else "com.whatsapp"
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        } catch (_: Exception) {}

        // Fallback: Play Store or Toast if WhatsApp is not installed
        try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
            return true
        } catch (_: Exception) {
            try {
                val webPlayStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webPlayStoreIntent)
                return true
            } catch (_: Exception) {
                try {
                    Toast.makeText(context, "تطبيق واتساب غير مثبت على هذا الجهاز", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
        }

        return false
    }
}
