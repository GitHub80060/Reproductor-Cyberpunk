package com.personal.neondeck

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Efectos de audio: ecualizador, refuerzo de graves y más volumen.
 * Los crea el servicio (en la sesión de audio del reproductor) y la pantalla AUDIO los controla.
 * Los ajustes se guardan para la próxima vez que abras la app.
 */
object Fx {
    private const val PREFS = "neon_fx"

    private var prefs: SharedPreferences? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null

    var supported by mutableStateOf(false)
        private set
    var bassAvailable by mutableStateOf(false)
        private set
    var loudAvailable by mutableStateOf(false)
        private set
    var enabledState by mutableStateOf(true)
        private set
    var presetName by mutableStateOf("NEON")
        private set
    var bassStrength by mutableIntStateOf(200)   // 0..1000
        private set
    var loudGain by mutableIntStateOf(0)         // milibelios, 0..1000
        private set
    var minLevel by mutableIntStateOf(-1500)
        private set
    var maxLevel by mutableIntStateOf(1500)
        private set

    /** Nivel actual de cada banda, en milibelios (100 = 1 dB). */
    val levels = mutableStateListOf<Int>()

    /** Frecuencia central de cada banda, en Hz. */
    val freqs = mutableStateListOf<Int>()

    /** Curvas de cada preset: 5 puntos que se adaptan al número de bandas del teléfono. */
    val presets: Map<String, FloatArray> = linkedMapOf(
        "PLANO" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "NEON" to floatArrayOf(0.55f, 0.15f, -0.10f, 0.25f, 0.55f),
        "GRAVES" to floatArrayOf(0.90f, 0.55f, 0.05f, -0.05f, 0.00f),
        "VOCES" to floatArrayOf(-0.30f, 0.00f, 0.55f, 0.40f, -0.10f),
        "AGUDOS" to floatArrayOf(-0.20f, 0.00f, 0.05f, 0.55f, 0.90f)
    )

    fun attach(ctx: Context, session: Int) {
        if (prefs == null) prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs!!
        releaseEffects()

        enabledState = p.getBoolean("enabled", true)
        bassStrength = p.getInt("bass", 200)
        loudGain = p.getInt("loud", 0)
        presetName = p.getString("preset", "NEON") ?: "NEON"

        try {
            val e = Equalizer(0, session)
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()

            freqs.clear()
            for (i in 0 until n) freqs.add(e.getCenterFreq(i.toShort()) / 1000)

            val saved = p.getString("levels", null)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
            levels.clear()
            if (saved != null && saved.size == n) levels.addAll(saved)
            else levels.addAll(presetLevels(presetName, n))

            eq = e
            supported = true
        } catch (t: Throwable) {
            supported = false
        }

        try {
            val b = BassBoost(0, session)
            if (b.strengthSupported) bass = b else b.release()
        } catch (t: Throwable) {
            bass = null
        }

        try {
            loud = LoudnessEnhancer(session)
        } catch (t: Throwable) {
            loud = null
        }

        bassAvailable = bass != null
        loudAvailable = loud != null
        applyAll()
    }

    fun release() {
        releaseEffects()
        supported = false
        bassAvailable = false
        loudAvailable = false
    }

    private fun releaseEffects() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        try { loud?.release() } catch (_: Throwable) {}
        eq = null
        bass = null
        loud = null
    }

    private fun applyAll() {
        eq?.let { e ->
            try {
                for (i in levels.indices) {
                    e.setBandLevel(i.toShort(), levels[i].coerceIn(minLevel, maxLevel).toShort())
                }
                e.setEnabled(enabledState)
            } catch (_: Throwable) {}
        }
        bass?.let { b ->
            try {
                b.setStrength(bassStrength.coerceIn(0, 1000).toShort())
                b.setEnabled(enabledState && bassStrength > 0)
            } catch (_: Throwable) {}
        }
        loud?.let { l ->
            try {
                l.setTargetGain(loudGain.coerceIn(0, 1000))
                l.setEnabled(enabledState && loudGain > 0)
            } catch (_: Throwable) {}
        }
    }

    private fun presetLevels(name: String, n: Int): List<Int> {
        val curve = presets[name] ?: presets.getValue("PLANO")
        return List(n) { i ->
            val pos = if (n <= 1) 0f else i.toFloat() / (n - 1) * (curve.size - 1)
            val lo = pos.toInt().coerceIn(0, curve.size - 1)
            val hi = (lo + 1).coerceAtMost(curve.size - 1)
            val v = curve[lo] + (curve[hi] - curve[lo]) * (pos - lo)
            (v * maxLevel * 0.6f).toInt().coerceIn(minLevel, maxLevel)
        }
    }

    private fun save() {
        prefs?.edit()
            ?.putBoolean("enabled", enabledState)
            ?.putInt("bass", bassStrength)
            ?.putInt("loud", loudGain)
            ?.putString("preset", presetName)
            ?.putString("levels", levels.joinToString(","))
            ?.apply()
    }

    // ---- Acciones que usa la pantalla AUDIO ----

    fun toggleEnabled() {
        enabledState = !enabledState
        applyAll()
        save()
    }

    fun choosePreset(name: String) {
        presetName = name
        val n = levels.size
        if (n > 0) {
            val newLevels = presetLevels(name, n)
            for (i in 0 until n) levels[i] = newLevels[i]
        }
        applyAll()
        save()
    }

    fun changeBand(index: Int, level: Int) {
        if (index !in levels.indices) return
        levels[index] = level.coerceIn(minLevel, maxLevel)
        presetName = "CUSTOM"
        applyAll()
        save()
    }

    fun changeBass(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        applyAll()
        save()
    }

    fun changeLoud(gain: Int) {
        loudGain = gain.coerceIn(0, 1000)
        applyAll()
        save()
    }
}
