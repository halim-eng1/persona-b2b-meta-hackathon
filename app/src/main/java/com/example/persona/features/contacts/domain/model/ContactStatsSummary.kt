package com.example.persona.features.contacts.domain.model

data class ContactStatsSummary(
    val totalContacts: Int = 0,
    val vipCount: Int = 0,
    val regularCount: Int = 0,
    val debtorCount: Int = 0,
    val problematicCount: Int = 0,
    val blockedCount: Int = 0,
    val totalRevenue: Double = 0.0
)
