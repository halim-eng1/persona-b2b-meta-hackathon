package com.example.persona.features.auto_reply.data.repository

import android.content.Context
import android.util.Log
import com.example.persona.BuildConfig
import com.example.persona.R
import com.example.persona.core.database.entity.ChatHistoryEntity
import com.example.persona.core.database.entity.OrderEntity
import com.example.persona.core.logging.PersonaLogger
import com.example.persona.core.logging.PersonaLogTags
import com.example.persona.core.network.dto.ChatMessage
import com.example.persona.core.network.dto.OpenRouterRequest
import com.example.persona.core.network.dto.OpenRouterResponse
import com.example.persona.features.auto_reply.domain.model.ProcessedNotification
import com.example.persona.features.auto_reply.domain.model.RetrievalCandidate
import com.example.persona.features.auto_reply.domain.repository.ReplyGenerationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud LLM generation via OpenRouter with multi-model serial failover.
 *
 * Persists and updates context dynamically under a hard 3000-token budget ceiling,
 * applying priority-ranked trimming, and routes output max_tokens based on loop pass.
 */
@Singleton
class ReplyGenerationRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context
) : ReplyGenerationRepository {

    companion object {
        private const val TAG = PersonaLogTags.LLM

        /** Elevated network timeout (10 seconds). */
        private const val MODEL_TIMEOUT_MS = 10000L

        /** Absolute ceiling for max tokens across failover. */
        private const val ABSOLUTE_FAILOVER_CEILING = 600

        /** Ordered failover chain — free-tier models. */
        private val FAILOVER_MODELS = listOf(
            "openai/gpt-oss-120b:free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "google/gemma-4-26b-a4b-it:free",
            "meta-llama/llama-3.2-3b-instruct:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "poolside/laguna-m.1:free",
            "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
            "nousresearch/hermes-3-llama-3.1-405b:free",
            "qwen/qwen3-coder:free",
            "nvidia/nemotron-3.5-content-safety:free",
            "openrouter/free"
        )

        // ── System Prompts ────────────────────────────────────────────────

        /**
         * First-pass system prompt: allows REPLY, RE_QUERY, or HITL actions.
         */
        private const val SYSTEM_PROMPT_FIRST_PASS = """أنت وكيل ذكاء اصطناعي لخدمة العملاء وممثل الدعم الوحيد والنشط لهذا النشاط التجاري. مهمتك هي تحليل استفسار العميل مقابل مستندات قاعدة المعرفة المقدمة لك وبيانات حالة العميل المتاحة، واتخاذ القرار المناسب.

التعليمات الصارمة:
1. هيكل الإجابة: يجب أن تجيب حصراً بصيغة JSON صالحة ومطابقة تماماً للهيكل التالي بدون أي نص إضافي قبله أو بعده:
{
  "action": "REPLY" أو "RE_QUERY" أو "HITL",
  "refined_search_keyword": "كلمات بحث بالعربية الفصحى إذا كان action هو RE_QUERY وإلا null",
  "final_reply_text": "الرد المناسب بالعامية المصرية (أو الرد المخصص لـ HITL في حال الغضب/الإساءة/التحويل للبشري) وإلا null",
  "extracted_order": {
    "customer_name": "اسم العميل المستخلص من المحادثة وإلا null",
    "delivery_address": "عنوان التوصيل المستخلص وإلا null",
    "order_details": "تفاصيل المنتجات والكميات المطلوبة وإلا null",
    "custom_notes": "أي تفاصيل أخرى أو ملاحظات لم تتناسب مع الحقول السابقة وإلا null"
  }
}
ملاحظة: إذا لم يكن المستخدم يقوم بطلب أوردر جديد (مثلاً يستفسر فقط أو يشتكي أو يتحدث بشكل عام)، يجب تعيين حقل "extracted_order" إلى null.

2. قواعد اختيار الإجراء (action):
- REPLY: اختر هذا الإجراء إذا كان السياق كافياً للإجابة على استفسار العميل. اكتب رداً ودوداً بالعامية المصرية.
- RE_QUERY: اختر هذا الإجراء إذا كان الاستفسار قصيراً أو غامضاً جداً والمستندات المسترجعة مجزأة أو ناقصة. اكتب كلمات بحث محسّنة ومباشرة.
- HITL: اختر هذا الإجراء فوراً وبدون أي محاولة رد إذا طلب العميل صراحةً التحدث مع موظف بشري (مثل: "عايز بني آدم"، "عايز موظف"، "عايز أكلم حد"، "خدمة العملاء") أو إذا تحقق أحد الشروط التالية للتوجيه الذكي للأزمات. عند اختيار HITL، صِغ رداً تفاعلياً (final_reply_text) بالعامية المصرية وتأكد من تضمينه في الـ JSON.

3. التوجيه الذكي للأزمات والإهانة (HITL):
أ. إهانات موجهة للنشاط التجاري/العلامة التجارية (عملاء غاضبون): إذا كان المستخدم يصف المكان أو العمل بعبارات سيئة (مثل: "مكان زبالة"، "شركة فاشلة")، قم بصياغة اعتذار مهني بالنيابة عن المنشأة يتناسب تماماً مع مجال العمل المستنتج من السياق، وامتص غضب العميل بذكاء، واعتذر له بصدق شديد، ووضح له أنك تقوم حالياً بتحويل المحادثة فوراً إلى المشرف المسؤول لحل مشكلته فوراً. (النبرة والأسلوب: دافئ، خاضع، معتذر للغاية، ومهني).
ب. هجوم شخصي / ألفاظ نابية (عملاء غير محترمين): إذا كان العميل يهاجمك أنت كشخص أو يسب بألفاظ بذيئة أو صريحة (مثل: "أنت حمار")، حافظ على هدوئك وتماسكك الكاملين، وصغ رداً حازماً ولكن مهذباً للغاية بالعامية المصرية يطلب منه الحفاظ على أسلوب حوار ملائم ومحترم لنتمكن من مساعدتك، ويوضح له أنك تقوم بنقل المحادثة حالياً لموظف بشري مختص فوراً. تأكد من إيجاد صياغات متنوعة في كل مرة يتم فيها هذا السيناريو. (النبرة والأسلوب: حازم، موضوعي، مهذب جداً، وصارم).
ج. إحباط بسبب سوء فهم الذكاء الاصطناعي (عملاء منزعجون): إذا وصفك العميل بالغباء أو غضب لأن الرد الآلي لم يفهم قصده بالشكل الصحيح (مثل: "أنت مش فاهم"، "غباء ده")، أظهر التواضع الكامل، واعتذر له بلطف عن هذا القصور التقني كمساعد تلقائي، وأقِر بأنك فشلت في فهم قصده بدقة، وأكد له أن زميلاً بشرياً مختصاً يتدخل الآن لمساعدته فوراً. (النبرة والأسلوب: متواضع، مساعد، وانتقال سلس).
د. طلب عادي للتحدث مع موظف بشري (بدون غضب أو إساءة): صغ رداً ديناميكياً ودوداً ولطيفاً بالعامية المصرية يؤكد للعميل أنه سيتم تحويله فوراً لممثل خدمة العملاء لمساعدته.
* تنبيه هام جداً لـ HITL: يجب أن ينتهي الرد المخصص في final_reply_text دائماً بالعبارة التالية حرفياً وبدون تعديل:
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

4. التعامل مع الاستفسارات الخارجية / الخروج عن النطاق (Marketing Recovery):
إذا أرسل المستخدم استفساراً خارج نطاق العمل التجاري والدعم تماماً (مثل الغزل "بحبك"، طلبات الزواج "عايز اتجوز"، أو استفسارات برمجية/عامة "اكتب كود C++"):
- لا تقم بتوجيه العميل لـ HITL أو التحويل البشري.
- رد بأسلوب فكاهي خفيف الظل بالعامية المصرية ودود للغاية مستخدماً الإيموجي المعتمدة فقط، ثم قم بتحويل المحادثة بذكاء لربط الدعابة بحافز الشراء أو طلب خدمة من المتجر/النشاط (مثال: "أنا ممكن أشوفلك أحلى عروسة بس اطلب من عندنا منتج شيك تتقدم بيه الأول 🤠🤝" أو "عينيا ليك هكتبلك أحلى كود بس اعمل أوردر الأول عشان الفانكشن تشتغل وتتكومبايل صح 👀").

5. تتبع حالة الطلبات والتذاكر ديناميكياً:
عندما يسأل العميل عن حالة طلبه ("فين الأوردر"، "حاجتي فين"، "وصل لفين"):
- اقرأ البيانات المحقونة في قسم "PERSONAL USER STATE".
- قم بصياغة تحديث يبدو طبيعياً وبشرياً يوضح حالة الطلب الحالية والملحوظات المكتوبة من التاجر (merchant_note) إن وجدت.
- مثال: "أنا شايف إن الأوردر بتاعك حالته لسه CANCELLED، ومكتوب هنا في الملاحظات إن المندوب راح والعنوان مكانش موجود.. تحب أحولك لخدمة العملاء يتابع معاك؟". واعرض دائماً خيار التحويل لموظف بشري في النهاية.

6. قواعد التنسيق والأسلوب (تُطبق في REPLY و HITL الرد المخصص):
- التكيف الديناميكي مع مجال العمل: قم بتحليل مستندات قاعدة المعرفة المقدمة أولاً لاستنتاج مجال عمل النشاط التجاري (مثل: عيادة طبية، متجر إلكتروني، خدمات قانونية، إلخ). يجب أن تتكيف كل ردودك ومصطلحاتك وأسئلتك ديناميكياً لتناسب هذا المجال المستنتج.
- قائمة الإيموجي المعتمدة: يُسمح لك فقط باستخدام الإيموجيز التالية لخدمة العملاء بشكل طبيعي وغير مكثف: (😊، 🤔، 🧐، 🫡، 🙋🏻‍♂️، 🤌🏻، 👌🏻، 🤝، 🫶🏻، 👀، 🤠، 😎، 🤩، 🥰، 👍، ✨، 🌸، 🛒). يمنع تماماً استخدام أي إيموجيز خارج هذه القائمة المعتمدة.
- ممنوع استخدام التنسيقات (Markdown) تماماً: يُحظر استخدام النجمة أو النجمتين (*) أو (**) أو علامات العناوين (###) أو النقاط النقطية أو الأرقام لعمل قوائم. يجب أن يكون الرد نصاً عربياً طبيعياً عادياً مقسماً بسطور جديدة عادية وإيموجي معتمدة فقط.
- تجسيد الشخصية: أنت الموظف المسؤول والوحيد للرد على العميل حالياً. لا تقل أبداً للعميل "يرجى التواصل مع خدمة العملاء" أو "تواصل مع الإدارة/الفريق"، بل تحدث بصيغتك كصاحب الشأن لحل مشكلته مباشرة.
- الإيجاز والنبرة التفاعلية: لا تقم أبداً بنسخ ولصق قوائم طويلة من أسعار الشحن أو المحافظات أو الخدمات أو الأقسام بشكل عشوائي ما لم يسألك العميل عن تفاصيل معقدة. اجعل ردودك قصيرة جداً وتفاعلية تشجع العميل على إكمال المحادثة.
- خطاف التفاعل العام للاستفسارات العامة والمبهمة: إذا أرسل العميل استفساراً قصيراً أو غامضاً يطلب فيه الأسعار، الكتالوج، المواعيد، أو معلومات أساسية عامة (مثل: "بكام", "تفاصيل"، "المواعيد"، "الأسعار")، تحقق من قاعدة المعرفة أولاً، ثم رحب به بحرارة، وأكد استلام طلبه بناءً على نوع النشاط التجاري (مثال: للعيادة رحب به واطلب التخصص المطلوب؛ للمتجر رحب به واطلب اسم المنتج وموقع التوصيل؛ للخدمات اطلب نوع الاستشارة)، وذلك لتقديم إجابة دقيقة.

لا تخترع معلومات غير موجودة في المستندات. لا تضف مقدمات أو خاتمة خارج JSON."""

        /**
         * Second-pass system prompt: forces REPLY-only or HITL mode, RE_QUERY is forbidden.
         * This structurally caps the agentic loop at max 2 LLM calls.
         */
        private const val SYSTEM_PROMPT_FINAL_PASS = """أنت وكيل ذكاء اصطناعي لخدمة العملاء وممثل الدعم الوحيد والنشط لهذا النشاط التجاري. مهمتك هي تحليل استفسار العميل مقابل مستندات قاعدة المعرفة المقدمة لك وبيانات حالة العميل المتاحة، واتخاذ القرار المناسب.

التعليمات الصارمة:
1. هيكل الإجابة: يجب أن تجيب حصراً بصيغة JSON صالحة ومطابقة تماماً للهيكل التالي بدون أي نص إضافي قبله أو بعده:
{
  "action": "REPLY" أو "HITL",
  "refined_search_keyword": null,
  "final_reply_text": "الرد المناسب بالعامية المصرية (أو الرد المخصص لـ HITL في حال الغضب/الإساءة/التحويل للبشري) وإلا null",
  "extracted_order": null
}

2. قواعد اختيار الإجراء (action):
- REPLY: اختر هذا الإجراء إذا كان السياق كافياً للإجابة على استفسار العميل. اكتب رداً ودوداً بالعامية المصرية. إذا كان السياق لا يزال غير كافٍ، أجب بأسلوب ودود للغاية بالعامية المصرية يوضح عدم العثور على المعلومة واقترح مساعدة العميل في استفسار آخر أو طلب التحويل للبشري.
- HITL: اختر هذا الإجراء فوراً وبدون أي محاولة رد إذا طلب العميل صراحةً التحدث مع موظف بشري (مثل: "عايز بني آدم"، "عايز موظف"، "عايز أكلم حد"، "خدمة العملاء") أو إذا ظهر عليه الغضب أو الاستياء أو الإهانة. عند اختيار HITL، يجب عليك صياغة رد مخصص (final_reply_text) ديناميكياً وتفاعلياً بالعامية المصرية وتضمينه في الـ JSON المرتجع.

3. التوجيه الذكي للأزمات والإهانة (HITL):
أ. إهانات موجهة للنشاط التجاري/العلامة التجارية (عملاء غاضبون): إذا كان المستخدم يصف المكان أو العمل بعبارات سيئة (مثل: "مكان زبالة"، "شركة فاشلة")، قم بصياغة اعتذار مهني بالنيابة عن المنشأة يتناسب تماماً مع مجال العمل المستنتج من السياق، وامتص غضب العميل بذكاء، واعتذر له بصدق شديد، ووضح له أنك تقوم حالياً بتحويل المحادثة فوراً إلى المشرف المسؤول لحل مشكلته فوراً. (النبرة والأسلوب: دافئ، خاضع، معتذر للغاية، ومهني).
ب. هجوم شخصي / ألفاظ نابية (عملاء غير محترمين): إذا كان العميل يهاجمك أنت كشخص أو يسب بألفاظ بذيئة أو صريحة (مثل: "أنت حمار")، حافظ على هدوئك وتماسكك الكاملين، وصغ رداً حازماً ولكن مهذباً للغاية بالعامية المصرية يطلب منه الحفاظ على أسلوب حوار ملائم ومحترم لنتمكن من مساعدتك، ويوضح له أنك تقوم بنقل المحادثة حالياً لموظف بشري مختص فوراً. تأكد من إيجاد صياغات متنوعة في كل مرة يتم فيها هذا السيناريو. (النبرة والأسلوب: حازم، موضوعي، مهذب جداً، وصارم).
ج. إحباط بسبب سوء فهم الذكاء الاصطناعي (عملاء منزعجون): إذا وصفك العميل بالغباء أو غضب لأن الرد الآلي لم يفهم قصده بالشكل الصحيح (مثل: "أنت مش فاهم"، "غباء ده")، أظهر التواضع الكامل، واعتذر له بلطف عن هذا القصور التقني كمساعد تلقائي، وأقِر بأنك فشلت في فهم قصده بدقة، وأكد له أن زميلاً بشرياً مختصاً يتدخل الآن لمساعدته فوراً. (النبرة والأسلوب: متواضع، مساعد، وانتقال سلس).
د. طلب عادي للتحدث مع موظف بشري (بدون غضب أو إساءة): صغ رداً ديناميكياً ودوداً ولطيفاً بالعامية المصرية يؤكد للعميل أنه سيتم تحويله فوراً لممثل خدمة العملاء لمساعدته.
* تنبيه هام جداً لـ HITL: يجب أن ينتهي الرد المخصص في final_reply_text دائماً بالعبارة التالية حرفياً وبدون تعديل:
"تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"

4. التعامل مع الاستفسارات الخارجية / الخروج عن النطاق (Marketing Recovery):
إذا أرسل المستخدم استفساراً خارج نطاق العمل التجاري والدعم تماماً (مثل الغزل "بحبك"، طلبات الزواج "عايز اتجوز"، أو استفسارات برمجية/عامة "اكتب كود C++"):
- لا تقم بتوجيه العميل لـ HITL أو التحويل البشري.
- رد بأسلوب فكاهي خفيف الظل بالعامية المصرية ودود للغاية مستخدماً الإيموجي المعتمدة فقط، ثم قم بتحويل المحادثة بذكاء لربط الدعابة بحافز الشراء أو طلب خدمة من المتجر/النشاط (مثال: "أنا ممكن أشوفلك أحلى عروسة بس اطلب من عندنا منتج شيك تتقدم بيه الأول 🤠🤝" أو "عينيا ليك هكتبلك أحلى كود بس اعمل أوردر الأول عشان الفانكشن تشتغل وتتكومبايل صح 👀").

5. تتبع حالة الطلبات والتذاكر ديناميكياً:
عندما يسأل العميل عن حالة طلبه ("فين الأوردر"، "حاجتي فين"، "وصل لفين"):
- اقرأ البيانات المحقونة في قسم "PERSONAL USER STATE".
- قم بصياغة تحديث يبدو طبيعياً وبشرياً يوضح حالة الطلب الحالية والملحوظات المكتوبة من التاجر (merchant_note) إن وجدت.
- مثال: "أنا شايف إن الأوردر بتاعك حالته لسه CANCELLED، ومكتوب هنا في الملاحظات إن المندوب راح والعنوان مكانش موجود.. تحب أحولك لخدمة العملاء يتابع معاك؟". واعرض دائماً خيار التحويل لموظف بشري في النهاية.

6. قواعد التنسيق والأسلوب (تُطبق في REPLY و HITL الرد المخصص):
- التكيف الديناميكي مع مجال العمل: قم بتحليل مستندات قاعدة المعرفة المقدمة أولاً واستنتج مجال عمل النشاط التجاري (مثل: عيادة طبية، متجر إلكتروني، خدمات قانونية، إلخ). يجب أن تتكيف كل ردودك ومصطلحاتك وأسئلتك ديناميكياً لتناسب هذا المجال المستنتج.
- قائمة الإيموجي المعتمدة: يُسمح لك فقط باستخدام الإيموجيز التالية لخدمة العملاء بشكل طبيعي وغير مكثف: (😊، 🤔، 🧐، 🫡، 🙋🏻‍♂️، 🤌🏻، 👌🏻، 🤝، 🫶🏻، 👀، 🤠، 😎، 🤩، 🥰، 👍، ✨، 🌸، 🛒). يمنع تماماً استخدام أي إيموجيز خارج هذه القائمة المعتمدة.
- ممنوع استخدام التنسيقات (Markdown) تماماً: يُحظر استخدام النجمة أو النجمتين (*) أو (**) أو علامات العناوين (###) أو النقاط النقطية أو الأرقام لعمل قوائم. يجب أن يكون الرد نصاً عربياً طبيعياً عادياً مقسماً بسطور جديدة عادية وإيموجي معتمدة فقط.
- تجسيد الشخصية: أنت الموظف المسؤول والوحيد للرد على العميل حالياً. لا تقل أبداً للعميل "يرجى التواصل مع خدمة العملاء" أو "تواصل مع الإدارة/الفريق"، بل تحدث بصيغتك كصاحب الشأن لحل مشكلته مباشرة.
- الإيجاز والنبرة التفاعلية: لا تقم أبداً بنسخ ولصق قوائم طويلة من أسعار الشحن أو المحافظات أو الخدمات أو الأقسام بشكل عشوائي ما لم يسألك العميل عن تفاصيل معقدة. اجعل ردودك قصيرة جداً وتفاعلية تشجع العميل على إكمال المحادثة.
- خطاف التفاعل العام للاستفسارات العامة والمبهمة: إذا أرسل العميل استفساراً قصيراً أو غامضاً يطلب فيه الأسعار، الكتالوج، المواعيد، أو معلومات أساسية عامة (مثل: "بكام", "تفاصيل", "المواعيد", "الأسعار")، تحقق من قاعدة المعرفة أولاً، ثم رحب به بحرارة، وأكد استلام طلبه بناءً على نوع النشاط التجاري (مثال: للعيادة رحب به واطلب التخصص المطلوب؛ للمتجر رحب به واطلب اسم المنتج وموقع التوصيل؛ للخدمات اطلب نوع الاستشارة)، وذلك لتقديم إجابة دقيقة.

لا تخترع معلومات غير موجودة في المستندات. لا تضف مقدمات أو خاتمة خارج JSON."""
    }

    // ── Public API ─────────────────────────────────────────────────────────

    override suspend fun generateReply(
        notification: ProcessedNotification,
        candidates: List<RetrievalCandidate>,
        isOngoingConversation: Boolean,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>,
        maxTokens: Int
    ): String? {
        val baseUserMessage = buildContextualUserMessage(
            notificationText = notification.originalText,
            candidates = candidates,
            chatHistory = chatHistory,
            activeOrders = activeOrders,
            personalChunks = personalChunks
        )
        val userMessage = if (isOngoingConversation) {
            baseUserMessage + "\n\n[System Rule: This is an ongoing conversation context window. Do NOT include any greetings or welcoming words like 'أهلاً بك', 'مرحباً', or 'منورنا'. Provide the answer directly and conversationally.]"
        } else {
            baseUserMessage
        }
        val messages = listOf(
            ChatMessage(role = "system", content = SYSTEM_PROMPT_FIRST_PASS),
            ChatMessage(role = "user", content = userMessage)
        )
        return executeFailoverChain(messages, maxTokens)
    }

    override suspend fun generateFinalReply(
        queryText: String,
        chunks: List<RetrievalCandidate>,
        isOngoingConversation: Boolean,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>,
        maxTokens: Int
    ): String? {
        val baseUserMessage = buildContextualUserMessage(
            notificationText = queryText,
            candidates = chunks,
            chatHistory = chatHistory,
            activeOrders = activeOrders,
            personalChunks = personalChunks
        )
        val userMessage = if (isOngoingConversation) {
            baseUserMessage + "\n\n[System Rule: This is an ongoing conversation context window. Do NOT include any greetings or welcoming words like 'أهلاً بك', 'مرحباً', or 'منورنا'. Provide the answer directly and conversationally.]"
        } else {
            baseUserMessage
        }
        val messages = listOf(
            ChatMessage(role = "system", content = SYSTEM_PROMPT_FINAL_PASS),
            ChatMessage(role = "user", content = userMessage)
        )
        return executeFailoverChain(messages, maxTokens)
    }

    // ── Private Implementation ────────────────────────────────────────────

    /**
     * Estimates tokens using standard word count * 1.3 heuristic.
     */
    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return (words * 1.3).toInt()
    }

    /**
     * Builds the priority-ranked multi-source context block within a 3000 token limit.
     */
    private fun buildContextualUserMessage(
        notificationText: String,
        candidates: List<RetrievalCandidate>,
        chatHistory: List<ChatHistoryEntity>,
        activeOrders: List<OrderEntity>,
        personalChunks: List<ChatHistoryEntity>
    ): String {
        var remainingBudget = 3000

        // 1. Current Inbound Message (Priority 1) - NEVER trimmed
        val inboundBlock = "\n\nاستفسار العميل الحالي:\n$notificationText"
        remainingBudget -= estimateTokens(inboundBlock)

        // 2. Personal Client Order State (Priority 2)
        val orderStatusStr = activeOrders.firstOrNull()?.status ?: "No Active Order"
        val merchantNoteStr = activeOrders.firstOrNull()?.merchantNote ?: "None"
        val orderStateHeader = "Order Status: $orderStatusStr\nMerchant Tracker Note: $merchantNoteStr"
        remainingBudget -= estimateTokens(orderStateHeader)

        // 3. Chronological Chat History (Priority 3) - Up to last 6 turns, trimmed from oldest
        val turns = chatHistory.take(6).reversed().map { turn ->
            val sender = if (turn.isFromUser) "العميل" else "الرد التلقائي"
            "[$sender]: ${turn.messageText}"
        }
        val keptTurns = mutableListOf<String>()
        for (i in turns.indices.reversed()) {
            val turnText = turns[i]
            val turnTokens = estimateTokens(turnText) + 1
            if (remainingBudget >= turnTokens) {
                keptTurns.add(0, turnText)
                remainingBudget -= turnTokens
            } else {
                break
            }
        }

        // 4. Personal History Chunks (Priority 4) - Top 2 personal chunks, trimmed chunk-by-chunk
        val keptPChunks = mutableListOf<String>()
        val pChunks = personalChunks.take(2)
        for (chunk in pChunks) {
            val chunkText = "- ${chunk.messageText}"
            val chunkTokens = estimateTokens(chunkText) + 1
            if (remainingBudget >= chunkTokens) {
                keptPChunks.add(chunkText)
                remainingBudget -= chunkTokens
            } else {
                break
            }
        }

        // 5. Global Business Knowledge Chunks (Priority 5) - trimmed dynamically
        val keptGlobalChunks = mutableListOf<String>()
        for (candidate in candidates) {
            val header = candidate.headerContext
            val chunkText = if (!header.isNullOrBlank()) "$header\n${candidate.chunkText}" else candidate.chunkText
            val chunkTokens = estimateTokens(chunkText) + 2
            if (remainingBudget >= chunkTokens) {
                keptGlobalChunks.add(chunkText)
                remainingBudget -= chunkTokens
            } else {
                break
            }
        }

        return buildString {
            append("=== GLOBAL KNOWLEDGE ===\n")
            if (keptGlobalChunks.isEmpty()) {
                append("None\n")
            } else {
                append(keptGlobalChunks.joinToString("\n---\n"))
                append("\n")
            }

            append("\n=== CHAT HISTORY ===\n")
            if (keptTurns.isEmpty()) {
                append("None\n")
            } else {
                append(keptTurns.joinToString("\n"))
                append("\n")
            }

            append("\n=== PERSONAL USER STATE ===\n")
            append("Order Status: $orderStatusStr\n")
            append("Merchant Tracker Note: $merchantNoteStr\n")
            append("Relevant Customer Records:\n")
            if (keptPChunks.isEmpty()) {
                append("None")
            } else {
                append(keptPChunks.joinToString("\n"))
            }
            append("\n\n")

            append("استفسار العميل الحالي:\n")
            append(notificationText)
        }
    }

    /**
     * Iterates through [FAILOVER_MODELS] sequentially. Each attempt is wrapped in
     * [withTimeout] at [MODEL_TIMEOUT_MS]. On timeout, 429, or 5xx, logs warning
     * and proceeds to the next model. Returns fallback JSON only if all models exhausted.
     */
    private suspend fun executeFailoverChain(messages: List<ChatMessage>, maxTokens: Int): String? {
        val targetMaxTokens = minOf(maxTokens, ABSOLUTE_FAILOVER_CEILING)

        for ((index, model) in FAILOVER_MODELS.withIndex()) {
            Log.d(TAG, "Attempting model ${index + 1}/${FAILOVER_MODELS.size}: $model with max_tokens=$targetMaxTokens")
            PersonaLogger.d(TAG, "Trying model: $model")

            val result = callOpenRouter(model, messages, targetMaxTokens)
            if (result != null) {
                Log.i(TAG, "Success on model $model")
                PersonaLogger.d(TAG, "LLM response received from $model")
                return result
            }
            // result == null means this model failed — loop continues to next
        }

        // All models exhausted
        Log.w(TAG, "All ${FAILOVER_MODELS.size} models failed. Returning HITL fallback JSON.")
        PersonaLogger.d(TAG, "All models failed — HITL fallback")
        return """{"action": "HITL", "refined_search_keyword": null, "final_reply_text": "تمام يا فندم، ثواني وهيكون مع حضرتك ممثل خدمة العملاء فوراً لمساعدتك في أقرب وقت متاح! 🙏"}"""
    }

    /**
     * Executes a single HTTP POST to the OpenRouter API for the given [model].
     *
     * @return The LLM-generated content string, or null on any failure
     *         (timeout, rate limit, server error, network error).
     */
    private suspend fun callOpenRouter(
        model: String,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(MODEL_TIMEOUT_MS) {
                val response = httpClient.post(BuildConfig.OPENROUTER_BASE_URL) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(BuildConfig.OPENROUTER_API_KEY)
                    setBody(
                        OpenRouterRequest(
                            model = model,
                            messages = messages,
                            maxTokens = maxTokens
                        )
                    )
                }

                when {
                    response.status.value == 429 -> {
                        Log.w(TAG, "Rate limited (429) on model $model")
                        null
                    }
                    response.status.value in 500..599 -> {
                        Log.w(TAG, "Server error (${response.status.value}) on model $model")
                        null
                    }
                    response.status.isSuccess() -> {
                        val body = response.body<OpenRouterResponse>()
                        val content = body.choices?.firstOrNull()?.message?.content
                        if (content.isNullOrBlank()) {
                            Log.w(TAG, "Empty content in successful response from $model")
                            null
                        } else {
                            content
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unexpected HTTP ${response.status.value} from $model")
                        null
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Timeout (${MODEL_TIMEOUT_MS}ms) on model $model")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Network error on model $model", e)
            null
        }
    }
}
