package dev.cue.rebuild

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.*
import com.mikepenz.markdown.model.State as MarkdownDocument
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/** One rendering style for both speakers and the composer preview. */
@Composable
fun MarkdownText(content: String, color: Color = Cue.Ink, modifier: Modifier = Modifier) {
    // Keep the last parsed document visible while a streaming update is parsed off the UI thread.
    val parsed by
        produceState<MarkdownDocument?>(null, content) {
            val flavour = GFMFlavourDescriptor()
            value =
                MarkdownState(
                        Input(
                            content,
                            true,
                            flavour,
                            MarkdownParser(flavour),
                            ReferenceLinkHandlerImpl(),
                        )
                    )
                    .parse()
        }
    val text = Cue.ChatText.copy(color = color)
    val mono = text.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
    val link = text.copy(textDecoration = TextDecoration.Underline)
    val uriHandler = LocalUriHandler.current
    val safeLinks =
        remember(uriHandler) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    if (
                        uri.substringBefore(':').lowercase() in
                            setOf("http", "https", "mailto", "tel")
                    )
                        runCatching { uriHandler.openUri(uri) }
                }
            }
        }
    val state = parsed
    if (state == null || state is MarkdownDocument.Error) {
        Text(content, modifier, style = text)
        return
    }
    CompositionLocalProvider(LocalUriHandler provides safeLinks) {
        Markdown(
            state = state,
            modifier = modifier,
            colors =
                markdownColor(
                    text = color,
                    codeBackground = color.copy(alpha = .07f),
                    inlineCodeBackground = color.copy(alpha = .12f),
                    dividerColor = color.copy(alpha = .18f),
                    tableBackground = color.copy(alpha = .04f),
                ),
            typography =
                markdownTypography(
                    h1 =
                        text.copy(
                            fontSize = 23.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    h2 =
                        text.copy(
                            fontSize = 21.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    h3 =
                        text.copy(
                            fontSize = 19.sp,
                            lineHeight = 25.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    h4 = text.copy(fontWeight = FontWeight.SemiBold),
                    h5 = text.copy(fontWeight = FontWeight.SemiBold),
                    h6 = text.copy(fontWeight = FontWeight.SemiBold),
                    text = text,
                    paragraph = text,
                    ordered = text,
                    bullet = text,
                    list = text,
                    quote = text.copy(color = color.copy(alpha = .85f)),
                    code = mono,
                    inlineCode = mono,
                    link = link,
                    table = text,
                ),
            padding =
                markdownPadding(
                    block = 4.dp,
                    listItemTop = 2.dp,
                    listItemBottom = 2.dp,
                    listIndent = 12.dp,
                ),
            dimens = markdownDimens(tableCellWidth = 132.dp, tableCellPadding = 10.dp),
            animations = markdownAnimations(animateTextSize = { this }),
            imageTransformer = Coil2ImageTransformerImpl,
            components =
                markdownComponents(
                    codeFence = {
                        MarkdownCodeFence(
                            it.content,
                            it.node,
                            block = { code, language, style ->
                                CodeSnippet(code, language, style, color)
                            },
                        )
                    },
                    codeBlock = {
                        MarkdownCodeBlock(
                            it.content,
                            it.node,
                            block = { code, language, style ->
                                CodeSnippet(code, language, style, color)
                            },
                        )
                    },
                ),
        )
    }
}

@Composable
private fun CodeSnippet(code: String, language: String?, style: TextStyle, color: Color) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().background(color.copy(alpha = .07f), RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                language.orEmpty(),
                Modifier.weight(1f),
                color = color.copy(alpha = .7f),
                fontSize = 12.sp,
            )
            TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Text("Copy code", color = color, fontSize = 12.sp)
            }
        }
        Text(
            code,
            Modifier.horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            style = style,
        )
    }
}
