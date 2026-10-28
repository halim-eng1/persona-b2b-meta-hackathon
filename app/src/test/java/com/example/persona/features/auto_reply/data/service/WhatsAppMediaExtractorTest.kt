package com.example.persona.features.auto_reply.data.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WhatsAppMediaExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        mockContext = mockk(relaxed = true) {
            every { cacheDir } returns this@WhatsAppMediaExtractorTest.cacheDir
        }
    }

    @Test
    fun `isVoiceMessageIndicator detects voice notes and audio across arabic and english`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        assertTrue(extractor.isVoiceMessageIndicator("🎤 Voice message (0:02)"))
        assertTrue(extractor.isVoiceMessageIndicator("🎤 رسالة صوتية"))
        assertTrue(extractor.isVoiceMessageIndicator("مقطع صوتي"))
        assertTrue(extractor.isVoiceMessageIndicator("Audio file attached"))
        assertTrue(extractor.isVoiceMessageIndicator("🎙️"))

        assertFalse(extractor.isVoiceMessageIndicator("السلام عليكم ورحمة الله"))
        assertFalse(extractor.isVoiceMessageIndicator("بكام التيشيرت ده؟"))
    }

    @Test
    fun `isImageIndicator detects images and photos`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        assertTrue(extractor.isImageIndicator("📷 Sent a photo"))
        assertTrue(extractor.isImageIndicator("📷 بكام ده"))
        assertTrue(extractor.isImageIndicator("📸 صورة المنتج"))
        assertTrue(extractor.isImageIndicator("Please check this photo"))

        assertFalse(extractor.isImageIndicator("عايز اعرف الأسعار"))
    }

    @Test
    fun `extractBitmapFromNotification returns bitmap when EXTRA_PICTURE is present in bundle`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val mockBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 800
            every { height } returns 600
        }

        val extras = mockk<Bundle>(relaxed = true)
        every { extras.get(Notification.EXTRA_PICTURE) } returns (mockBitmap as Any)
        every { extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE) } returns mockBitmap
        val notification = mockk<Notification> {
            this.extras = extras
        }
        val sbn = mockk<StatusBarNotification> {
            every { this@mockk.notification } returns notification
            every { postTime } returns 1000L
        }

        val extracted = extractor.extractBitmapFromNotification(sbn)
        assertNotNull(extracted)
        assertEquals(mockBitmap, extracted)
    }

    @Test
    fun `saveBitmapToCache writes bitmap to jpeg file in cache directory`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val mockBitmap = mockk<Bitmap>(relaxed = true) {
            every { compress(any(), any(), any()) } answers {
                val stream = thirdArg<java.io.OutputStream>()
                stream.write("FAKE_JPEG_BYTES".toByteArray())
                true
            }
        }

        val file = extractor.saveBitmapToCache(mockBitmap)
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertTrue(file.name.startsWith("incoming_media_"))
        assertTrue(file.name.endsWith(".jpg"))
        assertEquals("FAKE_JPEG_BYTES", file.readText())
    }

    @Test
    fun `transcribeVoiceNote calls Groq Whisper Audio API and returns transcribed text`() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", request.url.toString())
            assertTrue(request.headers.contains(HttpHeaders.Authorization, "Bearer test_groq_key"))
            respond(
                content = """{"text": "عايز أعرف سعر التيشيرت الأسود مقاس لارج لو سمحت"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client,
            groqPrimaryApiKey = "test_groq_key",
            groqFallbackApiKey = "test_fallback_key"
        )

        val tempAudio = tempFolder.newFile("sample.opus").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("عايز أعرف سعر التيشيرت الأسود مقاس لارج لو سمحت", result)
    }

    @Test
    fun `transcribeVoiceNote falls back to secondary Groq key on 429 rate limit`() = runBlocking {
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount++
            if (callCount == 1) {
                assertEquals("Bearer primary_key", request.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"error":{"message":"Rate limit reached"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                assertEquals("Bearer secondary_key", request.headers[HttpHeaders.Authorization])
                respond(
                    content = """{"text": "السلام عليكم، هل متاح مقاس ميديوم؟"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client,
            groqPrimaryApiKey = "primary_key",
            groqFallbackApiKey = "secondary_key"
        )

        val tempAudio = tempFolder.newFile("voice.opus").apply {
            writeBytes(byteArrayOf(9, 8, 7))
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("السلام عليكم، هل متاح مقاس ميديوم؟", result)
        assertEquals(2, callCount)
    }
}
