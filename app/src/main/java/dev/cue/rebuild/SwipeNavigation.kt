package dev.cue.rebuild

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

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
internal val LocalNavigationActive = staticCompositionLocalOf { true }

/** Reserve a component's bounds without consuming its taps, long presses or drags. */
@Composable
fun Modifier.excludeNavigationGestures(): Modifier {
    val exclusions = LocalNavigationExclusions.current ?: return this
    val key = remember { Any() }
    DisposableEffect(exclusions, key) { onDispose { exclusions.regions.remove(key) } }
    return onGloballyPositioned { exclusions.regions[key] = it }
}

/** Adjacent pages follow the finger; the route changes only after the swipe settles. */
@Composable
fun SwipeNavigation(
    route: Route,
    left: Route?,
    right: Route?,
    direction: Int,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (Route) -> Unit,
) {
    val navigate by rememberUpdatedState(onNavigate)
    val latestRoute by rememberUpdatedState(route)
    val latestContent by rememberUpdatedState(content)
    val exclusions = remember { NavigationExclusions() }
    val savedPages = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    var displayed by remember { mutableStateOf(route) }
    var neighbor by remember { mutableStateOf<Route?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var side by remember { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(0) }
    var settling by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }

    // Button and system-back navigation use the same geometry as dragging.
    LaunchedEffect(route, width) {
        if (route == displayed || width == 0) return@LaunchedEffect
        motion?.cancelAndJoin()
        settling = true
        neighbor = route
        side = direction
        offset = 0f
        try {
            animate(
                0f,
                direction * width.toFloat(),
                animationSpec = tween(Cue.NavigationMs, easing = Cue.Ease),
            ) { value, _ ->
                offset = value
            }
            displayed = route
        } finally {
            neighbor = null
            offset = 0f
            settling = false
        }
    }
    DisposableEffect(Unit) { onDispose { motion?.cancel() } }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { width = it.width }
            .onGloballyPositioned { exclusions.container = it }
            .pointerInput(route, displayed, left, right, enabled) {
                if (!enabled) return@pointerInput
                val minFlingDistance = 32.dp.toPx()
                val minFlingVelocity = 650.dp.toPx()
                awaitEachGesture {
                    val down =
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (settling || exclusions.contains(down.position)) return@awaitEachGesture
                    val velocity =
                        VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var dragSide = 0
                    var destination: Route? = null
                    var released = false
                    var commit = false
                    try {
                        while (true) {
                            // Child scrollers get first refusal. Bubble and composer bounds are
                            // excluded above.
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.count { it.pressed } > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.uptimeMillis == down.uptimeMillis) continue
                            if (change.isConsumed) break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val delta = change.position - down.position
                            if (dragSide == 0) {
                                if (
                                    abs(delta.y) > viewConfiguration.touchSlop &&
                                        abs(delta.y) >= abs(delta.x)
                                )
                                    break
                                if (abs(delta.x) > viewConfiguration.touchSlop) {
                                    if (abs(delta.x) < abs(delta.y) * 1.5f) break
                                    dragSide = if (delta.x > 0) 1 else -1
                                    destination = if (dragSide > 0) left else right
                                    if (destination == null) break
                                    side = dragSide
                                    neighbor = destination
                                }
                            }
                            if (destination != null) {
                                change.consume()
                                offset =
                                    (delta.x * dragSide).coerceIn(0f, size.width.toFloat()) *
                                        dragSide
                            }
                            if (!change.pressed) {
                                released = true
                                val speed = velocity.calculateVelocity().x * dragSide
                                val distance = offset * dragSide
                                // A deliberate reverse flick cancels even after crossing the
                                // distance threshold.
                                commit =
                                    destination != null &&
                                        speed > -minFlingVelocity &&
                                        (distance >= size.width * .08f ||
                                            distance > minFlingDistance && speed > minFlingVelocity)
                                break
                            }
                        }
                    } finally {
                        if (destination != null && latestRoute == displayed) {
                            val target = destination
                            val finish = released && commit
                            settling = true
                            motion =
                                scope.launch {
                                    try {
                                        animate(
                                            offset,
                                            if (finish) dragSide * width.toFloat() else 0f,
                                            animationSpec =
                                                tween(Cue.NavigationMs, easing = Cue.Ease),
                                        ) { value, _ ->
                                            offset = value
                                        }
                                        if (finish) {
                                            displayed = target
                                            navigate(target)
                                        }
                                    } finally {
                                        offset = 0f
                                        neighbor = null
                                        settling = false
                                    }
                                }
                        }
                    }
                }
            }
    ) {
        for (page in listOfNotNull(displayed, neighbor).distinct()) {
            key(page) {
                // Keep saveable keys stable as this page moves between preview and foreground.
                val pageContent =
                    remember(page) {
                        movableContentOf {
                            savedPages.SaveableStateProvider(
                                "${page.screen}:${page.agentId}:${page.integrationId}"
                            ) {
                                latestContent(page)
                            }
                        }
                    }
                val active = page == displayed
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            translationX = offset + if (active) 0f else -side * width.toFloat()
                        }
                        .testTag("page-${page.screen}")
                        .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
                ) {
                    CompositionLocalProvider(
                        LocalNavigationExclusions provides if (active) exclusions else null,
                        LocalNavigationActive provides (active && !settling),
                    ) {
                        pageContent()
                    }
                }
            }
        }
    }
}
