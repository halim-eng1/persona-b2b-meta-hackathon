package com.example.persona.features.auto_reply.data.repository

import com.example.persona.core.data.repository.UserPreferencesRepository
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.features.auto_reply.domain.model.PersonaTone
import com.example.persona.features.auto_reply.domain.model.StylePreferences
import com.example.persona.features.auto_reply.domain.model.StyleProfile
import com.example.persona.features.auto_reply.domain.modifier.StylePostProcessor
import com.example.persona.features.chat_import.domain.model.ConversationContext
import com.example.persona.features.chat_import.domain.model.IntentType
import com.example.persona.features.chat_import.domain.model.SentimentType
import com.example.persona.features.chat_import.domain.model.TopicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextBuilderTest {

    @Test
    fun `buildFirstPassSystemPrompt defaults to WITTY_EGYPTIAN`() {
        val defaultPrompt = PromptContextBuilder.buildFirstPassSystemPrompt()
        val explicitWittyPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)

        assertEquals(explicitWittyPrompt, defaultPrompt)
        assertTrue(defaultPrompt.contains("Dynamic 4-Gear Calibration"))
        assertTrue(defaultPrompt.contains("ابن بلد لمّاح، واقعي، وسريع البديهة"))
    }

    @Test
    fun `buildFinalPassSystemPrompt defaults to WITTY_EGYPTIAN`() {
        val defaultPrompt = PromptContextBuilder.buildFinalPassSystemPrompt()
        val explicitWittyPrompt = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)

        assertEquals(explicitWittyPrompt, defaultPrompt)
        assertTrue(defaultPrompt.contains("Dynamic 4-Gear Calibration"))
    }

    @Test
    fun `WITTY_EGYPTIAN prompt contains the 4-gear calibration and grounded humor`() {
        val prompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)

        assertTrue(prompt.contains("Dynamic 4-Gear Calibration"))
        assertTrue(prompt.contains("Sharp Grounded Dry Humor"))
        assertTrue(prompt.contains("Comedic Defusion"))
        assertTrue(prompt.contains("خفة دم شيك بدون قلة ذوق"))
        assertTrue(prompt.contains("ابن بلد لمّاح"))
    }

    @Test
    fun `STANDARD_POLITE prompt contains calm customer service rules and zero sarcasm`() {
        val prompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        assertTrue(prompt.contains("Welcoming, courteous, and practical"))
        assertTrue(prompt.contains("3. PERSONA ENGINE, SCRIPT PURITY, CONCISENESS & TONE:"))
        // Must NOT contain witty sarcasm calibration
        assertFalse(prompt.contains("Dynamic 4-Gear Calibration"))
        assertFalse(prompt.contains("الواقعية الساخرة"))
        assertFalse(prompt.contains("Comedic Defusion"))
        assertFalse(prompt.contains("ابن بلد لمّاح"))
    }

    @Test
    fun `invariant sections 1, 2, 4, 5, 6 remain 100 percent identical between tones in first pass`() {
        val witty = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val polite = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        // Section 1: Output format
        val sec1Witty = extractSection(witty, "1. OUTPUT FORMAT:", "2. IDENTITY & ZERO HALLUCINATION:")
        val sec1Polite = extractSection(polite, "1. OUTPUT FORMAT:", "2. IDENTITY & ZERO HALLUCINATION:")
        assertEquals("Section 1 must be identical", sec1Witty, sec1Polite)

        // Section 2: Identity & Zero Hallucination
        val sec2Witty = extractSection(witty, "2. IDENTITY & ZERO HALLUCINATION:", "3. PERSONA ENGINE, SCRIPT PURITY, CONCISENESS & TONE:")
        val sec2Polite = extractSection(polite, "2. IDENTITY & ZERO HALLUCINATION:", "3. PERSONA ENGINE, SCRIPT PURITY, CONCISENESS & TONE:")
        assertEquals("Section 2 must be identical", sec2Witty, sec2Polite)

        // Section 4: Actions & Workflow
        val sec4Witty = extractSection(witty, "4. ACTIONS & WORKFLOW:", "5. ORDER ACTIONS & EXTRACTION:")
        val sec4Polite = extractSection(polite, "4. ACTIONS & WORKFLOW:", "5. ORDER ACTIONS & EXTRACTION:")
        assertEquals("Section 4 must be identical", sec4Witty, sec4Polite)

        // Section 5: Order Actions & Extraction
        val sec5Witty = extractSection(witty, "5. ORDER ACTIONS & EXTRACTION:", "6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:")
        val sec5Polite = extractSection(polite, "5. ORDER ACTIONS & EXTRACTION:", "6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:")
        assertEquals("Section 5 must be identical", sec5Witty, sec5Polite)

        // Section 6: Out of bounds recovery
        val sec6Witty = witty.substringAfter("6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:").trim()
        val sec6Polite = polite.substringAfter("6. GENERALIZED OUT-OF-BOUNDS MARKETING RECOVERY:").trim()
        assertEquals("Section 6 must be identical", sec6Witty, sec6Polite)
    }

    @Test
    fun `mandatory HITL Arabic hand-off phrase is present in both tones`() {
        val canonicalHitl = PromptContextBuilder.CANONICAL_HITL_MESSAGE
        val witty = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val polite = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        assertTrue(witty.contains(canonicalHitl))
        assertTrue(polite.contains(canonicalHitl))
    }

    @Test
    fun `both tones contain sensible emoji rule and graceful closing anti-looping behavioral rules`() {
        val wittyFirst = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeFirst = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)
        val wittyFinal = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeFinal = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        val expectedEmojiRule = "EMOJI RULE: Respond in plain text by default. ONLY if the customer includes an emoji in their message, you may mirror with AT MOST ONE contextually appropriate emoji."

        for (prompt in listOf(wittyFirst, politeFirst, wittyFinal, politeFinal)) {
            assertTrue(prompt.contains(expectedEmojiRule))
            assertTrue(prompt.contains("GRACEFUL CLOSING & THANKS (Anti-Looping)"))
            assertFalse(prompt.contains("NEVER append 😎 to every message"))
            assertFalse(prompt.contains("العفو يا فندم، نورتنا ونتمنى لك يوم سعيد! 💙"))
        }
    }

    @Test
    fun `system prompts do not contain literal canned dialogue examples`() {
        val wittyPrompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politePrompt = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        for (prompt in listOf(wittyPrompt, politePrompt)) {
            assertFalse(prompt.contains("وعليكم السلام! منورنا"))
            assertFalse(prompt.contains("واقفين على رجلينا"))
            assertFalse(prompt.contains("في النعمة الحمد لله"))
            assertFalse(prompt.contains("أنا معاك أهو يا فندم وسامعك كويس"))
            assertFalse(prompt.contains("طب ما نشوفلك حاجة رايقة"))
            assertFalse(prompt.contains("الأوردر بيسلم عليك"))
            assertFalse(prompt.contains("الأوردر بتاعك حالته [Status]"))
        }
    }

    @Test
    fun `micro prompts contain sensible emoji rule and no canned dialogue`() {
        val banterPrompt = PromptContextBuilder.buildMicroBanterPrompt()
        val orderStatusPrompt = PromptContextBuilder.buildMicroOrderStatusPrompt()

        val expectedEmojiRule = "EMOJI RULE: Respond in plain text by default. ONLY if the customer includes an emoji in their message, you may mirror with AT MOST ONE contextually appropriate emoji."

        assertTrue(banterPrompt.contains(expectedEmojiRule))
        assertTrue(orderStatusPrompt.contains(expectedEmojiRule))
        assertFalse(banterPrompt.contains("NEVER append 😎 to every message"))
        assertFalse(orderStatusPrompt.contains("NEVER append 😎 to every message"))
        assertFalse(banterPrompt.contains("واقفين على رجلينا"))
        assertFalse(orderStatusPrompt.contains("الأوردر بتاعك حالته [Status]"))
    }

    @Test
    fun `PersonaTone deserialization and fallback`() {
        // Unknown or legacy strings fall back to WITTY_EGYPTIAN
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString(null))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("CASUAL"))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("PROFESSIONAL"))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("UNKNOWN_TONE"))

        // Known names deserialize cleanly (case-insensitive)
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("WITTY_EGYPTIAN"))
        assertEquals(PersonaTone.WITTY_EGYPTIAN, PersonaTone.fromString("witty_egyptian"))
        assertEquals(PersonaTone.STANDARD_POLITE, PersonaTone.fromString("STANDARD_POLITE"))
        assertEquals(PersonaTone.STANDARD_POLITE, PersonaTone.fromString("standard_polite"))
    }

    @Test
    fun `StylePostProcessor acts as passive pass-through preserving emojis and text`() {
        val processor = StylePostProcessor()
        val prefs = StylePreferences(
            personaTone = PersonaTone.WITTY_EGYPTIAN,
            styleProfile = StyleProfile.PROFESSIONAL
        )
        val context = ConversationContext(
            intent = IntentType.GENERAL,
            topic = TopicType.GENERAL,
            sentiment = SentimentType.POSITIVE,
            confidence = 1.0f
        )

        val input = "واقفين على رجلينا مستنيين الأوردر بتاعك أهو 😎 قولّي نجهزلك إيه؟"
        val output = processor.process(
            rawReply = input,
            prefs = prefs,
            context = context,
            userEmojiRate = 0.5f
        )

        assertEquals(input, output)
    }

    @Test
    fun `formatStoreWebsiteBlock formats non-empty url and NONE placeholder correctly`() {
        assertEquals(
            "<store_website>\nhttps://velo.ordersapps.com/ar\n</store_website>",
            PromptContextBuilder.formatStoreWebsiteBlock("https://velo.ordersapps.com/ar")
        )
        assertEquals(
            "<store_website>NONE</store_website>",
            PromptContextBuilder.formatStoreWebsiteBlock(null)
        )
        assertEquals(
            "<store_website>NONE</store_website>",
            PromptContextBuilder.formatStoreWebsiteBlock("")
        )
        assertEquals(
            "<store_website>NONE</store_website>",
            PromptContextBuilder.formatStoreWebsiteBlock("   ")
        )
    }

    @Test
    fun `both tones contain strict zero-price-guessing and store website ordering channels in both passes`() {
        val wittyFirst = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeFirst = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)
        val wittyFinal = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeFinal = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        for (prompt in listOf(wittyFirst, politeFirst, wittyFinal, politeFinal)) {
            assertTrue(prompt.contains("STRICT ZERO-PRICE-GUESSING:"))
            assertTrue(prompt.contains("NEVER estimate, guess, or hallucinate prices (e.g., NEVER say 'سعره 450 جنيه تقريباً'"))
            assertTrue(prompt.contains("STORE WEBSITE & ORDERING CHANNELS:"))
            assertTrue(prompt.contains("If <store_website> is provided and not NONE, this is the store's verified official online website."))
            assertTrue(prompt.contains("If <store_website> is NONE, state clearly and naturally that orders and inquiries are handled directly right here on WhatsApp."))
            assertFalse(prompt.contains("مفيش موقع أو فرع متسجل حالياً"))
        }
    }

    @Test
    fun `filterAndPairChatHistory purges custom media refusal messages when configured`() {
        val now = 10_000_000L
        val customFallback = "نعتذر، المتجر لا يستقبل صوراً حالياً."
        val history = listOf(
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = customFallback, isFromUser = false, timestamp = now - 500L),
            ChatHistoryEntity(id = 1L, contactName = "c", messageText = "بكام التيشيرت؟", isFromUser = true, timestamp = now - 1000L)
        )

        val filtered = PromptContextBuilder.filterAndPairChatHistory(history, now, customFallback)
        assertEquals(1, filtered.size)
        assertEquals(1L, filtered[0].id)
        assertEquals("بكام التيشيرت؟", filtered[0].messageText)
    }

    @Test
    fun `localizeOrderStatus maps database enums to authentic Egyptian Arabic`() {
        assertEquals("قيد المراجعة", PromptContextBuilder.localizeOrderStatus("PENDING"))
        assertEquals("قيد التجهيز", PromptContextBuilder.localizeOrderStatus("IN_PREPARATION"))
        assertEquals("في الطريق مع المندوب", PromptContextBuilder.localizeOrderStatus("SHIPPING_IN_PROGRESS"))
        assertEquals("تم التوصيل", PromptContextBuilder.localizeOrderStatus("SHIPPED"))
        assertEquals("ملغي", PromptContextBuilder.localizeOrderStatus("CANCELLED"))
        assertEquals("مرتجع", PromptContextBuilder.localizeOrderStatus("RETURNED"))
        // Case-insensitive
        assertEquals("قيد التجهيز", PromptContextBuilder.localizeOrderStatus("in_preparation"))
        // Unknown & null fallbacks
        assertEquals("", PromptContextBuilder.localizeOrderStatus(null))
        assertEquals("CUSTOM_STATE", PromptContextBuilder.localizeOrderStatus("CUSTOM_STATE"))
    }

    @Test
    fun `formatCustomerOrderState localizes raw order status enums and never leaks English enum`() {
        val activeOrder = OrderEntity(
            id = 101L,
            contactName = "أحمد",
            status = "IN_PREPARATION",
            orderDetails = "هودي أسود مقاس XL",
            deliveryAddress = "مدينة نصر",
            customNotes = "اتصال بعد 2 الضهر",
            timestamp = 10_000_000L
        )

        val formatted = PromptContextBuilder.formatCustomerOrderState(listOf(activeOrder))

        assertTrue(formatted.contains("Active Order: #101 [قيد التجهيز]"))
        assertTrue(formatted.contains("Order Status: قيد التجهيز"))
        assertFalse(formatted.contains("IN_PREPARATION"))
    }

    @Test
    fun `buildMicroOrderStatusPrompt includes extracted_order schema keys and order_action options`() {
        val prompt = PromptContextBuilder.buildMicroOrderStatusPrompt()

        // Verify valid order_action options
        assertTrue(prompt.contains("\"order_action\": \"CREATE\" | \"CANCEL\" | \"UPDATE\" | null"))

        // Verify extracted_order schema keys
        assertTrue(prompt.contains("\"extracted_order\": {"))
        assertTrue(prompt.contains("\"customer_name\": \"Full name or null\""))
        assertTrue(prompt.contains("\"delivery_address\": \"Detailed address or null\""))
        assertTrue(prompt.contains("\"order_details\": \"Items and sizes or null\""))
        assertTrue(prompt.contains("\"custom_notes\": \"Special instructions or null\""))

        // Verify behavioral rules for order status & updates
        assertTrue(prompt.contains("ORDER STATUS & UPDATE RULES:"))
        assertTrue(prompt.contains("If the customer confirms an address, provides missing details, or requests an update to their order, set \"order_action\": \"UPDATE\""))
        assertTrue(prompt.contains("If they explicitly request order cancellation, set \"order_action\": \"CANCEL\""))
    }

    @Test
    fun `buildVisionExtractionSystemPrompt and buildFirstPassSystemPrompt do not contain old canned hoodie example strings`() {
        val visionPrompt = PromptContextBuilder.buildVisionExtractionSystemPrompt()
        val firstPassWitty = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val firstPassPolite = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)
        val finalPassWitty = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val finalPassPolite = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        val cannedStrings = listOf("NO SYSTEM IS SAFE", "أنا مش بني آدم أنا مهندس")

        for (prompt in listOf(visionPrompt, firstPassWitty, firstPassPolite, finalPassWitty, finalPassPolite)) {
            for (canned in cannedStrings) {
                assertFalse("Prompt must not contain canned string '$canned'", prompt.contains(canned))
            }
        }
    }

    @Test
    fun `buildMicroOrderStatusPrompt and buildMicroBanterPrompt support PersonaTone and conciseness directives`() {
        val concisenessDirective = "Match reply length strictly to the question: keep replies to 1–3 short, natural lines maximum unless specific order or catalog details require slightly more."

        val wittyBanter = PromptContextBuilder.buildMicroBanterPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeBanter = PromptContextBuilder.buildMicroBanterPrompt(tone = PersonaTone.STANDARD_POLITE)
        val wittyOrderStatus = PromptContextBuilder.buildMicroOrderStatusPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val politeOrderStatus = PromptContextBuilder.buildMicroOrderStatusPrompt(tone = PersonaTone.STANDARD_POLITE)

        // Conciseness directive present across all micro prompts and tones
        assertTrue(wittyBanter.contains(concisenessDirective))
        assertTrue(politeBanter.contains(concisenessDirective))
        assertTrue(wittyOrderStatus.contains(concisenessDirective))
        assertTrue(politeOrderStatus.contains(concisenessDirective))

        // Tone calibration for WITTY_EGYPTIAN
        assertTrue(wittyBanter.contains("ابن بلد لمّاح، واقعي، وسريع البديهة"))
        assertTrue(wittyOrderStatus.contains("ابن بلد لمّاح، واقعي، وسريع البديهة"))
        assertFalse(wittyBanter.contains("العامية المصرية المهذبة"))

        // Tone calibration for STANDARD_POLITE
        assertTrue(politeBanter.contains("Welcoming, courteous, and practical customer service representative"))
        assertTrue(politeOrderStatus.contains("Welcoming, courteous, and practical customer service representative"))
        assertFalse(politeBanter.contains("ابن بلد لمّاح"))
        assertFalse(politeOrderStatus.contains("ابن بلد لمّاح"))
    }

    @Test
    fun `voice note ASR handling directive is present in generated system prompts`() {
        val firstWitty = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val firstPolite = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)
        val finalWitty = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val finalPolite = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)

        val asrDirective = "VOICE NOTE & ASR HANDLING:"
        val asrPrefix = "[رسالة صوتية من العميل]:"
        val asrPhoneRule = "normalize into a standard 11-digit Egyptian mobile number (010, 011, 012, or 015) without duplicating spoken digits."

        for (prompt in listOf(firstWitty, firstPolite, finalWitty, finalPolite)) {
            assertTrue("System prompt must contain ASR header", prompt.contains(asrDirective))
            assertTrue("System prompt must contain voice note prefix directive", prompt.contains(asrPrefix))
            assertTrue("System prompt must contain phone normalization rule", prompt.contains(asrPhoneRule))
        }
    }

    @Test
    fun `text-only bot invariant fake media ban is present in all prompt variants and tones`() {
        val expectedInvariant = "TEXT-ONLY BOT INVARIANT: You are a text-only automated WhatsApp assistant. You CANNOT send, attach, or transmit images/photos. NEVER say 'بعتهالك' or promise 'هجيبلك صور' or say 'بص عليها' when no image exists. Direct customers to <store_website> to view photos and full catalogs."

        assertTrue(PromptContextBuilder.SECTION_3_WITTY_EGYPTIAN.contains(expectedInvariant))
        assertTrue(PromptContextBuilder.SECTION_3_STANDARD_POLITE.contains(expectedInvariant))

        val firstWitty = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val firstPolite = PromptContextBuilder.buildFirstPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)
        val finalWitty = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val finalPolite = PromptContextBuilder.buildFinalPassSystemPrompt(tone = PersonaTone.STANDARD_POLITE)
        val banterWitty = PromptContextBuilder.buildMicroBanterPrompt(tone = PersonaTone.WITTY_EGYPTIAN)
        val banterPolite = PromptContextBuilder.buildMicroBanterPrompt(tone = PersonaTone.STANDARD_POLITE)

        for (prompt in listOf(firstWitty, firstPolite, finalWitty, finalPolite, banterWitty, banterPolite)) {
            assertTrue("Prompt must contain text-only invariant", prompt.contains(expectedInvariant))
        }
    }

    @Test
    fun `buildMicroBanterPrompt includes dynamic store_specialty and RE_QUERY schema and directives`() {
        val customTopics = listOf("هوديز قطن 850 جرام", "تيشيرتات مبرمجين", "مجات سيراميك")
        val promptWithTopics = PromptContextBuilder.buildMicroBanterPrompt(
            storeName = "Velo Store",
            storeTopics = customTopics
        )

        assertTrue(promptWithTopics.contains("<store_specialty>"))
        assertTrue(promptWithTopics.contains("Velo Store specializes exclusively in the following categories and topics:"))
        assertTrue(promptWithTopics.contains("- هوديز قطن 850 جرام"))
        assertTrue(promptWithTopics.contains("- تيشيرتات مبرمجين"))
        assertTrue(promptWithTopics.contains("- مجات سيراميك"))
        assertTrue(promptWithTopics.contains("STRICT BOUNDARY: Answer strictly within these listed categories."))

        val defaultPrompt = PromptContextBuilder.buildMicroBanterPrompt(storeName = "Velo Store")
        assertTrue(defaultPrompt.contains("<store_specialty>"))
        assertTrue(defaultPrompt.contains("STRICT BOUNDARY: Answer on behalf of Velo Store strictly within verified store offerings. NEVER invent products, inventory, or categories not officially offered by Velo Store."))

        // Schema assertions
        assertTrue(defaultPrompt.contains("\"action\": \"REPLY\" | \"RE_QUERY\""))
        assertTrue(defaultPrompt.contains("\"refined_search_keyword\": \"2-4 formal Arabic search keywords if customer asks about catalog/products, or null\""))

        // Directive assertions
        assertTrue(defaultPrompt.contains("output \"action\": \"RE_QUERY\" with 2–4 formal Arabic keywords in refined_search_keyword to look up the catalog."))
    }

    @Test
    fun `filterAndPairChatHistory preserves an entire rapid burst of 8 consecutive customer messages as a single turn without dropping the root complaint`() {
        val now = 10_000_000L
        val burstMessages = listOf(
            "حد يرد يا جماعة",
            "مستني ردكم",
            "اتصرفوا",
            "واخد فلوسي",
            "أنا عايز ارجعه",
            "والخامات نص كم",
            "والطباعة مش واضحة",
            "التيشيرت مقطوع من الكم" // Root complaint, earliest in burst
        )

        // Stored in DB descending by timestamp
        val historyDesc = burstMessages.mapIndexed { index, text ->
            ChatHistoryEntity(
                id = (8 - index).toLong(),
                contactName = "Halim",
                messageText = text,
                isFromUser = true,
                timestamp = now - (index * 1000L)
            )
        }

        val filtered = PromptContextBuilder.filterAndPairChatHistory(historyDesc, nowMs = now)

        assertEquals("All 8 messages in the user burst must be preserved", 8, filtered.size)
        assertEquals("The earliest root complaint must be first in chronological order", "التيشيرت مقطوع من الكم", filtered[0].messageText)
        assertEquals("The latest burst message must be last in chronological order", "حد يرد يا جماعة", filtered[7].messageText)
    }

    @Test
    fun `filterAndPairChatHistory preserves multi-turn dialogue with previous assistant turns`() {
        val now = 10_000_000L
        val historyDesc = listOf(
            ChatHistoryEntity(id = 6L, contactName = "c", messageText = "عايز أعرف الميعاد", isFromUser = true, timestamp = now - 100L),
            ChatHistoryEntity(id = 5L, contactName = "c", messageText = "فين الشحنة؟", isFromUser = true, timestamp = now - 200L),
            ChatHistoryEntity(id = 4L, contactName = "c", messageText = "الطلب اتأخر", isFromUser = true, timestamp = now - 300L),
            ChatHistoryEntity(id = 3L, contactName = "c", messageText = "وعليكم السلام يا فندم، أقدر أساعدك إزاي؟", isFromUser = false, timestamp = now - 1000L),
            ChatHistoryEntity(id = 2L, contactName = "c", messageText = "السلام عليكم", isFromUser = true, timestamp = now - 2000L)
        )

        val filtered = PromptContextBuilder.filterAndPairChatHistory(historyDesc, nowMs = now)

        assertEquals(5, filtered.size)
        assertEquals("السلام عليكم", filtered[0].messageText)
        assertEquals("وعليكم السلام يا فندم، أقدر أساعدك إزاي؟", filtered[1].messageText)
        assertEquals("الطلب اتأخر", filtered[2].messageText)
        assertEquals("عايز أعرف الميعاد", filtered[4].messageText)
    }

    @Test
    fun `Section 3 in both tones includes pragmatic colloquial metaphor decoding for Egyptian expressions`() {
        val witty = PromptContextBuilder.getSection3Prompt(PersonaTone.WITTY_EGYPTIAN)
        val polite = PromptContextBuilder.getSection3Prompt(PersonaTone.STANDARD_POLITE)

        for (section3 in listOf(witty, polite)) {
            assertTrue("Must include pragmatic semantics heading", section3.contains("PRAGMATIC SEMANTICS & COLLOQUIAL METAPHORS"))
            assertTrue("Must explain 'نص كم' idiom", section3.contains("نص كم"))
            assertTrue("Must prohibit literal catalog mapping", section3.contains("NEVER interpret them literally as physical catalog attributes"))
            assertTrue("Must mention quality descriptors", section3.contains("أي كلام") && section3.contains("مضروب"))
        }
    }

    @Test
    fun `Section 4 in both first and final pass includes multi-topic synthesis prioritizing high-stakes containment`() {
        val firstPass = PromptContextBuilder.buildFirstPassSystemPrompt()
        val finalPass = PromptContextBuilder.buildFinalPassSystemPrompt()

        for (prompt in listOf(firstPass, finalPass)) {
            assertTrue("Must include multi-topic synthesis rule", prompt.contains("MULTI-TOPIC SYNTHESIS & ESCALATION PRIORITY"))
            assertTrue("Must prioritize high-stakes financial/damage containment", prompt.contains("ALWAYS prioritize containment of the highest-stakes issue"))
            assertTrue("Must acknowledge physical evidence", prompt.contains("Explicitly acknowledge any visible physical evidence"))
        }
    }

    private fun extractSection(fullText: String, startHeader: String, endHeader: String): String {
        return fullText.substringAfter(startHeader).substringBefore(endHeader).trim()
    }
}
