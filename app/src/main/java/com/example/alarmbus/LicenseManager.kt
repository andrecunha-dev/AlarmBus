package com.example.alarmbus

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LicenseManager {

    // 💰 VALOR PADRÃO MENSAL DA ASSINATURA (Mínimo R$ 5,00)
    const val MONTHLY_PRICE = 7.90

    private const val PREFS_NAME = "alarmbus_license_prefs"
    private const val KEY_EXPIRATION_TIME = "license_expiration_time"
    private const val KEY_PROMO_PRICE = "admin_promo_price"
    private const val KEY_PROMO_DAYS = "admin_promo_days"
    private const val KEY_IS_ADMIN = "is_developer_admin"
    private const val KEY_FREE_TRIAL_USED = "free_trial_alarm_used"

    // Credenciais de Administrador Desenvolvedor
    const val ADMIN_NAME = "Andre3097"
    const val ADMIN_CPF = "33388015805"

    // 🎁 Os 7 códigos promocionais de teste para presente (60 dias cada)
    val PROMO_CODES_60_DAYS = setOf("3301", "3302", "3303", "3304", "3305", "3306", "3307")

    private val ASAAS_API_TOKEN by lazy {
        val h = "24616163745F70726F645F3030304D7a6B774F4441324D5759324F474D334D57526C4D4455324E574D334D7a4A6c4E7a5A6d4E475A685A4759364F6D526A593249325A5442694C5751354F5445744E47566c4E6930344D44466c4C54557a4E5467794E4463315A4445354D546F364A474668593268664D3255784E324D7a4D444D744E4745334E6930304F5751794C5749795A6A55744D7a45794D7a6B305A5449784D7a4D79"
        val b = ByteArray(h.length / 2)
        for (i in 0 until h.length step 2) {
            b[i / 2] = h.substring(i, i + 2).toInt(16).toByte()
        }
        String(b, Charsets.UTF_8).trim()
    }

    private const val ASAAS_BASE_URL = "https://www.asaas.com/api/v3"

    fun registerInstallationOnAsaas(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("app_installation_registered", false)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = getDeviceId(context)
                val searchDevUrl = URL("$ASAAS_BASE_URL/customers?externalReference=$deviceId")
                val searchConn = (searchDevUrl.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (searchConn.responseCode == 200) {
                    val res = searchConn.inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(res).optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        prefs.edit().putBoolean("app_installation_registered", true).apply()
                        return@launch
                    }
                }

                val createUrl = URL("$ASAAS_BASE_URL/customers")
                val createConn = (createUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                }
                val body = JSONObject().apply {
                    put("name", "Instalacao - Dispositivo $deviceId")
                    put("cpfCnpj", "33388015805")
                    put("externalReference", deviceId)
                    put("observations", "Instalado em ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                }
                createConn.outputStream.bufferedWriter().use { it.write(body.toString()) }
                if (createConn.responseCode == 200) {
                    prefs.edit().putBoolean("app_installation_registered", true).apply()
                }
            } catch (e: Exception) {
                Log.e("LicenseManager", "Erro ao registrar instalacao no Asaas", e)
            }
        }
    }

    fun hasFreeTrialAvailable(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_FREE_TRIAL_USED, false)
    }

    fun consumeFreeTrial(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FREE_TRIAL_USED, true).commit()
    }

    fun isPromoCodeAvailable(context: Context, code: String): Boolean {
        val cleanCode = code.trim().replace("[^0-9]".toRegex(), "")
        if (!PROMO_CODES_60_DAYS.contains(cleanCode)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean("promo_used_$cleanCode", false)
    }

    fun redeemPromoCode(context: Context, code: String, deviceId: String): Boolean {
        val cleanCode = code.trim().replace("[^0-9]".toRegex(), "")
        if (!PROMO_CODES_60_DAYS.contains(cleanCode)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("promo_used_$cleanCode", false)) return false

        prefs.edit()
            .putBoolean("promo_used_$cleanCode", true)
            .putString("promo_device_$cleanCode", deviceId)
            .apply()

        grantCustomLicense(context, 60)
        return true
    }

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
        val price = prefs.getFloat(KEY_PROMO_PRICE, 7.90f).toDouble()
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val searchUrl = URL("$ASAAS_BASE_URL/customers?externalReference=ADMIN_PROMO_CONFIG")
                val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(res).optJSONArray("data")
                    val jsonString = JSONObject().apply {
                        put("price", price.coerceAtLeast(5.00))
                        put("days", days)
                    }.toString()

                    if (data != null && data.length() > 0) {
                        val cusId = data.getJSONObject(0).getString("id")
                        val updateUrl = URL("$ASAAS_BASE_URL/customers/$cusId")
                        val updateConn = (updateUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("access_token", ASAAS_API_TOKEN)
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("User-Agent", "AlarmBus/1.0")
                            doOutput = true
                        }
                        val body = JSONObject().apply { put("observations", jsonString) }
                        updateConn.outputStream.bufferedWriter().use { it.write(body.toString()) }
                        updateConn.responseCode
                    } else {
                        val createUrl = URL("$ASAAS_BASE_URL/customers")
                        val createConn = (createUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("access_token", ASAAS_API_TOKEN)
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("User-Agent", "AlarmBus/1.0")
                            doOutput = true
                        }
                        val body = JSONObject().apply {
                            put("name", "Configuracao Promocional AlarmBus")
                            put("externalReference", "ADMIN_PROMO_CONFIG")
                            put("observations", jsonString)
                        }
                        createConn.outputStream.bufferedWriter().use { it.write(body.toString()) }
                        createConn.responseCode
                    }
                }
            } catch (e: Exception) {
                Log.e("LicenseManager", "Erro ao salvar config no Asaas", e)
            }
        }
    }

    suspend fun fetchGlobalPromoConfig(context: Context): Pair<Double, Int> {
        return withContext(Dispatchers.IO) {
            try {
                registerInstallationOnAsaas(context)

                val url = URL("$ASAAS_BASE_URL/customers?externalReference=ADMIN_PROMO_CONFIG")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(jsonText).optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val obs = data.getJSONObject(0).optString("observations", "")
                        if (obs.isNotBlank()) {
                            val obj = JSONObject(obs)
                            val price = obj.optDouble("price", 7.90).coerceAtLeast(5.00)
                            val days = obj.optInt("days", 30)

                            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putFloat(KEY_PROMO_PRICE, price.toFloat())
                                .putInt(KEY_PROMO_DAYS, days)
                                .apply()

                            return@withContext Pair(price, days)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("LicenseManager", "Aviso ao buscar oferta no Asaas", e)
            }
            Pair(getPromoPrice(context), getPromoDays(context))
        }
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
    }

    fun isPaidLicenseActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expirationTime = prefs.getLong(KEY_EXPIRATION_TIME, 0L)
        return System.currentTimeMillis() < expirationTime
    }

    fun isLicenseActive(context: Context): Boolean {
        if (isPaidLicenseActive(context)) return true
        return hasFreeTrialAvailable(context)
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

    data class AdminCustomerStats(
        val totalInstallations: Int,
        val activeCount: Int,
        val freeTrialCount: Int,
        val customerList: List<AdminCustomerItem>
    )

    data class AdminCustomerItem(
        val id: String,
        val name: String,
        val cpfCnpj: String,
        val dateCreated: String,
        val statusText: String
    )

    suspend fun fetchAdminCustomerStats(): AdminCustomerStats {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$ASAAS_BASE_URL/customers?limit=100")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("access_token", ASAAS_API_TOKEN)
                    setRequestProperty("User-Agent", "AlarmBus/1.0")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(jsonText)
                    val data = obj.optJSONArray("data")

                    val items = mutableListOf<AdminCustomerItem>()
                    var totalInstallations = 0
                    var activeCount = 0
                    var freeTrialCount = 0

                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val item = data.getJSONObject(i)
                            val ref = item.optString("externalReference", "")
                            if (ref == "ADMIN_PROMO_CONFIG") continue

                            totalInstallations++

                            val name = item.optString("name", "Dispositivo Android")
                            val cpf = item.optString("cpfCnpj", "Sem CPF")
                            val date = item.optString("dateCreated", "")
                            val id = item.optString("id", "")

                            val isPaid = cpf.isNotBlank() && cpf != "33388015805" && cpf != "Sem CPF"
                            val status = if (isPaid) "🟢 ATIVO (PAGO)" else "⚪ TESTE GRATUITO"

                            if (isPaid) activeCount++ else freeTrialCount++

                            items.add(
                                AdminCustomerItem(
                                    id = id,
                                    name = name,
                                    cpfCnpj = if (isPaid) cpf else "Dispositivo Registrado",
                                    dateCreated = date,
                                    statusText = status
                                )
                            )
                        }
                    }
                    return@withContext AdminCustomerStats(
                        totalInstallations = totalInstallations,
                        activeCount = activeCount,
                        freeTrialCount = freeTrialCount,
                        customerList = items
                    )
                }
            } catch (e: Exception) {
                Log.e("LicenseManager", "Erro ao buscar relatorio de clientes", e)
            }
            AdminCustomerStats(0, 0, 0, emptyList())
        }
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
            }
        } catch (e: Exception) {
            Log.e("LicenseManager", "Erro ao buscar por CPF", e)
        }

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
            }
        } catch (e: Exception) {
            Log.e("LicenseManager", "Erro ao buscar por deviceId", e)
        }

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
            }
        } catch (e: Exception) {
            Log.e("LicenseManager", "Erro fatal ao criar cliente Asaas", e)
        }

        return "cus_000207424432"
    }

    private fun getTomorrowDate(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(calendar.time)
    }
}
