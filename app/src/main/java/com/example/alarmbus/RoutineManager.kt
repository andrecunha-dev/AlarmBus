package com.example.alarmbus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ScheduledRoutine(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: List<Int>, // 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
    val destLat: Double,
    val destLng: Double,
    val destAddress: String,
    val alertDist: Int,
    val itineraryId: String? = null
)

object RoutineManager {

    private const val PREFS_NAME = "alarmbus_routines_prefs"
    private const val KEY_ROUTINES_JSON = "routines_list_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getRoutines(context: Context): List<ScheduledRoutine> {
        val jsonStr = getPrefs(context).getString(KEY_ROUTINES_JSON, "[]") ?: "[]"
        val list = mutableListOf<ScheduledRoutine>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val enabled = obj.optBoolean("enabled", true)
                val hour = obj.getInt("hour")
                val minute = obj.getInt("minute")

                val daysArray = obj.optJSONArray("daysOfWeek") ?: JSONArray()
                val days = mutableListOf<Int>()
                for (j in 0 until daysArray.length()) {
                    days.add(daysArray.getInt(j))
                }

                val destLat = obj.getDouble("destLat")
                val destLng = obj.getDouble("destLng")
                val destAddress = obj.optString("destAddress", "Destino Salvo")
                val alertDist = obj.optInt("alertDist", 300)
                val itineraryId = obj.optString("itineraryId", null)

                list.add(
                    ScheduledRoutine(
                        id = id,
                        name = name,
                        enabled = enabled,
                        hour = hour,
                        minute = minute,
                        daysOfWeek = days,
                        destLat = destLat,
                        destLng = destLng,
                        destAddress = destAddress,
                        alertDist = alertDist,
                        itineraryId = itineraryId
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveRoutine(context: Context, routine: ScheduledRoutine) {
        val current = getRoutines(context).toMutableList()
        current.removeAll { it.id == routine.id }
        current.add(0, routine)
        saveAllRoutines(context, current)

        scheduleRoutineAlarm(context, routine)
    }

    fun toggleRoutine(context: Context, id: String, enabled: Boolean) {
        val current = getRoutines(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(enabled = enabled)
            current[index] = updated
            saveAllRoutines(context, current)

            if (enabled) {
                scheduleRoutineAlarm(context, updated)
            } else {
                cancelRoutineAlarm(context, updated.id)
            }
        }
    }

    fun deleteRoutine(context: Context, id: String) {
        val current = getRoutines(context).toMutableList()
        current.removeAll { it.id == id }
        saveAllRoutines(context, current)
        cancelRoutineAlarm(context, id)
    }

    fun rescheduleAllRoutines(context: Context) {
        val routines = getRoutines(context)
        for (r in routines) {
            if (r.enabled) {
                scheduleRoutineAlarm(context, r)
            }
        }
    }

    private fun saveAllRoutines(context: Context, list: List<ScheduledRoutine>) {
        val array = JSONArray()
        for (r in list) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("enabled", r.enabled)
                put("hour", r.hour)
                put("minute", r.minute)

                val daysArray = JSONArray()
                r.daysOfWeek.forEach { daysArray.put(it) }
                put("daysOfWeek", daysArray)

                put("destLat", r.destLat)
                put("destLng", r.destLng)
                put("destAddress", r.destAddress)
                put("alertDist", r.alertDist)
                put("itineraryId", r.itineraryId ?: "")
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ROUTINES_JSON, array.toString()).commit()
    }

    fun scheduleRoutineAlarm(context: Context, routine: ScheduledRoutine) {
        if (!routine.enabled || routine.daysOfWeek.isEmpty()) {
            cancelRoutineAlarm(context, routine.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTimeMillis = getNextTriggerTimeMillis(routine.hour, routine.minute, routine.daysOfWeek)

        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            putExtra("routine_id", routine.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            routine.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("RoutineManager", "Rotina '${routine.name}' agendada com setAlarmClock para: ${java.util.Date(triggerTimeMillis)}")
        } catch (e: Exception) {
            Log.e("RoutineManager", "Erro ao agendar rotina via setAlarmClock", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e("RoutineManager", "Erro fallback agendamento rotina", ex)
            }
        }
    }

    fun cancelRoutineAlarm(context: Context, routineId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, RoutineAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            routineId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun getNextTriggerTimeMillis(hour: Int, minute: Int, daysOfWeek: List<Int>): Long {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMin = now.get(Calendar.MINUTE)

        // Se for o mesmo dia e minuto do teste atual, agenda para daqui a 3 segundos no futuro
        if (daysOfWeek.contains(currentDay) && hour == currentHour && minute == currentMin) {
            return now.timeInMillis + 3000L
        }

        var target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Procura o próximo dia e horário válido
        for (i in 0..7) {
            val dayOfWeek = target.get(Calendar.DAY_OF_WEEK)
            if (daysOfWeek.contains(dayOfWeek) && target.timeInMillis > now.timeInMillis) {
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis
    }

    fun formatDaysOfWeek(days: List<Int>): String {
        if (days.size == 7) return "Todos os dias"
        if (days.containsAll(listOf(2, 3, 4, 5, 6)) && days.size == 5) return "Seg a Sex"
        if (days.containsAll(listOf(1, 7)) && days.size == 2) return "Fim de semana"

        val names = mapOf(
            1 to "Dom", 2 to "Seg", 3 to "Ter", 4 to "Qua",
            5 to "Qui", 6 to "Sex", 7 to "Sáb"
        )
        return days.sorted().mapNotNull { names[it] }.joinToString(", ")
    }
}
