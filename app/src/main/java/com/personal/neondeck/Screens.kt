package com.personal.neondeck

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

// ───────────────────────── Raíz de la app ─────────────────────────

@Composable
fun NeonApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cy.Yellow,
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

    // Permisos: leer música (y mostrar la notificación en Android 13+)
    val audioPerm =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[audioPerm] == true ||
            ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED
    }
    val askPermissions = {
        val list = mutableListOf(audioPerm)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        launcher.launch(list.toTypedArray())
    }
    LaunchedEffect(Unit) { if (!granted) askPermissions() }

    // Biblioteca
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(granted, reloadKey) {
        if (granted) {
            tracks = withContext(Dispatchers.IO) { loadTracks(appCtx) }
            loaded = true
        }
    }
    val byId = remember(tracks) { tracks.associateBy { it.id.toString() } }
    val current = pc.mediaId?.let { byId[it] }
    val art by produceState<Bitmap?>(null, current?.id) {
        value = loadArt(appCtx, current)
    }

    // Avance de la barra de progreso mientras suena
    LaunchedEffect(pc.playing) {
        while (pc.playing) {
            pc.refreshPosition()
            delay(250)
        }
    }

    var tab by rememberSaveable { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().cyberBackground()) {
        Header()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> LibraryScreen(
                    tracks = tracks,
                    loaded = loaded,
                    granted = granted,
                    currentId = pc.mediaId,
                    onAsk = { askPermissions() },
                    onReload = { reloadKey++ },
                    onPlay = { list, index, shuffleOn ->
                        pc.playQueue(list, index, shuffleOn)
                        if (shuffleOn == true) tab = 1
                    }
                )
                1 -> PlayerScreen(pc = pc, art = art)
                else -> AudioScreen()
            }
        }
        if (tab != 1 && pc.mediaId != null) {
            MiniPlayer(pc = pc, onOpen = { tab = 1 })
        }
        NavBar(selected = tab, onSelect = { tab = it })
    }
}

// ───────────────────────── Cabecera y navegación ─────────────────────────

@Composable
private fun Header() {
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
            GlitchText("NEON//DECK", tx(24.sp, Cy.Yellow, Orbitron, FontWeight.Bold, 2.sp))
            Text("AUDIO.SYS  ·  ONLINE", style = tx(12.sp, Cy.Cyan, Rajdhani, FontWeight.Bold, 3.sp))
        }
        Box(
            Modifier
                .size(12.dp)
                .background(Cy.Red.copy(alpha = blink.value))
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Brush.horizontalGradient(listOf(Cy.Yellow, Cy.Red, Color.Transparent)))
    )
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Cy.Panel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NavItem("LISTA", Ico.LIB, selected == 0, Modifier.weight(1f)) { onSelect(0) }
        NavItem("DECK", Ico.DECK, selected == 1, Modifier.weight(1f)) { onSelect(1) }
        NavItem("AUDIO", Ico.EQ, selected == 2, Modifier.weight(1f)) { onSelect(2) }
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
            .background(if (active) Cy.Yellow else Color.Transparent)
            .border(1.dp, if (active) Cy.Yellow else Cy.Dim.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CyberIcon(ico, fg, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, style = tx(14.sp, fg, Rajdhani, FontWeight.Bold, 1.5.sp))
    }
}

@Composable
private fun MiniPlayer(pc: PlayerController, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .cutPanel(border = Cy.Yellow.copy(alpha = 0.6f), fill = Cy.Panel2, cut = 12.dp)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                pc.title.ifBlank { "—" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(16.sp, Cy.Yellow, Rajdhani, FontWeight.Bold)
            )
            Text(
                pc.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(13.sp, Cy.Cyan, Rajdhani, FontWeight.Medium)
            )
        }
        CyberIconButton(Ico.PREV, { pc.previous() }, size = 40.dp, color = Cy.Cyan, iconSize = 18.dp)
        Spacer(Modifier.width(6.dp))
        CyberIconButton(
            if (pc.playing) Ico.PAUSE else Ico.PLAY,
            { pc.togglePlay() },
            size = 44.dp,
            filled = true,
            iconSize = 20.dp
        )
        Spacer(Modifier.width(6.dp))
        CyberIconButton(Ico.NEXT, { pc.next() }, size = 40.dp, color = Cy.Cyan, iconSize = 18.dp)
    }
}

// ───────────────────────── Biblioteca ─────────────────────────

