package com.example.alarmbus

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt

/**
 * Gerenciador singleton para reprodução do som do alarme, vibração, controle de volume
 * e restauração do volume original após desativação.
 */
object AlarmPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var previousVolume: Int = -1
    private var ringing: Boolean = false

    fun isRinging(): Boolean = ringing

    fun startAlarm(
        context: Context,
        ringtoneUri: Uri? = null,
        vibrate: Boolean = true,
        volumeLevelPercent: Float = 100f
    ) {
        if (ringing) return
        ringing = true

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // 1. Salva o volume original do fluxo de ALARME do dispositivo
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        // 2. Ajusta o volume com base na porcentagem selecionada na barra (0 a 100%)
        val targetVol = (maxVol * (volumeLevelPercent / 100f)).roundToInt().coerceIn(0, maxVol)

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Erro ao ajustar volume", e)
        }

        // 3. Toca o som em loop usando MediaPlayer
        try {
            val soundUri = ringtoneUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI

            val player = MediaPlayer()
            player.setDataSource(context, soundUri)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Erro ao tocar som do alarme", e)
        }

        // 4. Vibração
        if (vibrate) {
            try {
                val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator = vib

                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(longArrayOf(0, 500, 200, 500), 0)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmPlayer", "Erro ao acionar vibração", e)
            }
        }

        // 5. Inicia o serviço de Overlay (janela flutuante, se permitido)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            try {
                val overlayIntent = Intent(context, AlarmOverlayService::class.java)
                context.startService(overlayIntent)
            } catch (e: Exception) {
                Log.e("AlarmPlayer", "Erro ao iniciar overlay", e)
            }
        }

        // 6. Abre a tela cheia de alarme (AlarmActivity) para permitir desativação rápida
        try {
            val alarmActivityIntent = Intent(context, AlarmActivity::class.java)
            alarmActivityIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            context.startActivity(alarmActivityIntent)
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Erro ao abrir AlarmActivity", e)
        }
    }

    fun stopAlarm(context: Context) {
        ringing = false

        // Para a mídia
        try {
            val player = mediaPlayer
            if (player != null) {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Erro ao parar mídia", e)
        } finally {
            mediaPlayer = null
        }

        // Para a vibração
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Erro ao cancelar vibração", e)
        } finally {
            vibrator = null
        }

        // Restaura o volume original que estava configurado no dispositivo antes do alarme
        if (previousVolume != -1) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
            } catch (e: Exception) {
                Log.e("AlarmPlayer", "Erro ao restaurar volume original", e)
            } finally {
                previousVolume = -1
            }
        }

        // Para o serviço de sobreposição (Overlay)
        try {
            val overlayIntent = Intent(context, AlarmOverlayService::class.java)
            context.stopService(overlayIntent)
        } catch (e: Exception) {
            Log.e("AlarmPlayer", "Erro ao parar overlay service", e)
        }

        // Atualiza preferências e envia Broadcast para a interface
        AlarmPreferences.setAlarmActive(context, false)

        val updateIntent = Intent("com.example.alarmbus.ACTION_ALARM_STOPPED")
        updateIntent.setPackage(context.packageName)
        context.sendBroadcast(updateIntent)
    }
}
