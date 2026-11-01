package com.example.persona.features.onboarding.domain.model_download

import android.content.Context
import com.example.persona.core.data.repository.UserPreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ModelValidatorTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var validator: ModelValidator

    @Before
    fun setUp() {
        every { context.applicationContext.classLoader } returns javaClass.classLoader
        validator = ModelValidator(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `validate returns Failed when model file is missing`() = runTest(testDispatcher) {
        val modelsDir = tmpFolder.newFolder("models")
        // Tokenizer exists, but ONNX model is missing
        File(modelsDir, ModelValidator.TOKENIZER_FILE_NAME).writeText("{}")

        val result = validator.validate(modelsDir)

        assertTrue(result is ValidationResult.Failed)
        val failed = result as ValidationResult.Failed
        assertTrue(failed.reason.contains("Model file does not exist"))
        coVerify(exactly = 0) { userPreferencesRepository.setModelPrepared(true) }
    }

    @Test
    fun `validate returns Failed when tokenizer file is missing`() = runTest(testDispatcher) {
        val modelsDir = tmpFolder.newFolder("models")
        // ONNX exists, but tokenizer is missing
        File(modelsDir, ModelValidator.MODEL_FILE_NAME).writeBytes(byteArrayOf(1, 2, 3))

        val result = validator.validate(modelsDir)

        assertTrue(result is ValidationResult.Failed)
        val failed = result as ValidationResult.Failed
        assertTrue(failed.reason.contains("Tokenizer file does not exist"))
        coVerify(exactly = 0) { userPreferencesRepository.setModelPrepared(true) }
    }

    @Test
    fun `validate purges corrupted files on exception`() = runTest(testDispatcher) {
        val modelsDir = tmpFolder.newFolder("models")
        val modelFile = File(modelsDir, ModelValidator.MODEL_FILE_NAME).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val tokenizerFile = File(modelsDir, ModelValidator.TOKENIZER_FILE_NAME).apply { writeText("corrupted json") }

        val result = validator.validate(modelsDir)

        assertTrue(result is ValidationResult.Failed)
        // Ensure invalid/corrupted files are purged
        assertTrue(!modelFile.exists() || !tokenizerFile.exists())
    }
}

