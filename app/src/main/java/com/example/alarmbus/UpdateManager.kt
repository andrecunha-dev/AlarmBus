package com.example.alarmbus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    const val CURRENT_VERSION_CODE = 30
    const val CURRENT_VERSION_NAME = "4.6"

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersionCode: Int,
        val latestVersionName: String,
        val apkDownloadUrl: String,
        val updateNotes: String
    )

    suspend fun checkForUpdates(context: Context): UpdateInfo {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/andrecunha-dev/AlarmBus/main/promo_config.json")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(jsonText)

                    val latestVersionCode = obj.optInt("latestVersionCode", CURRENT_VERSION_CODE)
                    val latestVersionName = obj.optString("latestVersionName", CURRENT_VERSION_NAME)
                    val apkDownloadUrl = obj.optString(
                        "apkDownloadUrl",
                        "https://github.com/andrecunha-dev/AlarmBus/releases/latest"
                    )
                    val updateNotes = obj.optString(
                        "updateNotes",
                        "Nova versão disponível com melhorias de desempenho e rotas."
                    )

                    val isUpdateAvailable = latestVersionCode > CURRENT_VERSION_CODE

                    return@withContext UpdateInfo(
                        isUpdateAvailable = isUpdateAvailable,
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        apkDownloadUrl = apkDownloadUrl,
                        updateNotes = updateNotes
                    )
                }
            } catch (e: Exception) {
                Log.w("UpdateManager", "Erro ao verificar atualizações", e)
            }
            UpdateInfo(false, CURRENT_VERSION_CODE, CURRENT_VERSION_NAME, "", "")
        }
    }

    fun openApkDownloadLink(context: Context, downloadUrl: String) {
        try {
            val url = if (downloadUrl.isNotBlank()) downloadUrl else "https://github.com/andrecunha-dev/AlarmBus/releases/latest"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Erro ao abrir link de download", e)
        }
    }
}
