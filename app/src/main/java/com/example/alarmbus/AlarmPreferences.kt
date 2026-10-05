package com.example.alarmbus

import android.content.Context
import android.net.Uri

/**
 * Gerenciador de preferências para persistência de dados de configuração do alarme e histórico.
 */
object AlarmPreferences {
    private const val PREFS_NAME = "alarmbus_prefs"

    private const val KEY_DEST_LAT = "dest_lat"
    private const val KEY_DEST_LNG = "dest_lng"
    private const val KEY_DEST_ADDRESS = "dest_address"
    private const val KEY_ALERT_DIST = "alert_dist"
    private const val KEY_VIBRATION = "vibration"
    private const val KEY_VOLUME_LEVEL = "volume_level"
    private const val KEY_TONE_URI = "tone_uri"
    private const val KEY_ALARM_ACTIVE = "alarm_active"
    private const val KEY_HISTORICO = "historico"

    fun saveAlarmConfig(
        context: Context,
        lat: Double,
        lng: Double,
        address: String,
        dist: Int,
        vibration: Boolean,
        volLevel: Float,
        toneUri: Uri?,
        active: Boolean
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putFloat(KEY_DEST_LAT, lat.toFloat())
        editor.putFloat(KEY_DEST_LNG, lng.toFloat())
        editor.putString(KEY_DEST_ADDRESS, address)
        editor.putInt(KEY_ALERT_DIST, dist)
        editor.putBoolean(KEY_VIBRATION, vibration)
        editor.putFloat(KEY_VOLUME_LEVEL, volLevel)
        editor.putString(KEY_TONE_URI, toneUri?.toString())
        editor.putBoolean(KEY_ALARM_ACTIVE, active)
        editor.apply()
    }

    fun setAlarmActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALARM_ACTIVE, active)
            .apply()
    }

    fun isAlarmActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALARM_ACTIVE, false)
    }

    fun getDestLat(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_DEST_LAT, 0f).toDouble()

    fun getDestLng(context: Context): Double =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_DEST_LNG, 0f).toDouble()

    fun getDestAddress(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_DEST_ADDRESS, "") ?: ""

    fun getAlertDist(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_ALERT_DIST, 500)

    fun isVibrationActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_VIBRATION, true)

    fun getVolumeLevel(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getFloat(KEY_VOLUME_LEVEL, 100f)

    fun getToneUri(context: Context): Uri? {
        val str = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TONE_URI, null)
        return if (!str.isNullOrEmpty()) Uri.parse(str) else null
    }

    fun saveHistorico(context: Context, list: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_HISTORICO, list.toSet())
            .apply()
    }

    fun getHistorico(context: Context): List<String> {
        val set = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getStringSet(KEY_HISTORICO, emptySet())
        return set?.toList() ?: emptyList()
    }
}
