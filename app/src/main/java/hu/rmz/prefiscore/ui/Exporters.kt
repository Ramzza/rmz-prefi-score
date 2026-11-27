package hu.rmz.prefiscore.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import hu.rmz.prefiscore.data.Sharing
import hu.rmz.prefiscore.model.Format
import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.HtmlReport

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

fun gameFileBase(g: Game) = "prefi_${Format.fileStamp(g.createdAt)}_${Format.safeFileName(g.players.joinToString("_") { it.name })}"

/** JSON mentése letöltésként (rendszer fájlválasztó). */
@Composable
fun rememberJsonSaver(): (fileName: String, content: String) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pending
        pending = null
        if (uri != null && content != null) {
            toast(ctx, if (Sharing.writeToUri(ctx, uri, content)) "Fájl elmentve" else "Nem sikerült menteni")
        }
    }
    return { name, content ->
        pending = content
        launcher.launch(name)
    }
}

fun shareJson(ctx: Context, fileName: String, content: String, whatsapp: Boolean) =
    Sharing.shareFile(ctx, fileName, Sharing.MIME_JSON, content, whatsapp, "Prefi játék")

fun shareHtml(ctx: Context, g: Game) =
    Sharing.shareFile(ctx, "${gameFileBase(g)}.html", Sharing.MIME_HTML, HtmlReport.render(g, System.currentTimeMillis()), false, "Prefi eredmény")
