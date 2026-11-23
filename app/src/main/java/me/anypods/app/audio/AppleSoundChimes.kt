package me.anypods.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object AppleSoundChimes {
    private const val TAG = "AnyPods.AppleChimes"
    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.IO)

    fun playAncWhoosh(context: Context) {
        scope.launch {
            val file = getOrCreateChime(context, "anc_suction.wav") {
                // Downward suction frequency sweep (720Hz -> 130Hz) with soft envelope
                val durationSec = 0.38f
                val totalSamples = (SAMPLE_RATE * durationSec).toInt()
                val samples = ShortArray(totalSamples)

                var phase = 0.0
                for (i in 0 until totalSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val progress = t / durationSec
                    // Frequency glides down exponentially
                    val freq = 720.0 * Math.pow(130.0 / 720.0, progress.toDouble())
                    phase += 2.0 * PI * freq / SAMPLE_RATE

                    // Smooth attack and soft decay
                    val envelope: Double = when {
                        progress < 0.15f -> (progress / 0.15f).toDouble()
                        else -> exp(-3.8 * (progress - 0.15f))
                    }
                    val sample = (sin(phase) * envelope * 0.75 * Short.MAX_VALUE.toDouble()).toInt()
                    samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                samples
            }
            playWav(context, file)
        }
    }

    fun playTransparencyTone(context: Context) {
        scope.launch {
            val file = getOrCreateChime(context, "transparency_tone.wav") {
                // Crisp dual high chime (1050Hz + 2100Hz harmonic)
                val durationSec = 0.30f
                val totalSamples = (SAMPLE_RATE * durationSec).toInt()
                val samples = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val progress = t / durationSec
                    val env: Double = when {
                        progress < 0.05f -> (progress / 0.05f).toDouble()
                        else -> exp(-6.5 * (progress - 0.05f))
                    }
                    val s1 = sin(2.0 * PI * 1046.5 * t) * 0.65 // C6
                    val s2 = sin(2.0 * PI * 2093.0 * t) * 0.35 // C7
                    val sample = ((s1 + s2) * env * 0.70 * Short.MAX_VALUE.toDouble()).toInt()
                    samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                samples
            }
            playWav(context, file)
        }
    }

    fun playAdaptiveTone(context: Context) {
        scope.launch {
            val file = getOrCreateChime(context, "adaptive_tone.wav") {
                // Rising two-tone chirp (880Hz -> 1320Hz)
                val durationSec = 0.32f
                val totalSamples = (SAMPLE_RATE * durationSec).toInt()
                val samples = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val progress = t / durationSec
                    val freq = if (progress < 0.45f) 880.0 else 1320.0
                    val env: Double = when {
                        progress < 0.06f -> (progress / 0.06f).toDouble()
                        progress in 0.42f..0.48f -> 0.4
                        else -> exp(-4.2 * (progress - 0.48f).coerceAtLeast(0.0f))
                    }
                    val sample = (sin(2.0 * PI * freq * t) * env * 0.65 * Short.MAX_VALUE.toDouble()).toInt()
                    samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                samples
            }
            playWav(context, file)
        }
    }

    fun playOffTone(context: Context) {
        scope.launch {
            val file = getOrCreateChime(context, "off_tone.wav") {
                // Subtle double-click tap (320Hz damped)
                val durationSec = 0.22f
                val totalSamples = (SAMPLE_RATE * durationSec).toInt()
                val samples = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val progress = t / durationSec
                    val env = when {
                        progress < 0.08f -> exp(-25.0 * progress)
                        progress in 0.10f..0.18f -> exp(-25.0 * (progress - 0.10f))
                        else -> 0.0
                    }
                    val sample = (sin(2.0 * PI * 340.0 * t) * env * 0.55 * Short.MAX_VALUE.toDouble()).toInt()
                    samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                samples
            }
            playWav(context, file)
        }
    }

    fun playConnectionChime(context: Context) {
        scope.launch {
            val file = getOrCreateChime(context, "connection_chime.wav") {
                // Apple's signature 3-tone major triad ascending (C5 523Hz -> E5 659Hz -> G5 784Hz)
                val durationSec = 0.55f
                val totalSamples = (SAMPLE_RATE * durationSec).toInt()
                val samples = ShortArray(totalSamples)

                for (i in 0 until totalSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val progress = t / durationSec

                    val freq = when {
                        progress < 0.28f -> 523.25
                        progress < 0.56f -> 659.25
                        else -> 783.99
                    }
                    val env: Double = when {
                        progress < 0.04f -> (progress / 0.04f).toDouble()
                        else -> exp(-2.5 * (progress - 0.56f).coerceAtLeast(0.0f))
                    }
                    val sample = (sin(2.0 * PI * freq * t) * env * 0.70 * Short.MAX_VALUE.toDouble()).toInt()
                    samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                samples
            }
            playWav(context, file)
        }
    }

    private fun playWav(context: Context, file: File) {
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, Uri.fromFile(file))
                prepare()
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play chime: ${e.message}")
        }
    }

    private fun getOrCreateChime(context: Context, filename: String, generator: () -> ShortArray): File {
        val file = File(context.cacheDir, filename)
        if (!file.exists()) {
            val samples = generator()
            writeWavFile(file, samples, SAMPLE_RATE)
        }
        return file
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val byteData = ByteArray(samples.size * 2)
        val buffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            buffer.putShort(s)
        }

        FileOutputStream(file).use { fos ->
            // WAV Header (44 bytes)
            val totalAudioLen = byteData.size
            val totalDataLen = totalAudioLen + 36
            val byteRate = sampleRate * 2

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // PCM
            header[21] = 0
            header[22] = 1 // Mono
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2 // block align
            header[33] = 0
            header[34] = 16 // bits per sample
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            fos.write(header)
            fos.write(byteData)
        }
    }
}
