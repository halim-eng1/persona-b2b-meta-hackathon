package com.example.persona.features.settings.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import com.example.persona.features.settings.presentation.model.SettingKey

@Stable
class SettingFocusState(initial: SettingKey?) {
    var target by mutableStateOf(initial)
        private set

    fun consume(key: SettingKey) {
        if (target == key) target = null
    }
}

val LocalSettingFocus = staticCompositionLocalOf { SettingFocusState(null) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.settingFocus(key: SettingKey): Modifier {
    val focus = LocalSettingFocus.current
    val requester = remember { BringIntoViewRequester() }
    val pulse = remember { Animatable(0f) }
    val tint = MaterialTheme.colorScheme.primary

    LaunchedEffect(focus.target) {
        if (focus.target == key) {
            requester.bringIntoView()
            repeat(2) {
                pulse.animateTo(1f, tween(280))
                pulse.animateTo(0f, tween(420))
            }
            focus.consume(key)
        }
    }

    return this
        .bringIntoViewRequester(requester)
        .drawBehind {
            if (pulse.value > 0f) {
                drawRoundRect(
                    color = tint.copy(alpha = 0.2f * pulse.value),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
        }
}
