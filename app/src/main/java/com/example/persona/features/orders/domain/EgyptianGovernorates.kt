package com.example.persona.features.orders.domain

/**
 * Standard list of Egyptian Governorates for B2B fulfillment routing & filtering.
 */
@Deprecated("Governorate dropdown deprecated in favor of full international deliveryAddress")
object EgyptianGovernorates {
    val ALL = listOf(
        "القاهرة",
        "الجيزة",
        "الإسكندرية",
        "القليوبية",
        "الشرقية",
        "الدقهلية",
        "البحيرة",
        "الغربية",
        "المنوفية",
        "دمياط",
        "بورسعيد",
        "الإسماعيلية",
        "السويس",
        "كفر الشيخ",
        "الفيوم",
        "بني سويف",
        "المنيا",
        "أسيوط",
        "سوهاج",
        "قنا",
        "الأقصر",
        "أسوان",
        "البحر الأحمر",
        "الوادي الجديد",
        "مطروح",
        "شمال سيناء",
        "جنوب سيناء"
    )
}
