package com.personal.neondeck

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    /** Ruta de la subcarpeta dentro de la carpeta elegida ("/" = la carpeta elegida en sí). */
    val folder: String
)

// ───────────────────────── Acceso limitado a UNA carpeta ─────────────────────────

/**
 * Selector de carpeta de Android pidiendo SOLO lectura.
 * La app no pide ningún permiso de almacenamiento: lo único que puede ver es la carpeta que elijas aquí.
 */
class PickMusicFolder : ActivityResultContract<Unit?, Uri?>() {
    override fun createIntent(context: Context, input: Unit?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** ¿Sigue vigente el permiso de lectura sobre esa carpeta? (Android lo quita si la carpeta se borra o se revoca.) */
fun hasFolderAccess(ctx: Context, uri: Uri): Boolean =
    try {
        ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    } catch (_: Exception) {
        false
    }

/**
 * Guarda el permiso de lectura de la carpeta elegida y SUELTA cualquier permiso de una carpeta anterior,
 * para que la app nunca tenga acceso a más de una carpeta a la vez.
 */
fun saveFolderAccess(ctx: Context, uri: Uri): Boolean =
    try {
        val resolver = ctx.contentResolver
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.persistedUriPermissions.forEach { p ->
            if (p.uri != uri) {
                try { resolver.releasePersistableUriPermission(p.uri, both) } catch (_: Exception) {}
            }
        }
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: Exception) {
        false
    }

/** Nombre corto de la carpeta elegida, para mostrarlo en pantalla. */
fun folderLabel(uri: Uri?): String {
    if (uri == null) return ""
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val name = docId.substringAfter(':', docId).trim('/').substringAfterLast('/')
        sanitizeText(name, "RAÍZ", 40)
    } catch (_: Exception) {
        "CARPETA"
    }
}

// ───────────────────────── Texto seguro (metadatos = texto plano) ─────────────────────────

private const val MAX_TEXT = 120

private fun isUnsafeCodePoint(cp: Int): Boolean =
    when (Character.getType(cp)) {
        // Controles (\u0000, \u001B…), formato invisible (bidi, ancho cero, BOM), uso privado, sin asignar
        Character.CONTROL.toInt(),
        Character.FORMAT.toInt(),
        Character.PRIVATE_USE.toInt(),
        Character.SURROGATE.toInt(),
        Character.UNASSIGNED.toInt() -> true
        else -> false
    }

/**
 * Limpia un texto que viene de un archivo (título, artista, álbum, nombre de archivo o carpeta).
 *  - Se usa SIEMPRE como texto plano: jamás se interpreta como HTML, ruta, consulta ni comando.
 *  - Quita caracteres de control y caracteres invisibles que cambian el sentido del texto (RLO/LRO, ancho cero…).
 *  - Los saltos de línea y tabulaciones pasan a ser un espacio, y los espacios repetidos se juntan.
 *  - Limita el largo para que un título gigante no rompa la pantalla ni la notificación.
 */
fun sanitizeText(raw: String?, fallback: String, max: Int = MAX_TEXT): String {
    if (raw.isNullOrEmpty()) return fallback
    val sb = StringBuilder(minOf(raw.length, max))
    var lastWasSpace = true // evita espacios al inicio
    var i = 0
    while (i < raw.length && sb.length < max) {
        val cp = raw.codePointAt(i)
        i += Character.charCount(cp)
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
            continue
        }
        if (isUnsafeCodePoint(cp)) continue
        sb.appendCodePoint(cp)
        lastWasSpace = false
    }
    val out = sb.toString().trim()
    return if (out.isEmpty()) fallback else out
}

// ───────────────────────── Lectura de la carpeta y de los metadatos ─────────────────────────

private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
private const val MAX_FILES = 20000   // tope de seguridad: no recorre carpetas infinitas
private const val MAX_DEPTH = 12      // tope de subcarpetas
private const val MIN_DURATION_MS = 40000L // ignora sonidos muy cortos (tonos, avisos)

private class Scan(val ctx: Context, val treeUri: Uri, val retriever: MediaMetadataRetriever) {
    val out = ArrayList<Track>()
    var visited = 0
}

