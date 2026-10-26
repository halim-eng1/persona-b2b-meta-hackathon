package com.example.persona.core.common

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

object GlobalExceptionHandler {
    fun create(tag: String = "PersonaChat"): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(tag, "Uncaught coroutine exception", throwable)
        }
}
