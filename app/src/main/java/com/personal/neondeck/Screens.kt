package com.personal.neondeck

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

// ───────────────────────── Raíz de la app ─────────────────────────

@Composable
fun NeonApp() {
    // Barra de estado y de navegación del mismo color que el fondo del tema
    val view = LocalView.current
    val barColor = Cy.Bg.toArgb()
    SideEffect {
        val window = (view.context as? Activity)?.window
        window?.statusBarColor = barColor
        window?.navigationBarColor = barColor
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cy.Primary,
            background = Cy.Bg,
            surface = Cy.Panel,
            onSurface = Cy.Text
        )
    ) {
        NeonRoot()
    }
}

@Composable
private fun NeonRoot() {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext

    val pc = remember { PlayerController(appCtx) }
    DisposableEffect(pc) {
        pc.connect()
        onDispose { pc.release() }
    }

    // Ajustes que se recuerdan: carpeta de música elegida, subcarpeta activa, alias y tema de color
    val uiPrefs = remember { appCtx.getSharedPreferences("neon_ui", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // ACCESO LIMITADO: la app no pide permiso de almacenamiento. Solo puede leer la carpeta que el usuario elige.
    // Si todavía no hay carpeta (o Android ya retiró el permiso), se muestra la pantalla que obliga a elegirla.
    var treeUri by remember {
        mutableStateOf(
            uiPrefs.getString("tree_uri", null)
                ?.let { Uri.parse(it) }
                ?.takeIf { hasFolderAccess(appCtx, it) }
        )
    }
    var subfolder by remember { mutableStateOf(uiPrefs.getString("subfolder", "") ?: "") }
    var alias by remember { mutableStateOf(uiPrefs.getString("alias", "") ?: "") }

    // Biblioteca
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(treeUri, reloadKey) {
        val t = treeUri
        if (t != null) {
            loaded = false
            tracks = withContext(Dispatchers.IO) { loadTracks(appCtx, t) }
            loaded = true
        }
    }

    val chooseSubfolder: (String) -> Unit = { path ->
        subfolder = path
        uiPrefs.edit().putString("subfolder", path).apply()
    }
    val folderPicker = rememberLauncherForActivityResult(PickMusicFolder()) { uri ->
        if (uri != null && saveFolderAccess(appCtx, uri)) {
            // Al cambiar de carpeta se detiene lo que sonaba: el permiso de la carpeta anterior ya se soltó
            pc.stopAndClear()
            tracks = emptyList()
            loaded = false
            treeUri = uri
            subfolder = ""
            uiPrefs.edit().putString("tree_uri", uri.toString()).putString("subfolder", "").apply()
            reloadKey++
        }
    }

    // Notificaciones (Android 13+): se piden una vez que ya hay carpeta elegida
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(treeUri) {
        if (treeUri != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Avatar del perfil (se guarda dentro de la app)
    var avatar by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) { avatar = withContext(Dispatchers.IO) { loadAvatar(appCtx) } }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveAvatar(appCtx, uri) }
                if (ok) avatar = withContext(Dispatchers.IO) { loadAvatar(appCtx) }
            }
        }
    }

    val byId = remember(tracks) { tracks.associateBy { it.id.toString() } }
    val current = pc.mediaId?.let { byId[it] }
    val art by produceState<Bitmap?>(null, current?.id) {
        value = loadArt(appCtx, current)
    }

    // Recuerda la última canción y su posición, para encontrarla igual al volver a abrir la app
    val savePosition = {
        val id = pc.mediaId
        if (id != null) {
            uiPrefs.edit().putString("last_pos_id", id).putLong("last_pos", pc.position).apply()
        }
    }
    LaunchedEffect(pc.mediaId) {
        pc.mediaId?.let { uiPrefs.edit().putString("last_id", it).apply() }
    }

    // Al abrir la app (si no hay nada sonando) deja cargada la última canción, en pausa
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, pc.connected, tracks) {
        if (!restored && loaded && pc.connected && tracks.isNotEmpty()) {
            restored = true
            if (pc.mediaId == null) {
                val lastId = uiPrefs.getString("last_id", null)
                var list = tracks.filter { it.inFolder(subfolder) }
                var index = list.indexOfFirst { it.id.toString() == lastId }
                if (index < 0) {
                    list = tracks
                    index = list.indexOfFirst { it.id.toString() == lastId }
                }
                if (lastId != null && index >= 0) {
                    val pos = if (uiPrefs.getString("last_pos_id", null) == lastId) uiPrefs.getLong("last_pos", 0L) else 0L
                    pc.restoreQueue(list, index, pos)
                }
            }
        }
    }

    // Avance de la barra de progreso mientras suena (y guarda la posición cada pocos segundos)
    LaunchedEffect(pc.playing) {
        var ticks = 0
        while (pc.playing) {
            pc.refreshPosition()
            if (++ticks % 20 == 0) savePosition()
            delay(250)
        }
        savePosition() // al pausar
    }

    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().cyberBackground()) {
        if (treeUri == null) {
            FolderGate(onPick = { folderPicker.launch(null) })
        } else {
            Header(avatar = avatar, alias = alias, onProfile = { tab = 3 })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> LibraryScreen(
                        tracks = tracks,
                        loaded = loaded,
                        currentId = pc.mediaId,
                        rootName = folderLabel(treeUri),
                        folder = subfolder,
                        onFolder = chooseSubfolder,
                        onPickFolder = { folderPicker.launch(null) },
                        onReload = { reloadKey++ },
                        onPlay = { list, index, shuffleOn ->
                            pc.playQueue(list, index, shuffleOn)
                            if (shuffleOn == true) tab = 1
                        }
                    )
                    1 -> PlayerScreen(pc = pc, art = art)
                    2 -> AudioScreen()
                    else -> ProfileScreen(
                        avatar = avatar,
                        alias = alias,
                        onAlias = {
                            alias = it
                            uiPrefs.edit().putString("alias", it).apply()
                        },
                        onPickAvatar = {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveAvatar = {
                            deleteAvatar(appCtx)
                            avatar = null
                        },
                        trackCount = tracks.size,
                        folderCount = tracks.map { it.folder }.distinct().size,
                        onTheme = {
                            Cy.themeIndex = it
                            uiPrefs.edit().putInt("theme", it).apply()
                        }
                    )
                }
            }
            if (tab != 1 && pc.mediaId != null) {
                MiniPlayer(pc = pc, onOpen = { tab = 1 })
            }
            NavBar(selected = tab, onSelect = { tab = it })
        }
    }
}