private fun stableId(s: String): Long {
    // FNV-1a de 64 bits: mismo archivo = mismo id entre sesiones
    var h = -3750763034362895579L
    for (ch in s) {
        h = h xor ch.code.toLong()
        h *= 1099511628211L
    }
    return h and Long.MAX_VALUE
}

private fun isAudioFile(name: String, mime: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return mime.startsWith("audio/") || ext in AUDIO_EXT
}

/** Lee SOLO la carpeta elegida (y sus subcarpetas) con el permiso que dio el usuario. */
fun loadTracks(ctx: Context, treeUri: Uri): List<Track> {
    val rootId = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: Exception) {
        return emptyList()
    }
    val retriever = MediaMetadataRetriever()
    val scan = Scan(ctx, treeUri, retriever)
    try {
        walk(scan, rootId, "/", 0)
    } catch (_: Exception) {
        // Si algo falla a medias devolvemos lo que ya se leyó
    } finally {
        try { retriever.release() } catch (_: Exception) {}
    }
    return scan.out
        .distinctBy { it.id }
        .sortedBy { it.title.lowercase() }
}

private fun walk(scan: Scan, parentDocId: String, relPath: String, depth: Int) {
    if (depth > MAX_DEPTH || scan.visited >= MAX_FILES) return

    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(scan.treeUri, parentDocId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    val dirs = ArrayList<Pair<String, String>>()   // docId, nombre
    val files = ArrayList<Triple<String, String, String>>() // docId, nombre, mime

    try {
        scan.ctx.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1).orEmpty()
                val mime = c.getString(2).orEmpty()
                if (name.startsWith(".")) continue // carpetas y archivos ocultos
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) dirs.add(docId to name)
                else files.add(Triple(docId, name, mime))
            }
        }
    } catch (_: Exception) {
        return
    }

    for ((docId, name, mime) in files) {
        if (scan.visited >= MAX_FILES) return
        if (!isAudioFile(name, mime)) continue
        scan.visited++
        readTrack(scan, docId, name, relPath)?.let { scan.out.add(it) }
    }
    for ((docId, name) in dirs) {
        if (scan.visited >= MAX_FILES) return
        val child = sanitizeText(name, "?", 60)
        val childPath = if (relPath == "/") child else "$relPath/$child"
        walk(scan, docId, childPath, depth + 1)
    }
}

private fun readTrack(scan: Scan, docId: String, fileName: String, relPath: String): Track? {
    val docUri = DocumentsContract.buildDocumentUriUsingTree(scan.treeUri, docId)

    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var duration = 0L
    try {
        val r = scan.retriever
        r.setDataSource(scan.ctx, docUri)
        title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        // Sin metadatos legibles: usamos el nombre del archivo
    }

    if (duration in 1L until MIN_DURATION_MS) return null

    val baseName = fileName.substringBeforeLast('.', fileName)
    return Track(
        id = stableId(docUri.toString()),
        // Todo lo que viene del archivo pasa por sanitizeText: se trata como texto plano
        title = sanitizeText(title, sanitizeText(baseName, "Sin título")),
        artist = sanitizeText(artist, "Artista desconocido"),
        album = sanitizeText(album, ""),
        durationMs = duration,
        uri = docUri,
        folder = relPath
    )
}

/** ¿La canción está en la subcarpeta [f] o dentro de ella? Con "" cuenta toda la carpeta elegida. */
fun Track.inFolder(f: String): Boolean =
    f.isEmpty() || folder.equals(f, ignoreCase = true) || folder.startsWith("$f/", ignoreCase = true)

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Carátula incrustada en el archivo (si la tiene). Se reduce para no gastar memoria. */
suspend fun loadArt(ctx: Context, track: Track?): Bitmap? = withContext(Dispatchers.IO) {
    if (track == null) return@withContext null
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(ctx, track.uri)
        val bytes = r.embeddedPicture ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxSide <= 0) return@withContext null
        var sample = 1
        while (maxSide / sample > 1024) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    } finally {
        try { r.release() } catch (_: Exception) {}
    }
}
