package com.example.persona.features.settings.presentation.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex

// ── LazyList Reorderable Implementation ──────────────────────────────────────

@Composable
fun rememberReorderableLazyListState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (from: Int, to: Int) -> Unit
): ReorderableLazyListState {
    val onMoveUpdated = rememberUpdatedState(onMove)
    return remember(lazyListState) {
        ReorderableLazyListState(lazyListState) { onMoveUpdated.value }
    }
}

class ReorderableLazyListState(
    val lazyListState: LazyListState,
    private val onMoveProvider: () -> ((from: Int, to: Int) -> Unit)
) {
    var draggedKey by mutableStateOf<Any?>(null)
        internal set

    var draggedIndex by mutableStateOf<Int?>(null)
        internal set

    var dragOffsetY by mutableFloatStateOf(0f)
        internal set

    fun isItemDragging(key: Any?): Boolean = draggedKey != null && draggedKey == key

    fun onDragStart(key: Any, index: Int, haptics: HapticFeedback) {
        draggedKey = key
        draggedIndex = index
        dragOffsetY = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(dragAmountY: Float, haptics: HapticFeedback) {
        val currentIndex = draggedIndex ?: return
        dragOffsetY += dragAmountY

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.firstOrNull { it.index == currentIndex } ?: return
        val currentCenter = currentItem.offset + (currentItem.size / 2) + dragOffsetY

        // Viewport edge auto-scroll
        val viewportStart = lazyListState.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = lazyListState.layoutInfo.viewportEndOffset.toFloat()
        val edgeThreshold = 120f
        if (currentCenter < viewportStart + edgeThreshold) {
            val ratio = ((viewportStart + edgeThreshold - currentCenter) / edgeThreshold).coerceIn(0f, 1f)
            lazyListState.dispatchRawDelta(-ratio * 20f)
        } else if (currentCenter > viewportEnd - edgeThreshold) {
            val ratio = ((currentCenter - (viewportEnd - edgeThreshold)) / edgeThreshold).coerceIn(0f, 1f)
            lazyListState.dispatchRawDelta(ratio * 20f)
        }

        // Midpoint swap check
        val targetItem = visibleItems.firstOrNull { other ->
            if (other.index == currentIndex) return@firstOrNull false
            val otherCenter = other.offset + (other.size / 2)
            if (dragOffsetY > 0) {
                other.index > currentIndex && currentCenter >= otherCenter
            } else {
                other.index < currentIndex && currentCenter <= otherCenter
            }
        }

        if (targetItem != null) {
            val targetIndex = targetItem.index
            onMoveProvider()(currentIndex, targetIndex)
            dragOffsetY += (currentItem.offset - targetItem.offset)
            draggedIndex = targetIndex
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDragEnd() {
        draggedKey = null
        draggedIndex = null
        dragOffsetY = 0f
    }

    fun onDragCancel() {
        draggedKey = null
        draggedIndex = null
        dragOffsetY = 0f
    }
}

fun Modifier.reorderableItem(
    state: ReorderableLazyListState,
    key: Any,
    index: Int,
    haptics: HapticFeedback
): Modifier = this
    .graphicsLayer {
        val dragging = state.isItemDragging(key)
        translationY = if (dragging) state.dragOffsetY else 0f
        scaleX = if (dragging) 1.02f else 1f
        scaleY = if (dragging) 1.02f else 1f
        shadowElevation = if (dragging) 16f else 0f
    }
    .zIndex(if (state.isItemDragging(key)) 10f else 0f)
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key, index, haptics) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y, haptics)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragCancel() }
        )
    }

fun Modifier.reorderableHandle(
    state: ReorderableLazyListState,
    key: Any,
    index: Int,
    haptics: HapticFeedback
): Modifier = this
    .pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.onDragStart(key, index, haptics) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y, haptics)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragCancel() }
        )
    }

// ── Column Reorderable Implementation ────────────────────────────────────────

@Composable
fun rememberReorderableColumnState(
    onMove: (from: Int, to: Int) -> Unit
): ReorderableColumnState {
    val onMoveUpdated = rememberUpdatedState(onMove)
    return remember {
        ReorderableColumnState { onMoveUpdated.value }
    }
}

class ReorderableColumnState(
    private val onMoveProvider: () -> ((from: Int, to: Int) -> Unit)
) {
    var draggedKey by mutableStateOf<Any?>(null)
        internal set

    var draggedIndex by mutableStateOf<Int?>(null)
        internal set

    var dragOffsetY by mutableFloatStateOf(0f)
        internal set

    var itemHeightPx by mutableFloatStateOf(180f)

    fun isItemDragging(key: Any?): Boolean = draggedKey != null && draggedKey == key

    fun onDragStart(key: Any, index: Int, haptics: HapticFeedback) {
        draggedKey = key
        draggedIndex = index
        dragOffsetY = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(dragAmountY: Float, totalItems: Int, haptics: HapticFeedback) {
        val currentIndex = draggedIndex ?: return
        dragOffsetY += dragAmountY

        val threshold = (if (itemHeightPx > 0f) itemHeightPx else 180f) * 0.5f

        if (dragOffsetY > threshold && currentIndex < totalItems - 1) {
            val targetIndex = currentIndex + 1
            onMoveProvider()(currentIndex, targetIndex)
            dragOffsetY -= itemHeightPx
            draggedIndex = targetIndex
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else if (dragOffsetY < -threshold && currentIndex > 0) {
            val targetIndex = currentIndex - 1
            onMoveProvider()(currentIndex, targetIndex)
            dragOffsetY += itemHeightPx
            draggedIndex = targetIndex
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun onDragEnd() {
        draggedKey = null
        draggedIndex = null
        dragOffsetY = 0f
    }

    fun onDragCancel() {
        draggedKey = null
        draggedIndex = null
        dragOffsetY = 0f
    }
}

fun Modifier.reorderableColumnItem(
    state: ReorderableColumnState,
    key: Any,
    index: Int,
    totalItems: Int,
    haptics: HapticFeedback
): Modifier = this
    .onGloballyPositioned { coordinates ->
        if (coordinates.size.height > 0) {
            state.itemHeightPx = coordinates.size.height.toFloat()
        }
    }
    .graphicsLayer {
        val dragging = state.isItemDragging(key)
        translationY = if (dragging) state.dragOffsetY else 0f
        scaleX = if (dragging) 1.02f else 1f
        scaleY = if (dragging) 1.02f else 1f
        shadowElevation = if (dragging) 16f else 0f
    }
    .zIndex(if (state.isItemDragging(key)) 10f else 0f)
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key, index, haptics) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y, totalItems, haptics)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragCancel() }
        )
    }

fun Modifier.reorderableColumnHandle(
    state: ReorderableColumnState,
    key: Any,
    index: Int,
    totalItems: Int,
    haptics: HapticFeedback
): Modifier = this
    .pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.onDragStart(key, index, haptics) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y, totalItems, haptics)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragCancel() }
        )
    }
