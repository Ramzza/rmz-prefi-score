package hu.rmz.prefiscore.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageLoader {
    /** A feltöltött kép hosszabb oldala legfeljebb ennyi pixel (olvasható marad, de nem túl nagy). */
    private const val MAX_EDGE = 2400

    /** Kép betöltése, elforgatás (EXIF) kezelése, kicsinyítés és JPEG-be tömörítés. */
    fun loadJpeg(context: Context, uri: Uri): ByteArray {
        val bitmap = decode(context, uri)
        val scale = MAX_EDGE.toFloat() / max(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            out.toByteArray()
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val edge = max(info.size.width, info.size.height)
                if (edge > MAX_EDGE * 2) {
                    val s = (MAX_EDGE * 2).toFloat() / edge
                    decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("A kép nem olvasható.")
    }
}
