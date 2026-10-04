package dev.cue.rebuild

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private class NavigationExclusions {
    var container: LayoutCoordinates? = null
    val regions = mutableMapOf<Any, LayoutCoordinates>()

    fun contains(position: Offset): Boolean {
        val root = container?.takeIf { it.isAttached } ?: return true
        val point = root.localToRoot(position)
        return regions.values.any { it.isAttached && it.boundsInRoot().contains(point) }
    }
}

private val LocalNavigationExclusions = staticCompositionLocalOf<NavigationExclusions?> { null }

/** Reserve a component's actual bounds without consuming its taps, long presses or drags. */
@Composable
fun Modifier.excludeNavigationGestures(): Modifier {
    val exclusions = LocalNavigationExclusions.current ?: return this
    val key = remember { Any() }
    DisposableEffect(exclusions, key) { onDispose { exclusions.regions.remove(key) } }
    return onGloballyPositioned { exclusions.regions[key] = it }
}

/** Full-screen horizontal navigation; child scrollers and explicitly reserved regions win. */
@Composable
fun SwipeNavigation(
    gestureKey: Any,
    onBack: (() -> Unit)?,
    onForward: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val back by rememberUpdatedState(onBack)
    val forward by rememberUpdatedState(onForward)
    val exclusions = remember { NavigationExclusions() }
    var progress by remember(gestureKey, enabled) { mutableFloatStateOf(0f) }
    var goingForward by remember { mutableStateOf(false) }
    val hintAlpha by
        animateFloatAsState(
            targetValue = progress,
            animationSpec = if (progress == 0f) tween(Cue.FeedbackMs) else snap(),
            label = "navigation hint",
        )
    CompositionLocalProvider(LocalNavigationExclusions provides exclusions) {
        Box(
            modifier
                .onGloballyPositioned { exclusions.container = it }
                .pointerInput(gestureKey, enabled, onBack != null, onForward != null) {
                    if (!enabled) return@pointerInput
                    val threshold = (size.width * .22f).coerceIn(64.dp.toPx(), 96.dp.toPx())
                    awaitEachGesture {
                        val down =
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                        if (exclusions.contains(down.position)) return@awaitEachGesture
                        var direction = 0f
                        try {
                            while (true) {
                                // Children get first refusal: paging, scrolling and selection keep
                                // ownership.
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.changes.count { it.pressed } > 1) break
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // Clickables consume the initial down; only movement/up consumption
                                // cancels us.
                                if (change.uptimeMillis == down.uptimeMillis) continue
                                if (change.isConsumed) break
                                val delta = change.position - down.position
                                if (direction == 0f) {
                                    if (
                                        abs(delta.y) > viewConfiguration.touchSlop &&
                                            abs(delta.y) >= abs(delta.x)
                                    )
                                        break
                                    if (abs(delta.x) > viewConfiguration.touchSlop) {
                                        if (abs(delta.x) < abs(delta.y) * 1.5f) break
                                        direction = if (delta.x > 0) 1f else -1f
                                        if (
                                            direction > 0 && back == null ||
                                                direction < 0 && forward == null
                                        )
                                            break
                                        goingForward = direction < 0
                                    }
                                }
                                if (direction != 0f) {
                                    change.consume()
                                    progress = (delta.x * direction / threshold).coerceIn(0f, 1f)
                                }
                                if (!change.pressed) {
                                    if (direction != 0f && delta.x * direction >= threshold) {
                                        if (direction > 0) back?.invoke() else forward?.invoke()
                                    }
                                    break
                                }
                            }
                        } finally {
                            progress = 0f
                        }
                    }
                }
        ) {
            content()
            if (hintAlpha > 0f) {
                Box(
                    Modifier.align(if (goingForward) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 8.dp)
                        .size(40.dp)
                        .alpha(hintAlpha)
                        .shadow(6.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (goingForward) Glyph.Next else Glyph.Back)
                }
            }
        }
    }
}
