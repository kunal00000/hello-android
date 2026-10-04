package dev.cue.rebuild

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Shared sheet surface and dismissal, with content retaining its own layout and state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CueSheet(
    onDismissRequest: () -> Unit,
    height: Dp? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val keyboardHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val availableHeight = (windowHeight * .90f - keyboardHeight).coerceAtLeast(200.dp)
    val sheetHeight = height?.coerceAtMost(availableHeight) ?: availableHeight
    val dismiss: () -> Unit = {
        keyboard?.hide()
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) onDismissRequest()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        scrimColor = Color.Black.copy(alpha = .40f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Box(
            Modifier.testTag("cue-sheet").fillMaxWidth().height(sheetHeight).navigationBarsPadding()
        ) {
            content(dismiss)
        }
    }
}
