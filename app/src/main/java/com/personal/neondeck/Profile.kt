package com.personal.neondeck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

private const val AVATAR_FILE = "avatar.jpg"

/**
 * Guarda la foto elegida como avatar: la endereza según la cámara, la recorta cuadrada
 * y la reduce a 512x512. Queda guardada dentro de la app (no depende de la galería).
 */
fun saveAvatar(ctx: Context, uri: Uri): Boolean {
    return try {
        val cr = ctx.contentResolver

        // 1) Medir la imagen para no cargarla gigante en memoria
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide <= 0) return false
        var sample = 1
        while (maxSide / sample > 1500) sample *= 2

        // 2) Cargarla
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false

        // 3) Enderezarla si la foto viene girada
        val degrees = try {
            cr.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
        val upright = if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        } else {
            decoded
        }

        // 4) Recorte cuadrado centrado y tamaño final
        val side = minOf(upright.width, upright.height)
        val square = Bitmap.createBitmap(
            upright,
            (upright.width - side) / 2,
            (upright.height - side) / 2,
            side,
            side
        )
        val finalBmp = Bitmap.createScaledBitmap(square, 512, 512, true)

        File(ctx.filesDir, AVATAR_FILE).outputStream().use {
            finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        true
    } catch (_: Exception) {
        false
    }
}

fun loadAvatar(ctx: Context): Bitmap? {
    val f = File(ctx.filesDir, AVATAR_FILE)
    return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
}

fun deleteAvatar(ctx: Context) {
    File(ctx.filesDir, AVATAR_FILE).delete()
}
