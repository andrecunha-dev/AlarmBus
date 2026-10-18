package com.example.alarmbus

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LicenseManager {

    // 💰 EDITE O VALOR MENSAL DA ASSINATURA AQUI (Mínimo R$ 5,00 exigido pelo Asaas)
    const val MONTHLY_PRICE = 5.00

    private const val PREFS_NAME = "alarmbus_license_prefs"
    private const val KEY_EXPIRATION_TIME = "license_expiration_time"
    private const val KEY_PROMO_PRICE = "admin_promo_price"
    private const val KEY_PROMO_DAYS = "admin_promo_days"
    private const val KEY_IS_ADMIN = "is_developer_admin"

    // Credenciais de Administrador Desenvolvedor
    const val ADMIN_NAME = "Andre3097"
    const val ADMIN_CPF = "33388015805"

    private val ASAAS_API_TOKEN by lazy {
        val hex = "24616163745F70726F645F3030304D7a6B774F4441324D5759324F474D334D57526C4D4455324E574D334D7a4A6c4E7A5A6D4E475A685A4759364F6D526A593249325A5442694C5751354F5445744E47566c4E6930344D44466c4C54557a4E5467794E4463315A4445354D546F364A474668593268664D3255784E324D7a4D444D744E4745334E6930304F5751794C5749795A6A55744D7A45794D7A6B305A5449784D7A4D79"
        val bytes = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        String(bytes, Charsets.UTF_8).trim()
    }

    private const val ASAAS_BASE_URL = "https://www.asaas.com/api/v3"

    fun isAdminUser(name: String, cpf: String): Boolean {
        val cleanCpf = cpf.replace("[^0-9]".toRegex(), "")
        return name.trim().equals(ADMIN_NAME, ignoreCase = true) && cleanCpf == ADMIN_CPF
    }

    fun setDeveloperAdmin(context: Context, isAdmin: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_ADMIN, isAdmin).apply()
    }

    fun isDeveloperAdmin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_ADMIN, false)
    }

    fun getPromoPrice(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val price = prefs.getFloat(KEY_PROMO_PRICE, 5.00f).toDouble()
        return price.coerceAtLeast(5.00)
    }

    fun getPromoDays(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PROMO_DAYS, 30)
    }

    fun saveAdminPromoSettings(context: Context, price: Double, days: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_PROMO_PRICE, price.coerceAtLeast(5.00).toFloat())
            .putInt(KEY_PROMO_DAYS, days)
            .apply()
    }

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

    fun grantCustomLicense(context: Context, days: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val futureTime = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000)
        prefs.edit().putLong(KEY_EXPIRATION_TIME, futureTime).apply()
    }

    fun grantDeveloperLifetimeLicense(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hundredYearsMillis = System.currentTimeMillis() + (100L * 365 * 24 * 60 * 60 * 1000)
        prefs.edit()
            .putLong(KEY_EXPIRATION_TIME, hundredYearsMillis)
            .putBoolean(KEY_IS_ADMIN, true)
            .apply()
    }

    data class PixPaymentResult(
        val paymentId: String,
        val qrCodeBase64: String,
        val copyAndPasteCode: String,
        val value: Double,
        val errorMessage: String? = null
    )

    suspend fun createPixPayment(
        context: Context,
        customerName: String,
        cpfCnpj: String,
        monthlyValue: Double,
        validityDays: Int
    ): PixPaymentResult? {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId(context)
                val finalValue = monthlyValue.coerceAtLeast(5.00)

                val customerId = getOrCreateCustomer(customerName, cpfCnpj, deviceId)

                val paymentUrl = URL("$ASAAS_BASE_URL/payments")
                val conn = (paymentUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                }

                val paymentJson = JSONObject().apply {
                    put("customer", customerId)
                    put("billingType", "PIX")
                    put("value", finalValue)
                    put("dueDate", getTomorrowDate())
                    put("description", "Acesso AlarmBus GPS ($validityDays dias) - Dispositivo $deviceId")
                }

                conn.outputStream.bufferedWriter().use { it.write(paymentJson.toString()) }

                if (conn.responseCode != 200) {
                    val errStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e("LicenseManager", "Erro Asaas ($conn.responseCode): $errStream")
                    val errMsg = try {
                        JSONObject(errStream).getJSONArray("errors").getJSONObject(0).getString("description")
                    } catch (e: Exception) {
                        "Erro de cobrança no Asaas ($conn.responseCode)"
                    }
                    return@withContext PixPaymentResult("", "", "", finalValue, errMsg)
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val paymentObj = JSONObject(responseText)
                val paymentId = paymentObj.getString("id")

                val qrCodeUrl = URL("$ASAAS_BASE_URL/payments/$paymentId/pixQrCode")
                val qrConn = (qrCodeUrl.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 8000
                    readTimeout = 8000
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
                        value = finalValue
                    )
                } else {
                    val errStream = qrConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val errMsg = try {
                        JSONObject(errStream).getJSONArray("errors").getJSONObject(0).getString("description")
                    } catch (e: Exception) {
                        "Cadastre uma chave Pix no painel do Asaas para liberar o QR Code."
                    }
                    return@withContext PixPaymentResult("", "", "", finalValue, errMsg)
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
                if (paymentId.isBlank()) return@withContext "PENDING"
                val url = URL("$ASAAS_BASE_URL/payments/$paymentId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 8000
                    readTimeout = 8000
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

    private fun getOrCreateCustomer(name: String, cpfCnpj: String, deviceId: String): String {
        val cleanCpf = cpfCnpj.replace("[^0-9]".toRegex(), "")
        val validCpf = if (cleanCpf.length == 11 || cleanCpf.length == 14) cleanCpf else "33388015805"
        val customerName = if (name.isNotBlank()) name.trim() else "Passageiro AlarmBus"

        // 1. Tenta buscar por CPF
        try {
            val searchUrl = URL("$ASAAS_BASE_URL/customers?cpfCnpj=$validCpf")
            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                setRequestProperty("access_token", ASAAS_API_TOKEN)
                setRequestProperty("User-Agent", "AlarmBus/1.0")
                connectTimeout = 6000
                readTimeout = 6000
            }
            val code = conn.responseCode
            if (code == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(res).optJSONArray("data")
                if (data != null && data.length() > 0) {
                    return data.getJSONObject(0).getString("id")
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("LicenseManager", "Search CPF error ($code): $err")
            }
        } catch (e: Exception) {
            Log.e("LicenseManager", "Erro ao buscar por CPF", e)
        }

        // 2. Tenta buscar por externalReference (deviceId)
        try {
            val searchDevUrl = URL("$ASAAS_BASE_URL/customers?externalReference=$deviceId")
            val conn = (searchDevUrl.openConnection() as HttpURLConnection).apply {
                setRequestProperty("access_token", ASAAS_API_TOKEN)
                setRequestProperty("User-Agent", "AlarmBus/1.0")
                connectTimeout = 6000
                readTimeout = 6000
            }
            val code = conn.responseCode
            if (code == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(res).optJSONArray("data")
                if (data != null && data.length() > 0) {
                    return data.getJSONObject(0).getString("id")
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e("LicenseManager", "Search device error ($code): $err")
            }
        } catch (e: Exception) {
            Log.e("LicenseManager", "Erro ao buscar por deviceId", e)
        }

        // 3. Cria novo cliente no Asaas
        try {
            val url = URL("$ASAAS_BASE_URL/customers")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("access_token", ASAAS_API_TOKEN)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "AlarmBus/1.0")
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
            }

            val customerJson = JSONObject().apply {
                put("name", customerName)
                put("cpfCnpj", validCpf)
                put("externalReference", deviceId)
            }

            conn.outputStream.bufferedWriter().use { it.write(customerJson.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                return JSONObject(res).getString("id")
            } else {
                val errStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e("LicenseManager", "Erro ao criar cliente Asaas ($code): $errStream")
            }
        } catch (e: Exception) {
            Log.e("LicenseManager", "Erro fatal ao criar cliente Asaas", e)
        }

        // 4. Fallback Mestre Garantido
        return "cus_000207424432"
    }

    private fun getTomorrowDate(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(calendar.time)
    }
}
