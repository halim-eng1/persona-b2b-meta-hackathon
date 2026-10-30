package com.example.persona.features.chats.domain.usecase

import com.example.persona.core.database.dao.ConversationMetadataDao
import com.example.persona.features.auto_reply.domain.orchestrator.AutoReplyOrchestrator
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DismissHitlAttentionUseCaseTest {

    private val conversationMetadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val autoReplyOrchestrator = mockk<AutoReplyOrchestrator>(relaxed = true)

    @Test
    fun `invoking use case cancels in-flight turn and clears needsHumanAttention in DAO`() = runTest {
        val useCase = DismissHitlAttentionUseCase(
            conversationMetadataDao = conversationMetadataDao,
            autoReplyOrchestrator = autoReplyOrchestrator
        )

        useCase("محمود", "BUSINESS")

        verify(exactly = 1) { autoReplyOrchestrator.cancelInFlightTurn("محمود") }
        coVerify(exactly = 1) { conversationMetadataDao.setNeedsHumanAttention("محمود", "BUSINESS", false) }
    }
}
