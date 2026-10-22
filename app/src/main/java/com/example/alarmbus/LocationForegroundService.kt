package com.example.alarmbus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Serviço em primeiro plano responsável por monitorar a localização do dispositivo em segundo plano,
 * manter a CPU ativa com WakeLock para evitar travamentos pelo Modo Doze e acionar o alarme.
 */
class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_LOCATION_ID = "location_channel"
        const val CHANNEL_ALARM_ID = "alarm_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()

        // Adquire WakeLock parcial para impedi que o Android congele o GPS no bolso (Modo Doze)
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmBus::GPSWakeLock").apply {
                acquire(120 * 60 * 1000L) // Mantém CPU acordada por até 2 horas
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Erro ao adquirir WakeLock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createTrackingNotification("Monitorando sua localização...")
        startForeground(NOTIFICATION_ID, notification)

        startLocationTracking()

        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION_ID,
                "Monitoramento de Localização",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de acompanhamento da distância até o destino"
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "Alerta de Chegada",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificação disparada ao chegar perto do destino"
                setSound(null, null)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(locationChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    private fun createTrackingNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_LOCATION_ID)
            .setContentTitle("AlarmBus GPS")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showAlarmTriggeredNotification(distanciaMetros: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = "PARAR_ALARME"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALARM_ID)
            .setContentTitle("🚨 VOCÊ ESTÁ CHEGANDO!")
            .setContentText("Destino a aproximadamente ${distanciaMetros}m. Prepare-se para descer.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(R.drawable.ic_launcher_foreground, "DESATIVAR ALARME", stopPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startLocationTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(0f)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                verificarProximidade(location)
            }
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun verificarProximidade(location: Location) {
        val destLat = AlarmPreferences.getDestLat(this)
        val destLng = AlarmPreferences.getDestLng(this)
        val alertDist = AlarmPreferences.getAlertDist(this)
        val active = AlarmPreferences.isAlarmActive(this)

        if (destLat == 0.0 || destLng == 0.0 || !active) return

        val destino = Location("").apply {
            latitude = destLat
            longitude = destLng
        }

        val straightDistance = location.distanceTo(destino)

        // Se estiver longe (> 2.5x o raio), aproxima por 1.35x. Se estiver próximo do raio de alerta, consulta a rota OSRM pelas ruas.
        if (straightDistance > alertDist * 2.5f) {
            val roadDistance = straightDistance * 1.35f
            processarDistanciaEAlarme(location, roadDistance, alertDist)
        } else {
            serviceScope.launch {
                val exactRoadDistance = fetchExactRoadDistance(
                    location.latitude, location.longitude,
                    destLat, destLng
                )
                processarDistanciaEAlarme(location, exactRoadDistance, alertDist)
            }
        }
    }

    private suspend fun fetchExactRoadDistance(startLat: Double, startLng: Double, destLat: Double, destLng: Double): Float {
        return withContext(Dispatchers.IO) {
            try {
                val urlString = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$destLng,$destLat?overview=false"
                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "AlarmBusGPS/1.0")
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(jsonText)
                    val routes = jsonObj.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val distMeters = routes.getJSONObject(0).getDouble("distance")
                        return@withContext distMeters.toFloat()
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Erro OSRM road distance", e)
            }
            val straight = FloatArray(1)
            Location.distanceBetween(startLat, startLng, destLat, destLng, straight)
            straight[0] * 1.35f
        }
    }

    private fun processarDistanciaEAlarme(location: Location, roadDistance: Float, alertDist: Int) {
        // Envia broadcast para a MainActivity atualizar a interface com a distância de rodagem real
        val updateIntent = Intent("LOCATION_UPDATE").apply {
            putExtra("latitude", location.latitude)
            putExtra("longitude", location.longitude)
            putExtra("distancia", roadDistance)
            setPackage(packageName)
        }
        sendBroadcast(updateIntent)

        if (AlarmPlayer.isRinging()) {
            showAlarmTriggeredNotification(roadDistance.toInt())
        } else {
            val status = "Destino a ${roadDistance.toInt()} m"
            val notification = createTrackingNotification(status)
            notificationManager.notify(NOTIFICATION_ID, notification)

            // Dispara o alarme apenas quando a distância real de rodagem pelas ruas/trajeto for menor que o raio
            if (roadDistance < alertDist) {
                val toneUri = AlarmPreferences.getToneUri(this)
                val vibrate = AlarmPreferences.isVibrationActive(this)
                val volLevel = AlarmPreferences.getVolumeLevel(this)

                AlarmPlayer.startAlarm(
                    context = this,
                    ringtoneUri = toneUri,
                    vibrate = vibrate,
                    volumeLevelPercent = volLevel
                )
                showAlarmTriggeredNotification(roadDistance.toInt())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Erro ao liberar WakeLock", e)
        }
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (::notificationManager.isInitialized) {
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancelAll()
        }
    }
}
