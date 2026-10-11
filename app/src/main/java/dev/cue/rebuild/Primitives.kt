package dev.cue.rebuild

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.caverock.androidsvg.SVG

// Reference: Cue 26.17.5 at 520 dpi. Dimensions are observed; motion is an estimate.
object Cue {
    val Blue = Color(0xFF1B68D9)
    val Ink: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    val Muted: Color
        @Composable
        get() =
            if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFB5B5B2)
            else Color(0xFF636361)

    val Faint: Color
        @Composable
        get() =
            if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFA7A7A3)
            else Color(0xFF6E6E6C)

    val Page: Color
        @Composable
        get() =
            if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF1B1B1B)
            else Color(0xFFF5F5F5)

    val Settings: Color
        @Composable
        get() =
            if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF161616)
            else Color(0xFFF0F0F0)

    val Bubble: Color
        @Composable
        get() =
            if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF292929)
            else Color(0xFFF2F2F2)

    val ComposerSurface: Color
        @Composable
        get() =
            if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF292929)
            else Color(0xFFFAFAF9)

    val Brown = Color(0xFFB17A4D)
    val ControlSize = 44.dp
    val HeaderHeight = 48.dp
    val PageInset = 16.dp
    val GroupRadius = 26.dp
    val IconSize = 22.dp
    val Title = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = .3.sp)
    val Label = TextStyle(fontSize = 16.sp, lineHeight = 20.sp)
    val ChatText =
        TextStyle(
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        )
    val Caption = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    const val FeedbackMs = 180
    const val TypingPulseMs = 450
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val NavigationMs = 320
}

// Compose honors the platform duration scale. This flag also removes repeating decoration.
private val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
private fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    enabled = ValueAnimator.areAnimatorsEnabled()
                }
            }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
fun CueTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            if (dark)
                darkColorScheme(
                    primary = Cue.Blue,
                    background = Color(0xFF161616),
                    surface = Color(0xFF242424),
                )
            else
                lightColorScheme(
                    primary = Cue.Blue,
                    background = Color.White,
                    surface = Color.White,
                    onSurface = Color(0xFF242423),
                ),
        typography =
            Typography(
                bodyLarge = Cue.Body,
                bodyMedium = Cue.Caption,
                labelLarge = Cue.Label,
                titleMedium = Cue.Title,
                titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalMotionEnabled provides rememberMotionEnabled(),
            content = content,
        )
    }
}

@Composable
fun Asset(
    name: String,
    modifier: Modifier = Modifier,
    recolor: Color? = null,
    folder: String = "cue",
) {
    val context = LocalContext.current
    val picture =
        remember(name, recolor, folder) {
            var xml =
                context.assets.open("$folder/$name.svg").bufferedReader().use { it.readText() }
            if (recolor != null)
                xml =
                    xml.replace(
                        Regex("fill=\"#[0-9A-Fa-f]{6}\""),
                        "fill=\"#%06X\"".format(recolor.toArgb() and 0xFFFFFF),
                    )
            SVG.getFromString(xml).renderToPicture()
        }
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.scale(size.width / picture.width, size.height / picture.height)
            canvas.nativeCanvas.drawPicture(picture)
            canvas.nativeCanvas.restore()
        }
    }
}

@Composable
fun Avatar(avatar: AvatarSpec, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        Asset("agent_avatar_shape_${avatar.shape}", Modifier.fillMaxSize(), Color(avatar.color))
        Asset("agent_avatar_face_${avatar.face}", Modifier.fillMaxSize())
    }
}

