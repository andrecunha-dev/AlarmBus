package com.example.alarmbus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver responsável por capturar as ações de parada do alarme vindas da Notificação
 * ou da Sobreposição (Overlay).
 */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if ("com.example.alarmbus.ACTION_STOP_ALARM" == action || "PARAR_ALARME" == action) {
            AlarmPlayer.stopAlarm(context)
            
            // Para também o serviço de localização em primeiro plano se o alarme for desativado
            try {
                val serviceIntent = Intent(context, LocationForegroundService::class.java)
                context.stopService(serviceIntent)
            } catch (e: Exception) {
                // Ignora erro ao parar serviço
            }
        }
    }
}
