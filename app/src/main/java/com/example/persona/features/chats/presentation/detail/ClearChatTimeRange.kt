package com.example.persona.features.chats.presentation.detail

import androidx.annotation.StringRes
import com.example.persona.R

/**
 * Time range options for clearing local chat messages.
 */
enum class ClearChatTimeRange(@StringRes val labelResId: Int) {
    LAST_HOUR(R.string.clear_range_last_hour),
    LAST_TWO_HOURS(R.string.clear_range_last_two_hours),
    TODAY(R.string.clear_range_today),
    ALL_TIME(R.string.clear_range_all_time)
}
