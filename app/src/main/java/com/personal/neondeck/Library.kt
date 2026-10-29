package com.personal.neondeck

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long,
    val uri: Uri,
    val folder: String
)

/** En Android 9 o menor la carpeta viene completa (/storage/emulated/0/Music/...); la dejamos como "Music/...". */
@Suppress("DEPRECATION")
private fun toRelativePath(fullPath: String): String {
    val root = Environment.getExternalStorageDirectory().absolutePath
    return if (fullPath.startsWith(root)) fullPath.removePrefix(root).trim('/')
    else fullPath.replace(Regex("^/storage/[^/]+/"), "").trim('/')
}

/** ¿La canción está en la carpeta [f] o en una de sus subcarpetas? Con "" cuenta toda la música. */
fun Track.inFolder(f: String): Boolean =
    f.isEmpty() || folder.equals(f, ignoreCase = true) || folder.startsWith("$f/", ignoreCase = true)

/** Convierte la carpeta que elige el usuario con el selector de Android en una ruta tipo "Music/Rock". */
fun treeUriToPath(uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        when {
            docId.contains(':') -> docId.substringAfter(':').trim('/')
            docId.equals("downloads", ignoreCase = true) -> "Download"
            else -> ""
        }
    } catch (_: Exception) {
        null
    }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Lee la música del teléfono (la que está en el almacenamiento, incluida la carpeta Música). */
fun loadTracks(ctx: Context): List<Track> {
    val collection: Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    // Carpeta de cada canción: RELATIVE_PATH en Android 10+, y la ruta completa en versiones anteriores
    @Suppress("DEPRECATION")
    val folderCol =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH
        else MediaStore.Audio.Media.DATA

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID,
        folderCol
    )

    // Excluye tonos, alarmas y notificaciones, y audios muy cortos
    val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
        "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
        "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
        "${MediaStore.Audio.Media.DURATION} >= 40000"

    val out = ArrayList<Track>()
    try {
        ctx.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iFolder = c.getColumnIndexOrThrow(folderCol)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val rawFolder = c.getString(iFolder).orEmpty()
                val folder = (
                    if (Build.VERSION.SDK_INT >= 29) rawFolder.trim('/')
                    else toRelativePath(rawFolder.substringBeforeLast('/', ""))
                    ).ifEmpty { "/" }
                val artist = c.getString(iArtist)?.takeIf { it != "<unknown>" } ?: "Artista desconocido"
                out.add(
                    Track(
                        id = id,
                        title = c.getString(iTitle) ?: "Sin título",
                        artist = artist,
                        album = c.getString(iAlbum) ?: "",
                        durationMs = c.getLong(iDur),
                        albumId = c.getLong(iAlbumId),
                        uri = ContentUris.withAppendedId(collection, id),
                        folder = folder
                    )
                )
            }
        }
    } catch (_: Exception) {
        // Sin permiso o sin acceso: devolvemos lo que haya
    }
    return out
}

fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

/** Carátula del álbum. Devuelve null si la canción no tiene. */
suspend fun loadArt(ctx: Context, track: Track?): Bitmap? = withContext(Dispatchers.IO) {
    if (track == null) return@withContext null
    try {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(track.uri, Size(720, 720), null)
        } else {
            ctx.contentResolver.openInputStream(albumArtUri(track.albumId))?.use {
                BitmapFactory.decodeStream(it)
            }
        }
    } catch (_: Exception) {
        null
    }
}
