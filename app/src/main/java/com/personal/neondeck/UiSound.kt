package com.personal.neondeck

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sonido de menú estilo cyberpunk: dos "blips" digitales cortos (subida de tono + nota aguda con efecto 8 bits).
 * Se genera por código (no hay archivos de audio) y se puede activar o desactivar en PERFIL.
 * Usa el canal de "efectos de interfaz", así que no interrumpe ni baja el volumen de la música.
 */
object UiSound {
    private const val RATE = 44100
    private const val KEY = "ui_sound"

    private var prefs: SharedPreferences? = null
    private var track: AudioTrack? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("neon_ui", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean(KEY, true)
    }

    fun toggle() {
        enabled = !enabled
        prefs?.edit()?.putBoolean(KEY, enabled)?.apply()
        if (enabled) play() // al activarlo suena una vez para que lo escuches
    }

    fun play() {
        if (!enabled) return
        try {
            val t = track ?: build().also { track = it }
            t.stop()
            t.setPlaybackHeadPosition(0)
            t.play()
        } catch (_: Throwable) {
            try { track?.release() } catch (_: Throwable) {}
            track = null
        }
    }

    private fun build(): AudioTrack {
        val data = synth()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, data.size)
        return t
    }

    /** Un blip: tono que sube de f0 a f1, onda casi cuadrada, con bajada rápida y "crujido" de pocos bits. */
    private fun blip(f0: Double, f1: Double, ms: Int): ShortArray {
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = f0 + (f1 - f0) * t
            phase += 2.0 * PI * f / RATE
            val wave = sin(phase) + sin(3.0 * phase) / 3.0
            val attack = minOf(1.0, i / (RATE * 0.002)) // 2 ms para evitar el "clic"
            val env = attack * (1.0 - t).pow(1.5)
            val v = wave * env * 0.30
            val crushed = (v * 32.0).roundToInt() / 32.0
            out[i] = (crushed * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun synth(): ShortArray {
        val a = blip(900.0, 1800.0, 55)
        val gap = ShortArray(RATE * 12 / 1000)
        val b = blip(2700.0, 2700.0, 45)
        return a + gap + b
    }
}
