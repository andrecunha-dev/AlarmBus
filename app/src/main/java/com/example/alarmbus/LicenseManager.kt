package com.example.alarmbus

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LicenseManager {

    // 💰 EDITE O VALOR MENSAL DA ASSINATURA AQUI (Ex: 4.90, 2.90, 3.90, 5.00)
    const val MONTHLY_PRICE = 4.90

    private val ASAAS_API_TOKEN by lazy {
        val b64 = "JGFhY3RfcHJvZF8wMDBNemt3T0RBMk1XWTJPR00zTVdSbE1EVTJOV016TXpKbE56Wm1OR1poWkdZNk9tUmpZMkkyWlRCaUxXUTVPVEV0TkdWbE5pMDRNREZsTFRVek5UZ3lORGMxWkRFNU1UbzZKR0ZoWTJoZk0yVXhOMk16TURNdE5HRTNOaTAwT1dReUxXSXlaalV0TXpFeU16azBaVEl4TXpNeQ=="
        String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8).trim()
    }

    private const val ASAAS_BASE_URL = "https://www.asaas.com/api/v3"

    private const val PREFS_NAME = "alarmbus_license_prefs"
    private const val KEY_EXPIRATION_TIME = "license_expiration_time"

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
    }

    fun isLicenseActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expirationTime = prefs.getLong(KEY_EXPIRATION_TIME, 0L)
        return System.currentTimeMillis() < expirationTime
    }

    fun getExpirationDaysRemaining(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expirationTime = prefs.getLong(KEY_EXPIRATION_TIME, 0L)
        val remainingMillis = expirationTime - System.currentTimeMillis()
        return if (remainingMillis > 0) (remainingMillis / (1000 * 60 * 60 * 24)).toInt() else 0
    }

    fun grant30DaysLicense(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val futureTime = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        prefs.edit().putLong(KEY_EXPIRATION_TIME, futureTime).apply()
    }

    data class PixPaymentResult(
        val paymentId: String,
        val qrCodeBase64: String,
        val copyAndPasteCode: String,
        val value: Double
    )

    suspend fun createPixPayment(
        context: Context,
        customerName: String,
        cpfCnpj: String,
        monthlyValue: Double = MONTHLY_PRICE
    ): PixPaymentResult? {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId(context)

                val customerId = getOrCreateCustomer(customerName, cpfCnpj, deviceId) ?: return@withContext null

                val paymentUrl = URL("$ASAAS_BASE_URL/payments")
                val conn = (paymentUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }

                val paymentJson = JSONObject().apply {
                    put("customer", customerId)
                    put("billingType", "PIX")
                    put("value", monthlyValue)
                    put("dueDate", getTomorrowDate())
                    put("description", "Assinatura Mensal AlarmBus GPS - Dispositivo $deviceId")
                }

                conn.outputStream.bufferedWriter().use { it.write(paymentJson.toString()) }

                if (conn.responseCode != 200) {
                    Log.e("LicenseManager", "Erro ao criar cobrança Asaas: ${conn.responseCode}")
                    return@withContext null
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val paymentObj = JSONObject(responseText)
                val paymentId = paymentObj.getString("id")

                val qrCodeUrl = URL("$ASAAS_BASE_URL/payments/$paymentId/pixQrCode")
                val qrConn = (qrCodeUrl.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                }

                if (qrConn.responseCode == 200) {
                    val qrResponse = qrConn.inputStream.bufferedReader().use { it.readText() }
                    val qrObj = JSONObject(qrResponse)
                    val qrCodeBase64 = qrObj.optString("encodedImage", "")
                    val copyPaste = qrObj.optString("payload", "")

                    return@withContext PixPaymentResult(
                        paymentId = paymentId,
                        qrCodeBase64 = qrCodeBase64,
                        copyAndPasteCode = copyPaste,
                        value = monthlyValue
                    )
                }
            } catch (e: Exception) {
                Log.e("LicenseManager", "Erro ao processar Pix Asaas", e)
            }
            null
        }
    }

    suspend fun checkPaymentStatus(paymentId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$ASAAS_BASE_URL/payments/$paymentId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                }
                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(jsonText)
                    return@withContext obj.optString("status", "PENDING")
                }
            } catch (e: Exception) {
                Log.e("LicenseManager", "Erro ao verificar status do pagamento", e)
            }
            "PENDING"
        }
    }

    private fun getOrCreateCustomer(name: String, cpfCnpj: String, deviceId: String): String? {
        try {
            val url = URL("$ASAAS_BASE_URL/customers")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("access_token", ASAAS_API_TOKEN)
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            val cleanCpf = cpfCnpj.replace("[^0-9]".toRegex(), "")
            val customerJson = JSONObject().apply {
                put("name", if (name.isNotBlank()) name else "Passageiro AlarmBus")
                if (cleanCpf.length == 11 || cleanCpf.length == 14) {
                    put("cpfCnpj", cleanCpf)
                }
                put("externalReference", deviceId)
            }

            conn.outputStream.bufferedWriter().use { it.write(customerJson.toString()) }

            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(res)
                return obj.getString("id")
            }
        } catch (e: Exception) {
            Log.e("LicenseManager", "Erro ao criar cliente Asaas", e)
        }
        return null
    }

    private fun getTomorrowDate(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(calendar.time)
    }
}