@Composable
private fun LibraryScreen(
    tracks: List<Track>,
    loaded: Boolean,
    granted: Boolean,
    currentId: String?,
    onAsk: () -> Unit,
    onReload: () -> Unit,
    onPlay: (List<Track>, Int, Boolean?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks
        else tracks.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
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
                cursorBrush = SolidColor(Cy.Yellow),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(searchShape)
                    .background(Cy.Panel)
                    .border(1.dp, Cy.Cyan.copy(alpha = 0.5f), searchShape)
                    .padding(horizontal = 12.dp),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        CyberIcon(Ico.SEARCH, Cy.Cyan, Modifier.size(18.dp))
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
            CyberIconButton(Ico.REFRESH, onClick = onReload, size = 44.dp, color = Cy.Cyan, iconSize = 20.dp)
        }

        Text(
            "${filtered.size} PISTAS",
            style = tx(12.sp, Cy.Dim, Rajdhani, FontWeight.Bold, 2.sp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )

        when {
            !granted -> InfoPanel(
                title = "ACCESO DENEGADO",
                text = "Necesito permiso para leer la música de tu teléfono.",
                button = "CONCEDER ACCESO",
                onButton = onAsk
            )
            loaded && tracks.isEmpty() -> InfoPanel(
                title = "SIN SEÑAL",
                text = "No encontré canciones. Copia archivos de audio (MP3, FLAC, M4A…) a tu teléfono y pulsa el botón de recargar.",
                button = "RECARGAR",
                onButton = onReload
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
        Text(title, style = tx(18.sp, Cy.Red, Orbitron, FontWeight.Bold, 2.sp))
        Text(text, style = tx(16.sp, Cy.Text, Rajdhani, FontWeight.Medium))
        CyberTextButton(button, onButton, filled = true)
    }
}

@Composable
private fun TrackRow(index: Int, track: Track, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Cy.Yellow.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .background(if (active) Cy.Yellow else Color.Transparent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "%02d".format(index + 1),
            style = tx(12.sp, if (active) Cy.Yellow else Cy.Dim, Orbitron, FontWeight.Normal),
            modifier = Modifier.width(38.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(17.sp, if (active) Cy.Yellow else Cy.Text, Rajdhani, FontWeight.Bold)
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tx(13.sp, Cy.Dim, Rajdhani, FontWeight.Medium, 0.5.sp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(track.durationMs), style = tx(13.sp, Cy.Cyan, Rajdhani, FontWeight.Bold))
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
                .border(2.dp, Cy.Yellow, frame)
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
                    .background(Cy.Cyan)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            pc.title.ifBlank { "SIN PISTA" },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = tx(20.sp, Cy.Yellow, Orbitron, FontWeight.Bold, 1.sp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            pc.artist.ifBlank { "Elige una canción en LISTA" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = tx(17.sp, Cy.Cyan, Rajdhani, FontWeight.Bold, 2.sp)
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
            Text(formatTime(shownPosition), style = tx(13.sp, Cy.Cyan, Rajdhani, FontWeight.Bold, 1.sp))
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
                color = if (pc.shuffle) Cy.Cyan else Cy.Dim,
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
                color = if (pc.repeat == Player.REPEAT_MODE_OFF) Cy.Dim else Cy.Cyan,
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
                Text("ECUALIZADOR", style = tx(20.sp, Cy.Yellow, Orbitron, FontWeight.Bold, 2.sp))
                Text("AUDIO.SYS // TUNING", style = tx(12.sp, Cy.Cyan, Rajdhani, FontWeight.Bold, 2.sp))
            }
            CyberTextButton(
                label = if (Fx.enabledState) "ON" else "OFF",
                onClick = { Fx.toggleEnabled() },
                color = if (Fx.enabledState) Cy.Cyan else Cy.Dim,
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
                        color = Cy.Yellow,
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
                            style = tx(13.sp, Cy.Cyan, Rajdhani, FontWeight.Bold, 0.5.sp),
                            modifier = Modifier.width(56.dp)
                        )
                        NeonSlider(
                            value = (level - min).toFloat() / range,
                            onChange = { f -> Fx.changeBand(i, min + (f * range).roundToInt()) },
                            modifier = Modifier.weight(1f),
                            color = Cy.Yellow,
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
            Modifier.fillMaxWidth().cutPanel(border = Cy.Red.copy(alpha = 0.55f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("REFUERZO DE GRAVES", style = tx(15.sp, Cy.Red, Rajdhani, FontWeight.Bold, 2.sp))
            if (Fx.bassAvailable) {
                NeonSlider(
                    value = Fx.bassStrength / 1000f,
                    onChange = { Fx.changeBass((it * 1000).roundToInt()) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Cy.Red,
                    segments = 30
                )
                Text("${Fx.bassStrength / 10}%", style = tx(13.sp, Cy.Text, Rajdhani, FontWeight.Bold))
            } else {
                Text("No disponible en este teléfono.", style = tx(14.sp, Cy.Dim, Rajdhani, FontWeight.Medium))
            }
        }

        // Volumen extra
        Column(
            Modifier.fillMaxWidth().cutPanel(border = Cy.Cyan.copy(alpha = 0.55f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("VOLUMEN EXTRA", style = tx(15.sp, Cy.Cyan, Rajdhani, FontWeight.Bold, 2.sp))
            if (Fx.loudAvailable) {
                NeonSlider(
                    value = Fx.loudGain / 1000f,
                    onChange = { Fx.changeLoud((it * 1000).roundToInt()) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Cy.Cyan,
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
