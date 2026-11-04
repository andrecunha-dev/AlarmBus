package com.example.alarmbus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class RoutineAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra("routine_id") ?: return
        val routine = RoutineManager.getRoutines(context).find { it.id == routineId } ?: return

        if (!routine.enabled) return

        try {
            Log.d("RoutineAlarmReceiver", "Ativando rotina programada: ${routine.name}")

            AlarmPreferences.saveAlarmConfig(
                context = context,
                lat = routine.destLat,
                lng = routine.destLng,
                address = routine.destAddress,
                dist = routine.alertDist,
                vibration = AlarmPreferences.isVibrationActive(context),
                volLevel = AlarmPreferences.getVolumeLevel(context),
                toneUri = AlarmPreferences.getToneUri(context),
                active = true
            )

            if (!routine.itineraryId.isNullOrBlank()) {
                ItineraryManager.setActiveItineraryId(context, routine.itineraryId)
            }

            // Inicia o serviço de localização em primeiro plano
            try {
                val serviceIntent = Intent(context, LocationForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.w("RoutineAlarmReceiver", "Aviso ao iniciar FGS em segundo plano: ${e.message}")
            }

            // Reagenda a próxima execução da rotina
            RoutineManager.scheduleRoutineAlarm(context, routine)

            // Dispara a tela e som do alarme
            val toneUri = AlarmPreferences.getToneUri(context)
            val vibrate = AlarmPreferences.isVibrationActive(context)
            val volLevel = AlarmPreferences.getVolumeLevel(context)
            AlarmPlayer.startAlarm(context, toneUri, vibrate, volLevel)

        } catch (e: Exception) {
            Log.e("RoutineAlarmReceiver", "Erro ao executar Broadcast Receiver da rotina", e)
        }
    }
}
