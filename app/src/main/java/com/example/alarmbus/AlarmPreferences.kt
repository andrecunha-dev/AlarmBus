package com.example.alarmbus

import android.content.Context
import android.net.Uri

/**
 * Gerenciador de preferências para persistência de dados de configuração do alarme e histórico.
 * Utiliza precisão de 64 bits (Double.doubleToRawLongBits) para armazenar coordenadas GPS com exatidão milimétrica.
 */
object AlarmPreferences {
    private const val PREFS_NAME = "alarmbus_prefs"

    private const val KEY_DEST_LAT = "dest_lat_v2"
    private const val KEY_DEST_LNG = "dest_lng_v2"
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
        editor.putLong(KEY_DEST_LAT, java.lang.Double.doubleToRawLongBits(lat))
        editor.putLong(KEY_DEST_LNG, java.lang.Double.doubleToRawLongBits(lng))
        editor.putString(KEY_DEST_ADDRESS, address)
        editor.putInt(KEY_ALERT_DIST, dist)
        editor.putBoolean(KEY_VIBRATION, vibration)
        editor.putFloat(KEY_VOLUME_LEVEL, volLevel)
        editor.putString(KEY_TONE_URI, toneUri?.toString())
        editor.putBoolean(KEY_ALARM_ACTIVE, active)
        editor.commit()
    }

    fun setAlarmActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALARM_ACTIVE, active)
            .commit()
    }

    fun isAlarmActive(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALARM_ACTIVE, false)
    }

    fun getDestLat(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val longVal = prefs.getLong(KEY_DEST_LAT, 0L)
        return if (longVal != 0L) {
            java.lang.Double.longBitsToDouble(longVal)
        } else {
            prefs.getFloat("dest_lat", 0f).toDouble()
        }
    }

    fun getDestLng(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val longVal = prefs.getLong(KEY_DEST_LNG, 0L)
        return if (longVal != 0L) {
            java.lang.Double.longBitsToDouble(longVal)
        } else {
            prefs.getFloat("dest_lng", 0f).toDouble()
        }
    }

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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        list.distinct().take(10).forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_HISTORICO, jsonArray.toString()).apply()
    }

    fun getHistorico(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val list = mutableListOf<String>()
        try {
            val jsonStr = prefs.getString(KEY_HISTORICO, "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: ClassCastException) {
            try {
                @Suppress("UNCHECKED_CAST")
                val oldSet = prefs.getStringSet(KEY_HISTORICO, emptySet())
                if (!oldSet.isNullOrEmpty()) {
                    list.addAll(oldSet)
                    saveHistorico(context, list)
                }
            } catch (ex: Exception) {
                prefs.edit().remove(KEY_HISTORICO).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
