package hu.rmz.prefiscore.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object Sharing {
    const val MIME_JSON = "application/json"
    const val MIME_HTML = "text/html"

    private fun cacheFile(context: Context, fileName: String, content: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L) it.delete() }
        val file = File(dir, fileName)
        file.writeText(content)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Fájl megosztása; [whatsapp] esetén közvetlenül a WhatsAppot nyitja meg (ha telepítve van). */
    fun shareFile(context: Context, fileName: String, mime: String, content: String, whatsapp: Boolean, title: String) {
        val uri = cacheFile(context, fileName, content)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (whatsapp) {
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                try {
                    context.startActivity(Intent(base).setPackage(pkg))
                    return
                } catch (_: ActivityNotFoundException) {
                }
            }
            Toast.makeText(context, "A WhatsApp nem található, válassz másik alkalmazást.", Toast.LENGTH_SHORT).show()
        }
        context.startActivity(Intent.createChooser(base, title))
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(content.toByteArray()) }
    }.isSuccess

    fun readUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
    }.getOrNull()
}
