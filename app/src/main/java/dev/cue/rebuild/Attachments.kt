package dev.cue.rebuild

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Private copies keep selected documents readable after process recreation.
data class Attachment(val name: String, val mime: String, val path: String)

private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

fun importAttachment(context: Context, uri: Uri): Attachment {
    val name =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst())
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            else null
        } ?: "Attachment"
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val directory = File(context.filesDir, "attachments").apply { mkdirs() }
    val file = File(directory, UUID.randomUUID().toString())
    try {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_ATTACHMENT_BYTES) { "Choose a file smaller than 5 MB." }
                    output.write(buffer, 0, count)
                }
            }
        }
    } catch (e: Exception) {
        file.delete()
        throw e
    }
    return Attachment(name, mime, file.absolutePath)
}

@Composable
fun rememberAttachmentActions(
    context: Context,
    onAttachment: (Attachment) -> Unit,
    onError: (String) -> Unit,
): (String) -> Unit {
    val scope = rememberCoroutineScope()
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { importAttachment(context, uri) } }
                        .onSuccess(onAttachment)
                        .onFailure { onError(it.message ?: "Could not read the file.") }
                }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null)
                scope.launch {
                    val attachment =
                        withContext(Dispatchers.IO) {
                            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
                            val file = File(dir, UUID.randomUUID().toString())
                            file.outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
                            }
                            Attachment("Photo.jpg", "image/jpeg", file.absolutePath)
                        }
                    onAttachment(attachment)
                }
        }
    return { action ->
        runCatching {
                when (action) {
                    "Camera" -> camera.launch(null)
                    "Photos" -> pick.launch("image/*")
                    "Files" -> pick.launch("*/*")
                }
            }
            .onFailure { onError("$action is not available on this device.") }
        Unit
    }
}

fun openAttachment(context: Context, file: Attachment) {
    val uri =
        androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".files",
            File(file.path),
        )
    val intent =
        android.content
            .Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, file.mime)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, file.name)) }
}

// Bound the read before allocating a string; large document imports must not block the UI.
fun importSkill(context: Context, uri: Uri): String {
    val reader =
        context.contentResolver.openInputStream(uri)?.bufferedReader()
            ?: error("Could not open that skill file.")
    return reader.use {
        val chars = CharArray(20001)
        var length = 0
        while (length < chars.size) {
            val count = it.read(chars, length, chars.size - length)
            if (count < 0) break
            length += count
        }
        require(length <= 20000) { "Choose a skill with no more than 20,000 characters." }
        String(chars, 0, length).trim().also { content ->
            require(content.isNotEmpty()) { "The skill file is empty." }
        }
    }
}