// ───────────────────────── Pantalla obligatoria: elegir carpeta de música ─────────────────────────

@Composable
private fun FolderGate(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlitchText("NEON//DECK", tx(26.sp, Cy.Primary, Orbitron, FontWeight.Bold, 2.sp))
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .cutPanel()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ELIGE TU CARPETA DE MÚSICA", style = tx(18.sp, Cy.Alert, Orbitron, FontWeight.Bold, 2.sp))
            Text(
                "Para empezar tienes que elegir la carpeta donde está tu música. " +
                    "NeonDeck solo podrá leer esa carpeta (y sus subcarpetas): no tiene acceso al resto de tu teléfono.",
                style = tx(16.sp, Cy.Text, Rajdhani, FontWeight.Medium)
            )
            Text(
                "Luego pulsa USAR ESTA CARPETA en el selector de Android.",
                style = tx(14.sp, Cy.Dim, Rajdhani, FontWeight.Medium)
            )
            CyberTextButton("ELEGIR CARPETA", onPick, filled = true)
        }
    }
}

// ───────────────────────── Cabecera y navegación ─────────────────────────

@Composable
private fun Header(avatar: Bitmap?, alias: String, onProfile: () -> Unit) {
    val blink = rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "blinkAlpha"
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            GlitchText("NEON//DECK", tx(24.sp, Cy.Primary, Orbitron, FontWeight.Bold, 2.sp))
            Text(
                if (alias.isBlank()) "AUDIO.SYS  ·  ONLINE" else "USER  ·  ${alias.trim().uppercase()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(12.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 3.sp)
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .background(Cy.Alert.copy(alpha = blink.value))
        )
        Spacer(Modifier.width(12.dp))
        AvatarBadge(avatar, alias, 42.dp, Modifier.clickableFx(onProfile))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Brush.horizontalGradient(listOf(Cy.Primary, Cy.Alert, Color.Transparent)))
    )
}