@Composable
fun RoundButton(
    glyph: Glyph,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.surface,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && LocalMotionEnabled.current) 0.91f else 1f,
            spring(stiffness = 700f),
            label = "press",
        )
    Box(
        modifier
            .size(Cue.ControlSize)
            .scale(scale)
            .shadow(
                10.dp,
                CircleShape,
                ambientColor = Color.Black.copy(alpha = .07f),
                spotColor = Color.Black.copy(alpha = .07f),
            )
            .background(background, CircleShape)
            .border(.5.dp, Cue.Muted.copy(alpha = .14f), CircleShape)
            .clip(CircleShape)
            .clickable(interaction, null, enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, tint = if (enabled) tint else Cue.Faint, size = 24.dp)
    }
}

@Composable
fun Header(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    buttonBackground: Color = MaterialTheme.colorScheme.surface,
    backGlyph: Glyph = Glyph.Back,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Cue.HeaderHeight)
            .padding(horizontal = Cue.PageInset),
        contentAlignment = Alignment.Center,
    ) {
        RoundButton(
            backGlyph,
            "Back",
            Modifier.align(Alignment.CenterStart),
            background = buttonBackground,
            onClick = onBack,
        )
        Column(
            Modifier.padding(horizontal = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                fontSize = if (subtitle == null) 18.sp else 15.sp,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Cue.Muted)
        }
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    fontSize: TextUnit = 16.sp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (!enabled) Cue.Bubble else if (primary) Cue.Ink else Cue.Bubble)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color =
                if (!enabled) Cue.Muted
                else if (primary) MaterialTheme.colorScheme.surface else Cue.Ink,
        )
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Cue.GroupRadius))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
fun SettingRow(
    title: String,
    glyph: Glyph? = null,
    value: String? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 49.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null || glyph != null) {
                if (leading != null) leading() else Icon(glyph!!, size = 21.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(title, Modifier.weight(1f), fontSize = 16.sp, letterSpacing = .2.sp)
            if (value != null) Text(value, color = Cue.Muted, fontSize = 16.sp)
            if (trailing != null) trailing()
            else if (onClick != null) {
                Spacer(Modifier.width(10.dp))
                Icon(Glyph.Next, tint = Cue.Muted, size = 18.dp)
            }
        }
        if (divider)
            HorizontalDivider(
                Modifier.padding(
                    start = if (leading != null) 52.dp else if (glyph != null) 48.dp else 16.dp,
                    end = 16.dp,
                ),
                color = Cue.Muted.copy(alpha = .09f),
                thickness = .5.dp,
            )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Cue.Muted.copy(alpha = .10f), CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyph.Search, tint = Cue.Muted)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value,
            onValueChange,
            Modifier.weight(1f).semantics { contentDescription = placeholder },
            singleLine = true,
            textStyle = TextStyle(color = LocalContentColor.current, fontSize = 16.sp),
            cursorBrush = SolidColor(Cue.Blue),
            decorationBox = {
                if (value.isEmpty()) Text(placeholder, color = Cue.Faint, fontSize = 16.sp)
                it()
            },
        )
        if (value.isNotEmpty())
            Box(
                Modifier.size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onValueChange("") }
                    .semantics { contentDescription = "Clear search" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Glyph.Close, tint = Cue.Muted, size = 16.dp)
            }
    }
}

@Composable
fun EmptyState(glyph: Glyph, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(glyph, tint = Cue.Faint, size = 44.dp)
        Text(text, color = Cue.Muted, fontSize = 16.sp)
    }
}

@Composable
fun TypingIndicator() {
    val transition =
        if (LocalMotionEnabled.current) rememberInfiniteTransition(label = "typing") else null
    Row(
        Modifier.background(Cue.Bubble, CircleShape).padding(18.dp).semantics {
            contentDescription = "Agent is responding"
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { i ->
            val alpha =
                transition
                    ?.animateFloat(
                        .3f,
                        .9f,
                        infiniteRepeatable(tween(Cue.TypingPulseMs, i * 140), RepeatMode.Reverse),
                        label = "dot$i",
                    )
                    ?.value ?: .65f
            Box(Modifier.size(6.dp).background(Cue.Muted.copy(alpha = alpha), CircleShape))
        }
    }
}
