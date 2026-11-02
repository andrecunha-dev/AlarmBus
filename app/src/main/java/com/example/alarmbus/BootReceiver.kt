package com.example.alarmbus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver ativado quando o celular reinicia (Boot Completed).
 * Reagenda automaticamente todas as rotinas ativas para garantir que nenhum alarme seja perdido.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            try {
                Log.d("BootReceiver", "Celular reiniciado. Reagendando rotinas do AlarmBus...")
                RoutineManager.rescheduleAllRoutines(context)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Erro ao reagendar rotinas no boot", e)
            }
        }
    }
}