/** Foto de perfil en marco angular. Si no hay foto, muestra la inicial del alias. */
@Composable
private fun AvatarBadge(avatar: Bitmap?, alias: String, size: Dp, modifier: Modifier = Modifier) {
    val shape = CutCornerShape(topStart = size / 4, bottomEnd = size / 4)
    val big = size > 60.dp
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Cy.Panel2)
            .border(if (big) 2.dp else 1.5.dp, Cy.Primary, shape),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                alias.trim().take(1).uppercase().ifEmpty { "?" },
                style = tx(if (big) 52.sp else 18.sp, Cy.Primary, Orbitron, FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cy.Panel)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NavItem("LISTA", Ico.LIB, selected == 0, Modifier.weight(1f)) { onSelect(0) }
        NavItem("DECK", Ico.DECK, selected == 1, Modifier.weight(1f)) { onSelect(1) }
        NavItem("AUDIO", Ico.EQ, selected == 2, Modifier.weight(1f)) { onSelect(2) }
        NavItem("PERFIL", Ico.USER, selected == 3, Modifier.weight(1f)) { onSelect(3) }
    }
}

@Composable
private fun NavItem(label: String, ico: Ico, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    val fg = if (active) Cy.Bg else Cy.Dim
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (active) Cy.Primary else Color.Transparent)
            .border(1.dp, if (active) Cy.Primary else Cy.Dim.copy(alpha = 0.4f), shape)
            .clickableFx(onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CyberIcon(ico, fg, Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, maxLines = 1, style = tx(12.sp, fg, Rajdhani, FontWeight.Bold, 0.8.sp))
    }
}

@Composable
private fun MiniPlayer(pc: PlayerController, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .cutPanel(border = Cy.Primary.copy(alpha = 0.6f), fill = Cy.Panel2, cut = 12.dp)
            .clickableFx(onOpen)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                pc.title.ifBlank { "—" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(16.sp, Cy.Primary, Rajdhani, FontWeight.Bold)
            )
            Text(
                pc.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(13.sp, Cy.Secondary, Rajdhani, FontWeight.Medium)
            )
        }
        CyberIconButton(Ico.PREV, { pc.previous() }, size = 40.dp, color = Cy.Secondary, iconSize = 18.dp)
        Spacer(Modifier.width(6.dp))
        CyberIconButton(
            if (pc.playing) Ico.PAUSE else Ico.PLAY,
            { pc.togglePlay() },
            size = 44.dp,
            filled = true,
            iconSize = 20.dp
        )
        Spacer(Modifier.width(6.dp))
        CyberIconButton(Ico.NEXT, { pc.next() }, size = 40.dp, color = Cy.Secondary, iconSize = 18.dp)
    }
}

// ───────────────────────── Biblioteca ─────────────────────────

private fun folderName(path: String): String =
    if (path == "/") "RAÍZ" else path.trim('/').substringAfterLast('/').ifEmpty { path }

