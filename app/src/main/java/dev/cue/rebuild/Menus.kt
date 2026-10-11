package dev.cue.rebuild

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

// Home + and Add Skills + in Cue share this rounded, softly outlined menu surface.
@Composable
fun ActionMenu(
    items: List<Pair<Glyph?, String>>,
    dismiss: () -> Unit,
    width: Dp = 238.dp,
    alignment: Alignment = Alignment.TopEnd,
    offset: DpOffset = DpOffset(0.dp, 48.dp),
    onSelect: (String) -> Unit,
) {
    val shape = RoundedCornerShape(36.dp)
    val shadowInset = 24.dp
    // Reserve space inside the popup for the blur; keep the card anchored to its trigger.
    val popupOffset =
        with(LocalDensity.current) {
            val horizontal = if (alignment == Alignment.BottomStart) -shadowInset else shadowInset
            val vertical = if (alignment == Alignment.BottomStart) shadowInset else -shadowInset
            IntOffset((offset.x + horizontal).roundToPx(), (offset.y + vertical).roundToPx())
        }
    Popup(
        alignment = alignment,
        offset = popupOffset,
        onDismissRequest = dismiss,
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        Box(
            Modifier.pointerInput(dismiss) { detectTapGestures { dismiss() } }.padding(shadowInset)
        ) {
            Column(
                Modifier.width(width)
                    .dropShadow(
                        shape,
                        Shadow(
                            radius = 24.dp,
                            color = Color.Black.copy(alpha = .08f),
                            offset = DpOffset(0.dp, 6.dp),
                        ),
                    )
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(1.dp, Cue.Ink.copy(alpha = .08f), shape)
                    .clip(shape)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp)
            ) {
                items.forEach { (icon, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) {
                                dismiss()
                                onSelect(label)
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            Icon(icon, size = 20.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(label, style = Cue.Label)
                    }
                }
            }
        }
    }
}
