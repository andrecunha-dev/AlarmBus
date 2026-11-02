package com.example.alarmbus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receiver acionado pelo AlarmManager no horário exato da rotina programada.
 * Ativa automaticamente o monitoramento GPS para o destino configurado sem necessidade de intervenção do usuário.
 */
class RoutineAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra("routine_id") ?: return
        val routine = RoutineManager.getRoutines(context).find { it.id == routineId } ?: return

        if (!routine.enabled) return

        try {
            Log.d("RoutineAlarmReceiver", "Ativando rotina programada automaticamente: ${routine.name}")

            // 1. Configura as preferências do destino da rotina
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

            // 2. Inicia o serviço de monitoramento GPS em primeiro plano
            val serviceIntent = Intent(context, LocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 3. Notifica e reagenda a próxima execução da rotina para o próximo dia válido da semana
            RoutineManager.scheduleRoutineAlarm(context, routine)

            val updateIntent = Intent("LOCATION_UPDATE").apply {
                putExtra("latitude", routine.destLat)
                putExtra("longitude", routine.destLng)
                setPackage(context.packageName)
            }
            context.sendBroadcast(updateIntent)

        } catch (e: Exception) {
            Log.e("RoutineAlarmReceiver", "Erro ao executar rotina programada", e)
        }
    }
}
