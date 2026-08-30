package com.mediaflow.app.ui.player.gestures

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ArtworkSwipePager(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .testTag("artwork_swipe_pager")
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(onNext, onPrevious) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val action = ArtworkSwipeMath.commit(
                            offsetPx = offsetX.value,
                            widthPx = widthPx,
                            velocityPx = 0f,
                        )
                        scope.launch {
                            when (action) {
                                ArtworkSwipeCommit.Next -> {
                                    offsetX.animateTo(-widthPx, spring())
                                    onNext()
                                    offsetX.snapTo(0f)
                                }
                                ArtworkSwipeCommit.Previous -> {
                                    offsetX.animateTo(widthPx, spring())
                                    onPrevious()
                                    offsetX.snapTo(0f)
                                }
                                ArtworkSwipeCommit.None -> {
                                    offsetX.animateTo(0f, spring())
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    },
                )
            }
            .offset { IntOffset(offsetX.value.roundToInt(), 0) },
    ) {
        content()
    }
}
