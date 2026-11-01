package com.example.persona.features.onboarding.data.model_download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.persona.features.onboarding.domain.model_download.DownloadProgress
import com.example.persona.features.onboarding.domain.model_download.ModelDownloadState
import com.example.persona.features.onboarding.domain.model_download.ModelValidator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadManagerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val modelValidator: ModelValidator = mockk(relaxed = true)
    private val connectivityManager: ConnectivityManager = mockk(relaxed = true)
    private val networkCapabilities: NetworkCapabilities = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var downloadManager: ModelDownloadManager

    @Before
    fun setUp() {
        val filesDir = tmpFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns mockk()
        every { connectivityManager.getNetworkCapabilities(any()) } returns networkCapabilities

        downloadManager = ModelDownloadManager(
            context = context,
            modelValidator = modelValidator,
            ioDispatcher = testDispatcher
        )
        // Default to sufficient storage (10GB) in unit test environment
        downloadManager.storageBytesProvider = { 10L * 1024L * 1024L * 1024L }
    }

    @Test
    fun `storage check transitions to InsufficientStorage when free space is under 300MB`() = runTest(testDispatcher) {
        downloadManager.storageBytesProvider = { 150L * 1024L * 1024L } // 150MB

        downloadManager.startDownload()

        val state = downloadManager.state.value
        assertTrue("Expected InsufficientStorage but was $state", state is ModelDownloadState.InsufficientStorage)
        val storageState = state as ModelDownloadState.InsufficientStorage
        assertEquals(300L * 1024L * 1024L, storageState.requiredBytes)
        assertEquals(150L * 1024L * 1024L, storageState.availableBytes)
    }

    @Test
    fun `download progress fraction calculates accurately`() {
        val emptyProgress = DownloadProgress(0L, 0L, 0L, 0L, "test")
        assertEquals(0f, emptyProgress.progressFraction, 0.001f)

        val halfProgress = DownloadProgress(50L, 100L, 10L, 5L, "test")
        assertEquals(0.5f, halfProgress.progressFraction, 0.001f)

        val completedProgress = DownloadProgress(100L, 100L, 10L, 0L, "test")
        assertEquals(1f, completedProgress.progressFraction, 0.001f)

        val overflowProgress = DownloadProgress(150L, 100L, 10L, 0L, "test")
        assertEquals(1f, overflowProgress.progressFraction, 0.001f)
    }

    @Test
    fun `total expected bytes equals model plus tokenizer spec sizes`() {
        val expectedSum = ModelDownloadManager.MODEL_SPEC.expectedBytes + ModelDownloadManager.TOKENIZER_SPEC.expectedBytes
        assertEquals(expectedSum, ModelDownloadManager.TOTAL_EXPECTED_BYTES)
        assertEquals(127_493_916L, ModelDownloadManager.TOTAL_EXPECTED_BYTES)
        assertEquals(300L * 1024L * 1024L, ModelDownloadManager.REQUIRED_STORAGE_BYTES)
    }

    @Test
    fun `cellular pre-flight transitions to CellularConfirmationRequired if not pre-approved`() = runTest(testDispatcher) {
        // Mock cellular transport active, Wi-Fi not active
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        downloadManager.startDownload()

        val state = downloadManager.state.value
        assertTrue(
            "Expected CellularConfirmationRequired but was $state",
            state is ModelDownloadState.CellularConfirmationRequired
        )
        val cellState = state as ModelDownloadState.CellularConfirmationRequired
        assertEquals(ModelDownloadManager.TOTAL_EXPECTED_BYTES, cellState.totalBytes)
    }
}
