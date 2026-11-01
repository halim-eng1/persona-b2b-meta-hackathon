package com.example.persona.features.auto_reply.data.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.example.persona.features.auto_reply.data.diagnostics.NotificationSourceClass
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun `hasNotificationImage returns false and hasAudioIndicator returns true for voice notifications`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TEXT, "🎤 Voice message (0:04)")
        }
        val notification = mockk<Notification> {
            this.extras = extras
        }
        val sbn = mockk<StatusBarNotification> {
            every { this@mockk.notification } returns notification
        }

        assertTrue(extractor.hasAudioIndicator(sbn, "🎤 Voice message (0:04)"))
        assertFalse(extractor.hasNotificationImage(sbn, "🎤 Voice message (0:04)"))
    }

    @Test
    fun `isImageIndicator detects images and photos`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        assertTrue(extractor.isImageIndicator("📷 Sent a photo"))
        assertTrue(extractor.isImageIndicator("📷 بكام ده"))
        assertTrue(extractor.isImageIndicator("📸 صورة المنتج"))
        assertTrue(extractor.isImageIndicator("Photo"))
        assertTrue(extractor.isImageIndicator("صورة"))
        assertTrue(extractor.isImageIndicator("Sent a photo"))

        assertFalse(extractor.isImageIndicator("Please check this photo"))
        assertFalse(extractor.isImageIndicator("بس انا كنت قصدي ب الصورة اني بحبك انت 😂🫣🤩"))
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
            writeBytes(ByteArray(1024) { 1 })
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
            writeBytes(ByteArray(1024) { 2 })
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("السلام عليكم، هل متاح مقاس ميديوم؟", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `extractBitmapFromNotification extracts bitmap from MessagingStyle messages with uri and type`() {
        mockkStatic(BitmapFactory::class)
        try {
            val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
            val mockBitmap = mockk<Bitmap>(relaxed = true)
            val mockUri = mockk<Uri>()
            val fakeStream = java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3))

            every { mockContext.contentResolver.openInputStream(mockUri) } returns fakeStream
            every { BitmapFactory.decodeStream(any()) } returns mockBitmap

            val messageBundle = mockk<Bundle>(relaxed = true) {
                every { getString("type") } returns "image/jpeg"
                every { getParcelable<Uri>("uri") } returns mockUri
                every { getParcelable("uri", Uri::class.java) } returns mockUri
                every { this@mockk.get("uri") } returns (mockUri as Any)
            }

            val extras = mockk<Bundle>(relaxed = true)
            every { extras.keySet() } returns setOf(Notification.EXTRA_MESSAGES)
            every { extras.getParcelableArray(Notification.EXTRA_MESSAGES) } returns arrayOf(messageBundle)
            every { extras.get(Notification.EXTRA_MESSAGES) } returns arrayOf(messageBundle)

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
        } finally {
            unmockkStatic(BitmapFactory::class)
        }
    }

    @Test
    fun `extractBitmapFromNotification returns bitmap from EXTRA_LARGE_ICON_BIG`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val mockBitmap = mockk<Bitmap>(relaxed = true)

        val extras = mockk<Bundle>(relaxed = true)
        every { extras.keySet() } returns setOf("android.largeIcon.big")
        every { extras.get(Notification.EXTRA_MESSAGES) } returns null
        every { extras.getParcelableArray(Notification.EXTRA_MESSAGES) } returns null
        every { extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES) } returns null
        every { extras.get(Notification.EXTRA_PICTURE) } returns null
        every { extras.get("android.pictureIcon") } returns null
        every { extras.get(Notification.EXTRA_LARGE_ICON_BIG) } returns mockBitmap

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
    fun `logNotificationExtrasStructure logs key and runtime types without exception`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val extras = mockk<Bundle>(relaxed = true)
        every { extras.keySet() } returns setOf("key1", "key2")
        every { extras.get("key1") } returns "text value"
        every { extras.get("key2") } returns 12345L

        extractor.logNotificationExtrasStructure(extras)
    }

    @Test
    fun `findLatestImageOnDisk anti-collision ensures claimed file is not re-claimed by concurrent turn`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val imagesDir = tempFolder.newFolder("whatsapp_media")
        extractor.imageDirectoriesOverride = listOf(imagesDir)

        val image1 = File(imagesDir, "IMG_20261028_WA0001.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(1_000_000L)
        }

        // First notification turn: claims image1
        val firstClaim = extractor.findLatestImageOnDisk(NotificationSourceClass.BUSINESS, postTime = 1_000_000L)
        assertNotNull(firstClaim)
        assertEquals(image1.absolutePath, firstClaim!!.absolutePath)
        assertTrue(extractor.isDiskFileClaimed(image1.absolutePath))

        // Concurrent notification turn with same postTime: cannot claim image1 again!
        val secondClaim = extractor.findLatestImageOnDisk(NotificationSourceClass.BUSINESS, postTime = 1_000_000L)
        assertNull(secondClaim)

        // When a new file arrives for the second turn:
        val image2 = File(imagesDir, "IMG_20261028_WA0002.jpg").apply {
            writeBytes(byteArrayOf(4, 5, 6))
            setLastModified(1_001_000L)
        }
        val thirdClaim = extractor.findLatestImageOnDisk(NotificationSourceClass.BUSINESS, postTime = 1_001_000L)
        assertNotNull(thirdClaim)
        assertEquals(image2.absolutePath, thirdClaim!!.absolutePath)
        assertTrue(extractor.isDiskFileClaimed(image2.absolutePath))
    }

    @Test
    fun `extractImage recovers bitmap immediately on Attempt 0 without polling delays`() = runTest {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        extractor.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined

        val mockBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1080
            every { compress(any(), any(), any()) } answers {
                val outStream = thirdArg<java.io.OutputStream>()
                outStream.write(byteArrayOf(1, 2, 3))
                true
            }
        }

        val extras = mockk<Bundle>(relaxed = true)
        every { extras.keySet() } returns setOf(Notification.EXTRA_PICTURE)
        every { extras.containsKey(Notification.EXTRA_PICTURE) } returns true
        every { extras.get(Notification.EXTRA_PICTURE) } returns (mockBitmap as Any)
        every { extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE) } returns mockBitmap

        val notification = mockk<Notification> {
            this.extras = extras
        }
        val sbn = mockk<StatusBarNotification> {
            every { key } returns "0|com.whatsapp.w4b|100|null|10822"
            every { this@mockk.notification } returns notification
            every { postTime } returns 1_000_000L
        }

        var providerCalls = 0
        val file = extractor.extractImage(
            sbn = sbn,
            sourceClass = NotificationSourceClass.BUSINESS,
            activeNotificationProvider = {
                providerCalls++
                null
            }
        )

        assertNotNull(file)
        assertTrue(file!!.exists())
        assertEquals(0, providerCalls)
        assertFalse(extractor.isDiskFileClaimed(file.absolutePath))
    }

    @Test
    fun `extractImage succeeds on staged micro-polling retry attempt when URI arrives asynchronously`() = runTest {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        extractor.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined

        val emptyExtras = mockk<Bundle>(relaxed = true)
        every { emptyExtras.keySet() } returns emptySet()
        every { emptyExtras.getCharSequence(Notification.EXTRA_TEXT) } returns "📷"
        every { emptyExtras.get(any()) } returns null
        every { emptyExtras.getParcelableArray(any()) } returns null

        val initialNotification = mockk<Notification> {
            this.extras = emptyExtras
        }
        val initialSbn = mockk<StatusBarNotification> {
            every { key } returns "0|com.whatsapp.w4b|200|null|10822"
            every { notification } returns initialNotification
            every { postTime } returns 1_000_000L
        }

        val mockBitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1080
            every { compress(any(), any(), any()) } answers {
                val outStream = thirdArg<java.io.OutputStream>()
                outStream.write(byteArrayOf(4, 5, 6))
                true
            }
        }
        val populatedExtras = mockk<Bundle>(relaxed = true)
        every { populatedExtras.keySet() } returns setOf(Notification.EXTRA_PICTURE)
        every { populatedExtras.containsKey(Notification.EXTRA_PICTURE) } returns true
        every { populatedExtras.get(Notification.EXTRA_PICTURE) } returns (mockBitmap as Any)
        every { populatedExtras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE) } returns mockBitmap

        val populatedNotification = mockk<Notification> {
            this.extras = populatedExtras
        }
        val populatedSbn = mockk<StatusBarNotification> {
            every { key } returns "0|com.whatsapp.w4b|200|null|10822"
            every { notification } returns populatedNotification
            every { postTime } returns 1_000_000L
        }

        var pollAttempt = 0
        val file = extractor.extractImage(
            sbn = initialSbn,
            sourceClass = NotificationSourceClass.BUSINESS,
            activeNotificationProvider = { _ ->
                pollAttempt++
                if (pollAttempt >= 2) {
                    populatedSbn
                } else {
                    initialSbn
                }
            },
            currentText = "📷"
        )

        assertNotNull(file)
        assertTrue(file!!.exists())
        assertEquals(2, pollAttempt)
    }

    @Test
    fun `extractImage escalates to disk fallback when micro-polling times out`() = runTest {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        extractor.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined

        val imagesDir = tempFolder.newFolder("whatsapp_timeout_media")
        extractor.imageDirectoriesOverride = listOf(imagesDir)

        val diskImage = File(imagesDir, "IMG_20261028_WA9999.jpg").apply {
            writeBytes(byteArrayOf(7, 8, 9))
            setLastModified(1_000_000L)
        }

        val emptyExtras = mockk<Bundle>(relaxed = true)
        every { emptyExtras.keySet() } returns emptySet()
        every { emptyExtras.getCharSequence(Notification.EXTRA_TEXT) } returns "📷"
        every { emptyExtras.get(any()) } returns null
        every { emptyExtras.getParcelableArray(any()) } returns null

        val sbn = mockk<StatusBarNotification> {
            every { key } returns "0|com.whatsapp.w4b|300|null|10822"
            every { notification } returns mockk { this.extras = emptyExtras }
            every { postTime } returns 1_000_000L
        }

        var pollCalls = 0
        val file = extractor.extractImage(
            sbn = sbn,
            sourceClass = NotificationSourceClass.BUSINESS,
            activeNotificationProvider = {
                pollCalls++
                null
            },
            currentText = "📷"
        )

        assertNotNull(file)
        assertEquals(diskImage.absolutePath, file!!.absolutePath)
        assertEquals(3, pollCalls)
        assertTrue(extractor.isDiskFileClaimed(diskImage.absolutePath))
    }

    @Test
    fun `pure text notification does not trigger micro-polling or disk fallback even if stale extras or disk images exist`() = runTest {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        extractor.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined

        val imagesDir = tempFolder.newFolder("whatsapp_stale_media")
        extractor.imageDirectoriesOverride = listOf(imagesDir)

        val diskImage = File(imagesDir, "IMG_20261028_WA0003.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(1_000_000L)
        }

        // WhatsApp notification where user sends a text following a photo
        val textBundle = mockk<Bundle>(relaxed = true) {
            every { getCharSequence("text") } returns "يعني مش متاح ماج خالص؟"
            every { getString("type") } returns null
            every { getString("data_mime_type") } returns null
        }

        val extras = mockk<Bundle>(relaxed = true)
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns "يعني مش متاح ماج خالص؟"
        every { extras.getParcelableArray(Notification.EXTRA_MESSAGES) } returns arrayOf(textBundle)
        // Stale picture slots left over from a previous turn
        every { extras.containsKey(Notification.EXTRA_PICTURE) } returns true

        val sbn = mockk<StatusBarNotification> {
            every { key } returns "0|com.whatsapp.w4b|400|null|10822"
            every { notification } returns mockk { this.extras = extras }
            every { postTime } returns 1_000_000L
        }

        var pollCalls = 0
        val file = extractor.extractImage(
            sbn = sbn,
            sourceClass = NotificationSourceClass.BUSINESS,
            activeNotificationProvider = {
                pollCalls++
                null
            },
            currentText = "يعني مش متاح ماج خالص؟"
        )

        // Must return null immediately without polling or disk fallback!
        assertNull(file)
        assertEquals(0, pollCalls)
        assertFalse(extractor.isDiskFileClaimed(diskImage.absolutePath))
    }

    @Test
    fun `extractDurationSeconds normalizes Eastern Arabic numerals and parses duration`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        assertEquals(4, extractor.extractDurationSeconds("🎤 Voice message (0:04)"))
        assertEquals(4, extractor.extractDurationSeconds("🎤 رسالة صوتية (٠:٠٤)"))
        assertEquals(72, extractor.extractDurationSeconds("رسالة صوتية (١:١٢)"))
        assertEquals(75, extractor.extractDurationSeconds("Voice message (1:15)"))
        assertEquals(9, extractor.extractDurationSeconds("مقطع صوتي (00:09)"))
        assertEquals(150, extractor.extractDurationSeconds("audio (2:30)"))
        assertNull(extractor.extractDurationSeconds("رسالة صوتية بدون مدة"))
        assertNull(extractor.extractDurationSeconds("السلام عليكم"))
    }

    @Test
    fun `extractAudioFromNotification extracts ogg file directly from MessagingStyle uri and bypasses disk scan`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val mockUri = mockk<Uri>()
        val dummyAudioBytes = ByteArray(1024) { 42 }
        val fakeStream = java.io.ByteArrayInputStream(dummyAudioBytes)

        every { mockContext.contentResolver.openInputStream(mockUri) } returns fakeStream

        val messageBundle = mockk<Bundle>(relaxed = true) {
            every { getString("type") } returns "audio/ogg; codecs=opus"
            every { getParcelable<Uri>("uri") } returns mockUri
            every { getParcelable("uri", Uri::class.java) } returns mockUri
            every { this@mockk.get("uri") } returns (mockUri as Any)
        }

        val extras = mockk<Bundle>(relaxed = true)
        every { extras.getParcelableArray(Notification.EXTRA_MESSAGES) } returns arrayOf(messageBundle)

        val sbn = mockk<StatusBarNotification> {
            every { notification } returns mockk { this@mockk.extras = extras }
        }

        val audioFile = extractor.extractAudioFromNotification(sbn)
        assertNotNull(audioFile)
        assertTrue(audioFile!!.exists())
        assertTrue(audioFile.name.endsWith(".ogg"))
        assertEquals(1024L, audioFile.length())
        assertEquals(42.toByte(), audioFile.readBytes()[0])
    }

    @Test
    fun `captureLatestVoiceNote matches duration within 1000ms and breaks ties by proximity to postTime`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val voiceDir = tempFolder.newFolder("whatsapp_voice_notes")
        extractor.voiceNoteDirectoriesOverride = listOf(voiceDir)

        val now = 1_000_000L

        // File 1: 4 seconds (4000ms), modified 5000ms ago
        val file4s = File(voiceDir, "PTT-001.opus").apply {
            writeBytes(ByteArray(1024))
            setLastModified(now - 5000L)
        }

        // File 2: 12 seconds (12000ms), modified 2000ms ago
        val file12s = File(voiceDir, "PTT-002.opus").apply {
            writeBytes(ByteArray(1024))
            setLastModified(now - 2000L)
        }

        // File 3: 4 seconds (4000ms), modified 1000ms ago (closer to postTime)
        val file4sCloser = File(voiceDir, "PTT-003.opus").apply {
            writeBytes(ByteArray(1024))
            setLastModified(now - 1000L)
        }

        extractor.audioDurationProvider = { file ->
            when (file.name) {
                "PTT-001.opus" -> 4050L // ~4s
                "PTT-002.opus" -> 12100L // ~12s
                "PTT-003.opus" -> 3980L // ~4s
                else -> null
            }
        }

        // First customer: 4s voice note. Candidates matching: PTT-001 (5s ago) and PTT-003 (1s ago).
        // Tie-breaker should pick PTT-003 (closest to now).
        val capturedCustomer1 = extractor.captureLatestVoiceNote(
            sourceClass = NotificationSourceClass.BUSINESS,
            postTime = now,
            expectedDurationSeconds = 4
        )
        assertNotNull(capturedCustomer1)
        assertEquals(file4sCloser.absolutePath, capturedCustomer1!!.absolutePath)
        assertTrue(extractor.isVoiceFileClaimed(file4sCloser.absolutePath))

        // Second customer: 12s voice note. Should pick PTT-002.
        val capturedCustomer2 = extractor.captureLatestVoiceNote(
            sourceClass = NotificationSourceClass.BUSINESS,
            postTime = now,
            expectedDurationSeconds = 12
        )
        assertNotNull(capturedCustomer2)
        assertEquals(file12s.absolutePath, capturedCustomer2!!.absolutePath)
        assertTrue(extractor.isVoiceFileClaimed(file12s.absolutePath))

        // Third customer: 4s voice note. PTT-003 is already claimed!
        // Should pick the remaining matching file: PTT-001.
        val capturedCustomer3 = extractor.captureLatestVoiceNote(
            sourceClass = NotificationSourceClass.BUSINESS,
            postTime = now,
            expectedDurationSeconds = 4
        )
        assertNotNull(capturedCustomer3)
        assertEquals(file4s.absolutePath, capturedCustomer3!!.absolutePath)
        assertTrue(extractor.isVoiceFileClaimed(file4s.absolutePath))

        // Fourth customer: No unclaimed 4s files remain!
        val capturedCustomer4 = extractor.captureLatestVoiceNote(
            sourceClass = NotificationSourceClass.BUSINESS,
            postTime = now,
            expectedDurationSeconds = 4
        )
        assertNull(capturedCustomer4)
    }

    @Test
    fun `captureLatestVoiceNote evicts claimed files older than 10 minutes`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val voiceDir = tempFolder.newFolder("whatsapp_voice_evict")
        extractor.voiceNoteDirectoriesOverride = listOf(voiceDir)

        val file = File(voiceDir, "PTT-OLD.opus").apply {
            writeBytes(ByteArray(1024))
            setLastModified(1_000_000L)
        }

        // Claim file at timestamp 1_000_000L
        extractor.captureLatestVoiceNote(NotificationSourceClass.BUSINESS, postTime = 1_000_000L)
        assertTrue(extractor.isVoiceFileClaimed(file.absolutePath))

        // 11 minutes later (1_000_000 + 660_000):
        val later = 1_000_000L + 11 * 60 * 1000L
        file.setLastModified(later)
        val reClaimed = extractor.captureLatestVoiceNote(NotificationSourceClass.BUSINESS, postTime = later)
        assertNotNull(reClaimed)
        assertEquals(file.absolutePath, reClaimed!!.absolutePath)
    }

    @Test
    fun `transcribeVoiceNote sends ogg filename extension and valid multipart payload without duplicate form-data headers`() = runBlocking {
        var wireBodyText = ""
        val mockEngine = MockEngine { request ->
            assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", request.url.toString())
            assertEquals("Bearer test_groq_key", request.headers[HttpHeaders.Authorization])

            val body = request.body
            assertTrue(body is MultiPartFormDataContent)
            val channel = io.ktor.utils.io.ByteChannel(autoFlush = true)
            runBlocking {
                (body as io.ktor.http.content.OutgoingContent.WriteChannelContent).writeTo(channel)
                channel.close(null)
                val readBytes = java.io.ByteArrayOutputStream()
                val buffer = java.nio.ByteBuffer.allocate(1024)
                while (!channel.isClosedForRead) {
                    buffer.clear()
                    val count = channel.readAvailable(buffer)
                    if (count > 0) {
                        readBytes.write(buffer.array(), 0, count)
                    } else if (count == -1) {
                        break
                    }
                }
                wireBodyText = readBytes.toString("UTF-8")
            }

            respond(
                content = """{"text": "عايز اعرف اسعار الهوديز"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine)

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client,
            groqPrimaryApiKey = "test_groq_key",
            groqFallbackApiKey = "fallback_key"
        )

        // WhatsApp file named with .opus extension
        val tempAudio = tempFolder.newFile("PTT-20261028-WA0009.opus").apply {
            writeBytes(ByteArray(1024) { 7 })
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("عايز اعرف اسعار الهوديز", result)

        // Verify wire format invariants:
        assertTrue(wireBodyText.contains("filename=\"PTT-20261028-WA0009.ogg\""))
        assertFalse(wireBodyText.contains("PTT-20261028-WA0009.opus"))
        assertTrue(wireBodyText.contains("Content-Type: audio/ogg"))
        assertTrue(wireBodyText.contains("name=model") || wireBodyText.contains("name=\"model\""))
        assertTrue(wireBodyText.contains("whisper-large-v3-turbo"))
        assertTrue(wireBodyText.contains("name=response_format") || wireBodyText.contains("name=\"response_format\""))
        assertTrue(wireBodyText.contains("json"))
        assertTrue(wireBodyText.contains("name=language") || wireBodyText.contains("name=\"language\""))
        assertTrue(wireBodyText.contains("ar"))
        assertFalse(wireBodyText.contains("form-data; name=\"file\"; form-data; name=\"file\""))
        assertFalse(wireBodyText.contains("form-data; name=file; form-data; name=file"))
    }

    @Test
    fun `transcribeVoiceNote delays 150ms and checks readiness when audio file is initially small`() = runTest {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        extractor.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined

        val emptyAudio = tempFolder.newFile("empty.opus").apply {
            writeBytes(ByteArray(100)) // <= 500 bytes
        }

        val result = extractor.transcribeVoiceNote(emptyAudio)
        assertNull(result)
    }

    @Test
    fun `isVoiceMessageIndicator detects forwarded voice notes and audio`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        assertTrue(extractor.isVoiceMessageIndicator("محولة: رسالة صوتية (0:15)"))
        assertTrue(extractor.isVoiceMessageIndicator("محولة: مقطع صوتي"))
        assertTrue(extractor.isVoiceMessageIndicator("Forwarded: Voice message (0:30)"))
        assertTrue(extractor.isVoiceMessageIndicator("Forwarded: Audio"))
    }

    @Test
    fun `extractDurationSeconds matches unparenthesized durations`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        assertEquals(15, extractor.extractDurationSeconds("Voice message 0:15"))
        assertEquals(90, extractor.extractDurationSeconds("رسالة صوتية 1:30"))
        assertEquals(4, extractor.extractDurationSeconds("مقطع صوتي 0:04"))
    }

    @Test
    fun `purgeStaleMediaCache removes stale voice and image files older than max age`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        val oldVoiceFile = File(cacheDir, "incoming_voice_old.ogg").apply {
            writeText("old_voice")
            setLastModified(System.currentTimeMillis() - 7200_000L) // 2 hours old
        }
        val freshVoiceFile = File(cacheDir, "incoming_voice_fresh.ogg").apply {
            writeText("fresh_voice")
            setLastModified(System.currentTimeMillis() - 100_000L) // 100s old
        }
        val oldMediaFile = File(cacheDir, "incoming_media_old.jpg").apply {
            writeText("old_media")
            setLastModified(System.currentTimeMillis() - 7200_000L) // 2 hours old
        }

        extractor.purgeStaleMediaCache(maxAgeMs = 3600_000L) // 1 hour TTL

        assertFalse(oldVoiceFile.exists())
        assertFalse(oldMediaFile.exists())
        assertTrue(freshVoiceFile.exists())
    }

    @Test
    fun `evictStaleClaimedDiskFiles evicts claims older than 10 minutes`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val diskImage = tempFolder.newFile("test_disk_img.jpg")
        val now = 1_000_000L

        extractor.imageDirectoriesOverride = listOf(tempFolder.root)
        diskImage.setLastModified(now)

        val recovered = extractor.findLatestImageOnDisk(NotificationSourceClass.BUSINESS, postTime = now)
        assertNotNull(recovered)
        assertTrue(extractor.isDiskFileClaimed(diskImage.absolutePath))

        // 11 minutes later:
        val later = now + 11 * 60 * 1000L
        extractor.evictStaleClaimedDiskFiles(now = later)
        assertFalse(extractor.isDiskFileClaimed(diskImage.absolutePath))
    }

    @Test
    fun `transcribeVoiceNote deletes temporary incoming_voice ogg cache file in finally`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"text": "مرحباً"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine)
        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client,
            groqPrimaryApiKey = "test_groq_key",
            groqFallbackApiKey = "fallback_key"
        )

        val tempVoiceCacheFile = File(cacheDir, "incoming_voice_12345.ogg").apply {
            writeBytes(ByteArray(1024) { 9 })
        }
        assertTrue(tempVoiceCacheFile.exists())

        val result = extractor.transcribeVoiceNote(tempVoiceCacheFile)
        assertEquals("مرحباً", result)
        assertFalse("Temporary incoming_voice_*.ogg cache file should be deleted in finally block", tempVoiceCacheFile.exists())
    }

    @Test
    fun `extractAudioFromNotification only inspects last message bundle and ignores historical turns`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val oldUri = mockk<Uri>()
        val latestUri = mockk<Uri>()
        val latestAudioBytes = ByteArray(1024) { 99 }

        every { mockContext.contentResolver.openInputStream(latestUri) } returns java.io.ByteArrayInputStream(latestAudioBytes)

        val oldBundle = mockk<Bundle>(relaxed = true) {
            every { getString("type") } returns "audio/ogg"
            every { getParcelable<Uri>("uri") } returns oldUri
            every { getParcelable("uri", Uri::class.java) } returns oldUri
            every { this@mockk.get("uri") } returns (oldUri as Any)
        }

        val latestBundle = mockk<Bundle>(relaxed = true) {
            every { getString("type") } returns "audio/ogg"
            every { getParcelable<Uri>("uri") } returns latestUri
            every { getParcelable("uri", Uri::class.java) } returns latestUri
            every { this@mockk.get("uri") } returns (latestUri as Any)
        }

        val extras = mockk<Bundle>(relaxed = true)
        every { extras.getParcelableArray(Notification.EXTRA_MESSAGES) } returns arrayOf(oldBundle, latestBundle)

        val sbn = mockk<StatusBarNotification> {
            every { notification } returns mockk { this@mockk.extras = extras }
        }

        val audioFile = extractor.extractAudioFromNotification(sbn)
        assertNotNull(audioFile)
        assertEquals(99.toByte(), audioFile!!.readBytes()[0])
    }

    @Test
    fun `parseQwenAsrResponse correctly parses SSE stream frames from Qwen FunASR`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val sseStream = """
            id:1
            event:result
            data:{"sentence":{"sentence_id":1,"begin_time":280,"end_time":3393,"text":"هو المفروض في حد موجود هنا دلوقتي ولا إيه؟","sentence_end":true},"text":"هو المفروض في حد موجود هنا دلوقتي ولا إيه؟"}
        """.trimIndent()

        val parsed = extractor.parseQwenAsrResponse(sseStream)
        assertEquals("هو المفروض في حد موجود هنا دلوقتي ولا إيه؟", parsed)
    }

    @Test
    fun `parseQwenAsrResponse handles empty or malformed SSE stream gracefully`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))
        val emptySse = ""
        val malformedSse = """
            id:1
            event:result
            data:not-valid-json
        """.trimIndent()

        assertNull(extractor.parseQwenAsrResponse(emptySse))
        assertNull(extractor.parseQwenAsrResponse(malformedSse))
    }

    @Test
    fun `callGeminiAudioTranscription sends valid inlineData payload and extracts transcribed text`() = runBlocking {
        var interceptedUrl = ""
        var interceptedContentType = ""
        var interceptedBody = ""

        val mockEngine = MockEngine { request ->
            interceptedUrl = request.url.toString()
            interceptedContentType = request.body.contentType?.toString()
                ?: request.headers[HttpHeaders.ContentType]
                ?: ""
            interceptedBody = (request.body as? io.ktor.http.content.TextContent)?.text
                ?: (request.body as? io.ktor.http.content.ByteArrayContent)?.let { String(it.bytes(), Charsets.UTF_8) }
                ?: ""

            respond(
                content = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "عايز اعرف اسعار التيشيرتات"
                              }
                            ],
                            "role": "model"
                          },
                          "finishReason": "STOP"
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine)
        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client
        )

        val sampleAudioBytes = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        val tempAudio = tempFolder.newFile("sample_voice.ogg").apply {
            writeBytes(sampleAudioBytes + ByteArray(500) { 1 })
        }

        val result = extractor.callGeminiAudioTranscription(tempAudio, "test_gemini_key_123")
        assertNotNull(result)
        assertEquals("عايز اعرف اسعار التيشيرتات", result)

        // Verify URL & Query param
        assertTrue(interceptedUrl.startsWith("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
        assertTrue(interceptedUrl.contains("key=test_gemini_key_123"))
        assertTrue(interceptedContentType.contains("application/json"))

        // Verify payload JSON structure
        val root = Json.parseToJsonElement(interceptedBody).jsonObject
        val contents = root["contents"] as JsonArray
        assertEquals(1, contents.size)
        val parts = contents[0].jsonObject["parts"] as JsonArray
        assertEquals(2, parts.size)

        // Part 0: inlineData
        val inlineData = parts[0].jsonObject["inlineData"]!!.jsonObject
        assertEquals("audio/ogg", inlineData["mimeType"]!!.jsonPrimitive.content)
        assertTrue(inlineData["data"]!!.jsonPrimitive.content.isNotBlank())

        // Part 1: text prompt
        val promptText = parts[1].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals(
            "Transcribe this colloquial Egyptian Arabic voice note verbatim into plain text. Output ONLY the plain transcription without any commentary, markdown, or formatting.",
            promptText
        )
    }

    @Test
    fun `transcribeVoiceNote falls back to Gemini Audio when Qwen FunASR fails`() = runBlocking {
        var qwenCalled = false
        var geminiCalled = false
        var groqCalled = false

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("qwencloudapi") -> {
                    qwenCalled = true
                    respond(
                        content = """{"error": "Internal server error"}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("generativelanguage.googleapis.com") -> {
                    geminiCalled = true
                    respond(
                        content = """
                            {
                              "candidates": [
                                {
                                  "content": {
                                    "parts": [
                                      {
                                        "text": "هو الاوردر هيوصل امتى لو سمحت؟"
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("groq.com") -> {
                    groqCalled = true
                    respond(
                        content = """{"text": "groq fallback"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine)
        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client,
            groqPrimaryApiKey = "groq_key",
            groqFallbackApiKey = "groq_fallback",
            qwenApiKey = "qwen_key",
            geminiApiKey = "gemini_key"
        )

        val tempAudio = tempFolder.newFile("test_voice_cascade.opus").apply {
            writeBytes(ByteArray(1024) { 5 })
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("هو الاوردر هيوصل امتى لو سمحت؟", result)
        assertTrue("Qwen should have been called first", qwenCalled)
        assertTrue("Gemini should have been called after Qwen failed", geminiCalled)
        assertFalse("Groq should NOT have been called since Gemini succeeded", groqCalled)
    }

    @Test
    fun `transcribeVoiceNote falls back to Gemini Audio when Qwen FunASR is absent`() = runBlocking {
        var geminiCalled = false
        var groqCalled = false

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("generativelanguage.googleapis.com") -> {
                    geminiCalled = true
                    respond(
                        content = """
                            {
                              "candidates": [
                                {
                                  "content": {
                                    "parts": [
                                      {
                                        "text": "بكام الشحن لإسكندرية؟"
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("groq.com") -> {
                    groqCalled = true
                    respond(
                        content = """{"text": "groq"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine)
        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client,
            groqPrimaryApiKey = "groq_key",
            groqFallbackApiKey = "groq_fallback",
            qwenApiKey = "",
            geminiApiKey = "gemini_key"
        )

        val tempAudio = tempFolder.newFile("test_voice_no_qwen.opus").apply {
            writeBytes(ByteArray(1024) { 6 })
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("بكام الشحن لإسكندرية؟", result)
        assertTrue("Gemini should have been called", geminiCalled)
        assertFalse("Groq should NOT have been called", groqCalled)
    }

    @Test
    fun `callGeminiAudioTranscription falls back to secondary model on 404 or error`() = runBlocking {
        val invokedModels = mutableListOf<String>()

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("models/gemini-3.5-flash-lite:generateContent") -> {
                    invokedModels.add("gemini-3.5-flash-lite")
                    respond(
                        content = """{"error": {"code": 404, "message": "Model not found"}}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("models/gemini-2.5-flash-lite:generateContent") -> {
                    invokedModels.add("gemini-2.5-flash-lite")
                    respond(
                        content = """
                            {
                              "candidates": [
                                {
                                  "content": {
                                    "parts": [
                                      {
                                        "text": "مقاس 2XL متاح؟"
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine)
        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = client
        )

        val tempAudio = tempFolder.newFile("test_gemini_fallback.opus").apply {
            writeBytes(ByteArray(1024) { 7 })
        }

        val result = extractor.callGeminiAudioTranscription(tempAudio, "test_api_key")
        assertNotNull(result)
        assertEquals("مقاس 2XL متاح؟", result)
        assertEquals(listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite"), invokedModels)
    }

    @Test
    fun `parseGeminiAudioResponse strips quotes and preambles cleanly`() {
        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(MockEngine { respond("") }))

        val responseWithPreamble = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Transcription: \"عايز هودي مقاس لارج لو سمحت\""
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = extractor.parseGeminiAudioResponse(responseWithPreamble)
        assertEquals("عايز هودي مقاس لارج لو سمحت", parsed)
    }

    @Test
    fun `transcribeVoiceNote routes through OpenRouter when OpenRouter model is tagged with AUDIO`() = runBlocking {
        var openRouterCalled = false
        var capturedModel: String? = null

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            if (url.contains("openrouter.ai/api/v1/audio/transcriptions")) {
                openRouterCalled = true
                val bodyStr = (request.body as? io.ktor.http.content.TextContent)?.text
                    ?: (request.body as? io.ktor.http.content.ByteArrayContent)?.let { String(it.bytes(), Charsets.UTF_8) }
                    ?: ""
                val json = Json.parseToJsonElement(bodyStr).jsonObject
                capturedModel = json["model"]?.jsonPrimitive?.content
                respond(
                    content = """{"text": "عايز اطلب هودي اسود مقاس XL"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = "error",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }
        }

        val extractor = WhatsAppMediaExtractor(
            mockContext,
            HttpClient(mockEngine),
            groqPrimaryApiKey = "",
            groqFallbackApiKey = "",
            qwenApiKey = "",
            geminiApiKey = "",
            openRouterPrimaryApiKey = "sk-or-test-key",
            openRouterFallbackApiKey = ""
        )

        val tempAudio = tempFolder.newFile("test_openrouter_voice.ogg").apply {
            writeBytes(ByteArray(1024) { 8 })
        }

        val result = extractor.transcribeVoiceNote(
            audioFile = tempAudio,
            audioModelsOverride = null,
            openRouterAudioModels = listOf("openai/whisper-large-v3")
        )

        assertNotNull(result)
        assertEquals("عايز اطلب هودي اسود مقاس XL", result)
        assertTrue("OpenRouter audio endpoint should be called", openRouterCalled)
        assertEquals("openai/whisper-large-v3", capturedModel)
    }

    @Test
    fun `callOpenRouterAudioTranscription falls back to chat completions when STT endpoint fails`() = runBlocking {
        var transcriptionsCalled = false
        var chatCompletionsCalled = false

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/api/v1/audio/transcriptions") -> {
                    transcriptionsCalled = true
                    respond(
                        content = """{"error": {"code": 404, "message": "Endpoint not supported for this model"}}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("/api/v1/chat/completions") -> {
                    chatCompletionsCalled = true
                    respond(
                        content = """
                            {
                              "choices": [
                                {
                                  "message": {
                                    "role": "assistant",
                                    "content": "مساء الخير، لو سمحت السعر كام؟"
                                  }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond(
                    content = "not found",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }
        }

        val extractor = WhatsAppMediaExtractor(mockContext, HttpClient(mockEngine))
        val tempAudio = tempFolder.newFile("multimodal_voice.ogg").apply {
            writeBytes(ByteArray(1024) { 9 })
        }

        val result = extractor.callOpenRouterAudioTranscription(tempAudio, "test_key", "google/gemini-2.5-flash")
        assertNotNull(result)
        assertEquals("مساء الخير، لو سمحت السعر كام؟", result)
        assertTrue("Transcriptions endpoint should be tried first", transcriptionsCalled)
        assertTrue("Chat completions endpoint should be called as fallback", chatCompletionsCalled)
    }

    @Test
    fun `transcribeVoiceNote uses custom OpenRouter API key from secure credentials`() = runBlocking {
        var authHeaderReceived: String? = null
        val mockEngine = MockEngine { request ->
            authHeaderReceived = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"text": "مرحبا بك"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockCreds = mockk<com.example.persona.features.settings.domain.repository.SecureCredentialsRepository>(relaxed = true)
        every { mockCreds.getApiKeys(com.example.persona.features.settings.domain.repository.ModelProviders.OPENROUTER) } returns listOf("sk-or-custom-vault-key")

        val extractor = WhatsAppMediaExtractor(
            mockContext,
            HttpClient(mockEngine),
            groqPrimaryApiKey = "",
            groqFallbackApiKey = "",
            qwenApiKey = "",
            geminiApiKey = "",
            openRouterPrimaryApiKey = "",
            openRouterFallbackApiKey = "",
            secureCredentialsRepository = mockCreds
        )

        val tempAudio = tempFolder.newFile("test_custom_or_voice.ogg").apply {
            writeBytes(ByteArray(1024) { 5 })
        }

        val result = extractor.transcribeVoiceNote(
            audioFile = tempAudio,
            audioModelsOverride = null,
            openRouterAudioModels = listOf("openai/whisper-large-v3"),
            audioProviderPriority = listOf("openrouter")
        )

        assertNotNull(result)
        assertEquals("Bearer sk-or-custom-vault-key", authHeaderReceived)
    }

    @Test
    fun `transcribeVoiceNote respects audioProviderPriority order`() = runBlocking {
        val providersAttempted = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("groq.com") -> {
                    providersAttempted.add("groq")
                    respond(
                        content = """{"text": "رد من جروك"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("openrouter.ai") -> {
                    providersAttempted.add("openrouter")
                    respond(
                        content = """{"text": "رد من اوبن راوتر"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("error", HttpStatusCode.InternalServerError)
            }
        }

        val extractor = WhatsAppMediaExtractor(
            mockContext,
            HttpClient(mockEngine),
            groqPrimaryApiKey = "gsk-test-key",
            groqFallbackApiKey = "",
            qwenApiKey = "",
            geminiApiKey = "",
            openRouterPrimaryApiKey = "sk-or-test-key",
            openRouterFallbackApiKey = ""
        )

        val tempAudio = tempFolder.newFile("test_priority_voice.ogg").apply {
            writeBytes(ByteArray(1024) { 7 })
        }

        // Groq prioritized first
        val result = extractor.transcribeVoiceNote(
            audioFile = tempAudio,
            audioModelsOverride = null,
            openRouterAudioModels = listOf("openai/whisper-large-v3"),
            audioProviderPriority = listOf("groq", "openrouter")
        )

        assertNotNull(result)
        assertEquals(listOf("groq"), providersAttempted)
    }
}
