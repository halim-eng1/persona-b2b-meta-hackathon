package com.example.persona.features.auto_reply.data.providers

import kotlinx.coroutines.flow.Flow

interface LlmProviderClient {
    val providerId: String
    fun generateStream(apiKey: String, request: ProviderRequest): Flow<StreamChunk>
}
