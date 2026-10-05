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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Serviço em primeiro plano responsável por monitorar a localização do dispositivo em segundo plano,
 * calcular a distância até o destino e acionar o alarme quando o usuário estiver próximo.
 */
class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var notificationManager: NotificationManager

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createTrackingNotification("Monitorando sua localização...")
        startForeground(NOTIFICATION_ID, notification)
        startLocationTracking()
        return START_STICKY
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Canal de monitoramento
            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION_ID,
                "Monitoramento de Localização",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal para o serviço de monitoramento de localização"
            }

            // Canal de alarme disparado (Prioridade Alta)
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "Disparo do Alarme",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificação enviada quando o alarme de proximidade dispara"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(locationChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    /**
     * Cria a notificação de monitoramento contínuo com um botão direto para desativar o alarme.
     */
    private fun createTrackingNotification(statusText: String): Notification {
        val stopIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = "com.example.alarmbus.ACTION_STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_LOCATION_ID)
            .setContentTitle("Alarme de Destino Ativo")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "DESATIVAR ALARME",
                stopPendingIntent
            )
            .build()
    }

    /**
     * Atualiza para uma notificação de alta prioridade quando o alarme estiver disparando.
     */
    private fun showAlarmTriggeredNotification(distancia: Int) {
        val stopIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            action = "com.example.alarmbus.ACTION_STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmActivityIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val alarmActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALARM_ID)
            .setContentTitle("🚨 CHEGANDO AO DESTINO!")
            .setContentText("Você está a $distancia m do seu destino!")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(alarmActivityPendingIntent, true)
            .setContentIntent(alarmActivityPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "DESATIVAR ALARME",
                stopPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startLocationTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
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

        val distancia = location.distanceTo(destino)

        // Envia broadcast para a MainActivity atualizar a interface
        val updateIntent = Intent("LOCATION_UPDATE").apply {
            putExtra("latitude", location.latitude)
            putExtra("longitude", location.longitude)
            putExtra("distancia", distancia)
            setPackage(packageName)
        }
        sendBroadcast(updateIntent)

        if (AlarmPlayer.isRinging()) {
            showAlarmTriggeredNotification(distancia.toInt())
        } else {
            val status = "Destino a ${distancia.toInt()} m"
            val notification = createTrackingNotification(status)
            notificationManager.notify(NOTIFICATION_ID, notification)

            if (distancia < alertDist) {
                val toneUri = AlarmPreferences.getToneUri(this)
                val vibrate = AlarmPreferences.isVibrationActive(this)
                val volLevel = AlarmPreferences.getVolumeLevel(this)

                AlarmPlayer.startAlarm(
                    context = this,
                    ringtoneUri = toneUri,
                    vibrate = vibrate,
                    volumeLevelPercent = volLevel
                )
                showAlarmTriggeredNotification(distancia.toInt())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        AlarmPlayer.stopAlarm(this)
    }
}
