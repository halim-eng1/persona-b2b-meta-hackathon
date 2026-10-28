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
}