@Composable
private fun LibraryScreen(
    tracks: List<Track>,
    loaded: Boolean,
    currentId: String?,
    rootName: String,
    folder: String,
    onFolder: (String) -> Unit,
    onPickFolder: () -> Unit,
    onReload: () -> Unit,
    onPlay: (List<Track>, Int, Boolean?) -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Carpetas que tienen música, con cuántas canciones hay en cada una
    val folders = remember(tracks) {
        tracks.groupingBy { it.folder }.eachCount().toList().sortedBy { it.first.lowercase() }
    }
    val inFolder = remember(tracks, folder) {
        if (folder.isEmpty()) tracks else tracks.filter { it.inFolder(folder) }
    }
    val filtered = remember(inFolder, query) {
        if (query.isBlank()) inFolder
        else inFolder.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }
    val searchShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = tx(16.sp, Cy.Text, Rajdhani, FontWeight.Medium),
                cursorBrush = SolidColor(Cy.Primary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(searchShape)
                    .background(Cy.Panel)
                    .border(1.dp, Cy.Secondary.copy(alpha = 0.5f), searchShape)
                    .padding(horizontal = 12.dp),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        CyberIcon(Ico.SEARCH, Cy.Secondary, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("BUSCAR PISTA…", style = tx(15.sp, Cy.Dim, Rajdhani, FontWeight.Medium, 1.sp))
                            }
                            inner()
                        }
                    }
                }
            )
            CyberIconButton(
                Ico.SHUFFLE,
                onClick = {
                    if (filtered.isNotEmpty()) onPlay(filtered, Random.nextInt(filtered.size), true)
                },
                size = 44.dp,
                iconSize = 20.dp
            )
            CyberIconButton(Ico.REFRESH, onClick = onReload, size = 44.dp, color = Cy.Secondary, iconSize = 20.dp)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cambiar la carpeta de música: abre el selector de Android y reemplaza el permiso anterior
            CyberTextButton(label = "CAMBIAR CARPETA", onClick = onPickFolder, color = Cy.Primary)
            CyberTextButton(
                label = "TODO (${tracks.size})",
                onClick = { onFolder("") },
                color = Cy.Secondary,
                filled = folder.isEmpty()
            )
            for ((path, count) in folders) {
                CyberTextButton(
                    label = "${folderName(path).uppercase()} ($count)",
                    onClick = { onFolder(path) },
                    color = Cy.Secondary,
                    filled = folder.equals(path, ignoreCase = true)
                )
            }
        }

        Text(
            "${rootName.uppercase()}  ·  ${filtered.size} PISTAS",
            style = tx(12.sp, Cy.Dim, Rajdhani, FontWeight.Bold, 2.sp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )

        when {
            !loaded -> Text(
                "ESCANEANDO CARPETA…",
                style = tx(14.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 2.sp),
                modifier = Modifier.padding(18.dp)
            )
            loaded && tracks.isEmpty() -> InfoPanel(
                title = "SIN SEÑAL",
                text = "No encontré canciones en la carpeta elegida. Revisa que tenga archivos de audio (MP3, FLAC, M4A…) o elige otra carpeta.",
                button = "CAMBIAR CARPETA",
                onButton = onPickFolder
            )
            loaded && inFolder.isEmpty() -> InfoPanel(
                title = "CARPETA VACÍA",
                text = "No encontré canciones en esa subcarpeta. Prueba con otra o vuelve a ver todo.",
                button = "VER TODO",
                onButton = { onFolder("") }
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filtered, key = { _, t -> t.id }) { index, t ->
                    TrackRow(
                        index = index,
                        track = t,
                        active = t.id.toString() == currentId,
                        onClick = { onPlay(filtered, index, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(title: String, text: String, button: String, onButton: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .cutPanel()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = tx(18.sp, Cy.Alert, Orbitron, FontWeight.Bold, 2.sp))
        Text(text, style = tx(16.sp, Cy.Text, Rajdhani, FontWeight.Medium))
        CyberTextButton(button, onButton, filled = true)
    }
}

@Composable
private fun TrackRow(index: Int, track: Track, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Cy.Primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickableFx(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .background(if (active) Cy.Primary else Color.Transparent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "%02d".format(index + 1),
            style = tx(12.sp, if (active) Cy.Primary else Cy.Dim, Orbitron, FontWeight.Normal),
            modifier = Modifier.width(38.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(17.sp, if (active) Cy.Primary else Cy.Text, Rajdhani, FontWeight.Bold)
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(13.sp, Cy.Dim, Rajdhani, FontWeight.Medium, 0.5.sp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(track.durationMs), style = tx(13.sp, Cy.Secondary, Rajdhani, FontWeight.Bold))
    }
}

// ───────────────────────── Reproductor ─────────────────────────

@Composable
private fun PlayerScreen(pc: PlayerController, art: Bitmap?) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (pc.duration > 0) (pc.position.toFloat() / pc.duration).coerceIn(0f, 1f) else 0f
    val shownPosition = drag?.let { (it * pc.duration).toLong() } ?: pc.position

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        val frame = CutCornerShape(topStart = 26.dp, bottomEnd = 26.dp)
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clip(frame)
                .background(Cy.Panel2)
                .border(2.dp, Cy.Primary, frame)
        ) {
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                NoSignalArt(Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Cy.Bg.copy(alpha = 0.5f))))
            )
            Text(
                "CH-01",
                style = tx(11.sp, Cy.Bg, Orbitron, FontWeight.Normal, 1.sp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 10.dp)
                    .background(Cy.Secondary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            pc.title.ifBlank { "SIN PISTA" },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = tx(20.sp, Cy.Primary, Orbitron, FontWeight.Bold, 1.sp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            pc.artist.ifBlank { "Elige una canción en LISTA" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = tx(17.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 2.sp)
        )

        Spacer(Modifier.height(14.dp))
        NeonSlider(
            value = fraction,
            onChange = { drag = it },
            onFinish = {
                drag?.let { pc.seekTo((it * pc.duration).toLong()) }
                drag = null
            },
            modifier = Modifier.fillMaxWidth(),
            segments = 40
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shownPosition), style = tx(13.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 1.sp))
            Text(formatTime(pc.duration), style = tx(13.sp, Cy.Dim, Rajdhani, FontWeight.Bold, 1.sp))
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CyberIconButton(
                Ico.SHUFFLE, { pc.toggleShuffle() },
                size = 44.dp,
                color = if (pc.shuffle) Cy.Secondary else Cy.Dim,
                iconSize = 20.dp
            )
            CyberIconButton(Ico.PREV, { pc.previous() }, size = 52.dp, iconSize = 22.dp)
            CyberIconButton(
                if (pc.playing) Ico.PAUSE else Ico.PLAY,
                { pc.togglePlay() },
                size = 72.dp,
                filled = true,
                iconSize = 32.dp
            )
            CyberIconButton(Ico.NEXT, { pc.next() }, size = 52.dp, iconSize = 22.dp)
            CyberIconButton(
                if (pc.repeat == Player.REPEAT_MODE_ONE) Ico.REPEAT_ONE else Ico.REPEAT,
                { pc.cycleRepeat() },
                size = 44.dp,
                color = if (pc.repeat == Player.REPEAT_MODE_OFF) Cy.Dim else Cy.Secondary,
                iconSize = 20.dp
            )
        }

        Spacer(Modifier.height(20.dp))
        SpectrumBars(pc.playing, Modifier.fillMaxWidth().height(64.dp))
        Spacer(Modifier.height(12.dp))
    }
}

// ───────────────────────── Audio / ecualizador ─────────────────────────

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) "%.1fk".format(hz / 1000f) else "$hz Hz"

@Composable
private fun AudioScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ECUALIZADOR", style = tx(20.sp, Cy.Primary, Orbitron, FontWeight.Bold, 2.sp))
                Text("AUDIO.SYS // TUNING", style = tx(12.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 2.sp))
            }
            CyberTextButton(
                label = if (Fx.enabledState) "ON" else "OFF",
                onClick = { Fx.toggleEnabled() },
                color = if (Fx.enabledState) Cy.Secondary else Cy.Dim,
                filled = Fx.enabledState
            )
        }

        if (!Fx.supported) {
            InfoPanel(
                title = "MOTOR APAGADO",
                text = "El motor de audio aún no está listo o tu teléfono no permite ecualizador. Reproduce una canción y vuelve a entrar.",
                button = "OK",
                onButton = {}
            )
        } else {
            // Presets
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (name in Fx.presets.keys) {
                    CyberTextButton(
                        label = name,
                        onClick = { Fx.choosePreset(name) },
                        color = Cy.Primary,
                        filled = Fx.presetName == name
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().cutPanel().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val min = Fx.minLevel
                val max = Fx.maxLevel
                val range = (max - min).coerceAtLeast(1)
                for (i in Fx.levels.indices) {
                    val level = Fx.levels[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            freqLabel(Fx.freqs.getOrElse(i) { 0 }),
                            style = tx(13.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 0.5.sp),
                            modifier = Modifier.width(56.dp)
                        )
                        NeonSlider(
                            value = (level - min).toFloat() / range,
                            onChange = { f -> Fx.changeBand(i, min + (f * range).roundToInt()) },
                            modifier = Modifier.weight(1f),
                            color = Cy.Primary,
                            segments = 24
                        )
                        Text(
                            "%+.1f".format(level / 100f),
                            textAlign = TextAlign.End,
                            style = tx(13.sp, Cy.Text, Rajdhani, FontWeight.Bold),
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
                Text(
                    "Valores en dB. Arrastra cada barra para ajustar esa frecuencia.",
                    style = tx(12.sp, Cy.Dim, Rajdhani, FontWeight.Medium)
                )
            }
        }

        // Graves
        Column(
            Modifier.fillMaxWidth().cutPanel(border = Cy.Alert.copy(alpha = 0.55f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("REFUERZO DE GRAVES", style = tx(15.sp, Cy.Alert, Rajdhani, FontWeight.Bold, 2.sp))
            if (Fx.bassAvailable) {
                NeonSlider(
                    value = Fx.bassStrength / 1000f,
                    onChange = { Fx.changeBass((it * 1000).roundToInt()) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Cy.Alert,
                    segments = 30
                )
                Text("${Fx.bassStrength / 10}%", style = tx(13.sp, Cy.Text, Rajdhani, FontWeight.Bold))
            } else {
                Text("No disponible en este teléfono.", style = tx(14.sp, Cy.Dim, Rajdhani, FontWeight.Medium))
            }
        }

        // Volumen extra
        Column(
            Modifier.fillMaxWidth().cutPanel(border = Cy.Secondary.copy(alpha = 0.55f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("VOLUMEN EXTRA", style = tx(15.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 2.sp))
            if (Fx.loudAvailable) {
                NeonSlider(
                    value = Fx.loudGain / 1000f,
                    onChange = { Fx.changeLoud((it * 1000).roundToInt()) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Cy.Secondary,
                    segments = 30
                )
                Text("+%.1f dB".format(Fx.loudGain / 100f), style = tx(13.sp, Cy.Text, Rajdhani, FontWeight.Bold))
                Text(
                    "Si notas que el sonido se distorsiona, baja esta barra o el refuerzo de graves.",
                    style = tx(12.sp, Cy.Dim, Rajdhani, FontWeight.Medium)
                )
            } else {
                Text("No disponible en este teléfono.", style = tx(14.sp, Cy.Dim, Rajdhani, FontWeight.Medium))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ───────────────────────── Perfil (foto, alias y colores) ─────────────────────────

@Composable
private fun ProfileScreen(
    avatar: Bitmap?,
    alias: String,
    onAlias: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    trackCount: Int,
    folderCount: Int,
    onTheme: (Int) -> Unit
) {
    val fieldShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("PERFIL", style = tx(20.sp, Cy.Primary, Orbitron, FontWeight.Bold, 2.sp))
            Text("USER.SYS // IDENTIDAD", style = tx(12.sp, Cy.Secondary, Rajdhani, FontWeight.Bold, 2.sp))
        }

        AvatarBadge(avatar, alias, 150.dp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CyberTextButton(if (avatar == null) "SUBIR FOTO" else "CAMBIAR FOTO", onPickAvatar, filled = true)
            if (avatar != null) {
                CyberTextButton("QUITAR", onRemoveAvatar, color = Cy.Alert)
            }
        }

        // Alias
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ALIAS", style = tx(13.sp, Cy.Dim, Rajdhani, FontWeight.Bold, 2.sp))
            BasicTextField(
                value = alias,
                onValueChange = { onAlias(it.take(20)) },
                singleLine = true,
                textStyle = tx(18.sp, Cy.Text, Rajdhani, FontWeight.Bold, 1.sp),
                cursorBrush = SolidColor(Cy.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(fieldShape)
                    .background(Cy.Panel)
                    .border(1.dp, Cy.Primary.copy(alpha = 0.6f), fieldShape)
                    .padding(horizontal = 14.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (alias.isEmpty()) {
                            Text(
                                "Escribe tu nombre o apodo",
                                style = tx(16.sp, Cy.Dim, Rajdhani, FontWeight.Medium)
                            )
                        }
                        inner()
                    }
                }
            )
        }

        // Estadísticas de la biblioteca
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("PISTAS", trackCount.toString(), Cy.Primary, Modifier.weight(1f))
            StatBox("CARPETAS", folderCount.toString(), Cy.Secondary, Modifier.weight(1f))
        }

        // Sonido de menú (se puede apagar)
        Row(
            Modifier.fillMaxWidth().cutPanel().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("SONIDO DE MENÚ", style = tx(14.sp, Cy.Text, Rajdhani, FontWeight.Bold, 1.sp))
                Text("Efecto al tocar opciones y canciones", style = tx(12.sp, Cy.Dim, Rajdhani, FontWeight.Medium))
            }
            CyberTextButton(
                label = if (UiSound.enabled) "ACTIVADO" else "DESACTIVADO",
                onClick = { UiSound.toggle() },
                color = if (UiSound.enabled) Cy.Primary else Cy.Dim,
                filled = UiSound.enabled
            )
        }

        // Temas de color
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("COLORES", style = tx(13.sp, Cy.Dim, Rajdhani, FontWeight.Bold, 2.sp))
            NeonThemes.chunked(2).forEachIndexed { rowIndex, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { i, theme ->
                        val index = rowIndex * 2 + i
                        ThemeCard(theme, Cy.themeIndex == index, Modifier.weight(1f)) { onTheme(index) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .cutPanel(border = color.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(value, style = tx(26.sp, color, Orbitron, FontWeight.Bold))
        Text(label, style = tx(12.sp, Cy.Dim, Rajdhani, FontWeight.Bold, 2.sp))
    }
}

@Composable
private fun ThemeCard(theme: NeonTheme, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    Row(
        modifier
            .height(64.dp)
            .clip(shape)
            .background(theme.panel)
            .border(
                if (selected) 2.5.dp else 1.dp,
                if (selected) theme.primary else Cy.Dim.copy(alpha = 0.4f),
                shape
            )
            .clickableFx(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(theme.name, maxLines = 1, style = tx(14.sp, theme.primary, Rajdhani, FontWeight.Bold, 1.sp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(width = 22.dp, height = 8.dp).background(theme.primary))
                Box(Modifier.size(width = 14.dp, height = 8.dp).background(theme.secondary))
                Box(Modifier.size(width = 8.dp, height = 8.dp).background(theme.alert))
            }
        }
        if (selected) {
            Text("ON", style = tx(12.sp, theme.primary, Orbitron, FontWeight.Normal))
        }
    }
}
