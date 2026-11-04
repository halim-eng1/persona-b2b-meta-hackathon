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
    fun `transcribeVoiceNote returns Meta Muse Voice transcription`() = runBlocking {
        val mockMetaClient = io.mockk.mockk<com.example.persona.features.auto_reply.data.providers.MetaModelApiClient>()
        io.mockk.coEvery { mockMetaClient.transcribeAudio(any(), any()) } returns
            Result.success("عايز أعرف سعر التيشيرت الأسود مقاس لارج لو سمحت")

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = HttpClient(MockEngine { respond("") }),
            metaModelApiClient = mockMetaClient
        )

        val tempAudio = tempFolder.newFile("sample.opus").apply {
            writeBytes(ByteArray(1024) { 1 })
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("عايز أعرف سعر التيشيرت الأسود مقاس لارج لو سمحت", result)
        io.mockk.coVerify(exactly = 1) { mockMetaClient.transcribeAudio(tempAudio, "ar") }
    }

    @Test
    fun `transcribeVoiceNote returns null when Meta transcription fails`() = runBlocking {
        val mockMetaClient = io.mockk.mockk<com.example.persona.features.auto_reply.data.providers.MetaModelApiClient>()
        io.mockk.coEvery { mockMetaClient.transcribeAudio(any(), any()) } returns
            Result.failure(IllegalStateException("rate limited"))

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = HttpClient(MockEngine { respond("") }),
            metaModelApiClient = mockMetaClient
        )

        val tempAudio = tempFolder.newFile("voice.opus").apply {
            writeBytes(ByteArray(1024) { 2 })
        }

        assertNull(extractor.transcribeVoiceNote(tempAudio))
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
    fun `transcribeVoiceNote forwards the original opus file to MetaModelApiClient`() = runBlocking {
        val mockMetaClient = io.mockk.mockk<com.example.persona.features.auto_reply.data.providers.MetaModelApiClient>()
        io.mockk.coEvery { mockMetaClient.transcribeAudio(any(), any()) } returns
            Result.success("عايز اعرف اسعار الهوديز")

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = HttpClient(MockEngine { respond("") }),
            metaModelApiClient = mockMetaClient
        )

        // WhatsApp file named with .opus extension
        val tempAudio = tempFolder.newFile("PTT-20261028-WA0009.opus").apply {
            writeBytes(ByteArray(1024) { 7 })
        }

        val result = extractor.transcribeVoiceNote(tempAudio)
        assertNotNull(result)
        assertEquals("عايز اعرف اسعار الهوديز", result)
        io.mockk.coVerify(exactly = 1) { mockMetaClient.transcribeAudio(tempAudio, "ar") }
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
        val mockMetaClient = io.mockk.mockk<com.example.persona.features.auto_reply.data.providers.MetaModelApiClient>()
        io.mockk.coEvery { mockMetaClient.transcribeAudio(any(), any()) } returns Result.success("مرحباً")

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = HttpClient(MockEngine { respond("") }),
            metaModelApiClient = mockMetaClient
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
    fun `transcribeVoiceNote invokes MetaModelApiClient when meta is prioritized`() = runBlocking {
        val mockMetaClient = io.mockk.mockk<com.example.persona.features.auto_reply.data.providers.MetaModelApiClient>()
        io.mockk.coEvery { mockMetaClient.transcribeAudio(any(), any()) } returns Result.success("تفريغ صوتي من ميتا")

        val extractor = WhatsAppMediaExtractor(
            context = mockContext,
            httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { respond("") }),
            metaModelApiClient = mockMetaClient
        )

        val tempAudio = tempFolder.newFile("test_meta_voice.ogg").apply {
            writeBytes(ByteArray(512) { 1 })
        }

        val result = extractor.transcribeVoiceNote(tempAudio)

        assertEquals("تفريغ صوتي من ميتا", result)
        io.mockk.coVerify(exactly = 1) { mockMetaClient.transcribeAudio(tempAudio, "ar") }
    }
}
