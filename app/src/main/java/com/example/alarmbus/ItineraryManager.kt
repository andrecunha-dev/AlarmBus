package com.example.alarmbus

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

data class LatLngData(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class Itinerary(
    val id: String,
    val name: String,
    val startAddress: String,
    val endAddress: String,
    val waypoints: List<LatLngData>,
    val dateCreated: String,
    val totalDistanceMeters: Double = 0.0,
    val durationSeconds: Long = 0L
)

object ItineraryManager {

    private const val PREFS_NAME = "alarmbus_itineraries"
    private const val KEY_ITINERARY_LIST = "itinerary_list_json"
    private const val KEY_ACTIVE_ITINERARY_ID = "active_itinerary_id"
    private const val KEY_IS_RECORDING = "is_recording_itinerary"
    private const val KEY_RECORDING_NAME = "recording_itinerary_name"
    private const val KEY_RECORDING_WAYPOINTS = "recording_waypoints_json"
    private const val KEY_RECORDING_START_TIME = "recording_start_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isRecording(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_RECORDING, false)
    }

    fun startLiveRecording(context: Context, name: String) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_RECORDING, true)
            .putString(KEY_RECORDING_NAME, name)
            .putString(KEY_RECORDING_WAYPOINTS, "[]")
            .putLong(KEY_RECORDING_START_TIME, System.currentTimeMillis())
            .apply()
    }

    fun addLiveWaypoint(context: Context, lat: Double, lng: Double) {
        if (!isRecording(context)) return
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_RECORDING_WAYPOINTS, "[]") ?: "[]"
        try {
            val array = JSONArray(jsonStr)
            if (array.length() > 0) {
                val lastObj = array.getJSONObject(array.length() - 1)
                val lastLat = lastObj.getDouble("lat")
                val lastLng = lastObj.getDouble("lng")
                val dist = FloatArray(1)
                android.location.Location.distanceBetween(lastLat, lastLng, lat, lng, dist)
                if (dist[0] < 10f) return
            }

            val newWp = JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
                put("ts", System.currentTimeMillis())
            }
            array.put(newWp)
            prefs.edit().putString(KEY_RECORDING_WAYPOINTS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAndSaveLiveRecording(context: Context): Itinerary? {
        if (!isRecording(context)) return null
        val prefs = getPrefs(context)
        val name = prefs.getString(KEY_RECORDING_NAME, "Fretado Empresa → Casa") ?: "Fretado Empresa → Casa"
        val jsonStr = prefs.getString(KEY_RECORDING_WAYPOINTS, "[]") ?: "[]"
        val startTime = prefs.getLong(KEY_RECORDING_START_TIME, System.currentTimeMillis())
        val durationSec = (System.currentTimeMillis() - startTime) / 1000L

        prefs.edit()
            .putBoolean(KEY_IS_RECORDING, false)
            .remove(KEY_RECORDING_WAYPOINTS)
            .remove(KEY_RECORDING_START_TIME)
            .apply()

        val waypoints = mutableListOf<LatLngData>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                waypoints.add(
                    LatLngData(
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lng"),
                        timestamp = obj.optLong("ts", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (waypoints.isEmpty()) return null

        val totalDist = calculateTotalDistance(waypoints)
        val itinerary = Itinerary(
            id = System.currentTimeMillis().toString(),
            name = name,
            startAddress = "Ponto Inicial Registrado",
            endAddress = "Ponto Final Registrado",
            waypoints = waypoints,
            dateCreated = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date()),
            totalDistanceMeters = totalDist,
            durationSeconds = durationSec
        )

        saveItinerary(context, itinerary)
        setActiveItineraryId(context, itinerary.id)
        return itinerary
    }

    fun getItineraries(context: Context): List<Itinerary> {
        val jsonString = getPrefs(context).getString(KEY_ITINERARY_LIST, "[]") ?: "[]"
        val list = mutableListOf<Itinerary>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val startAddress = obj.optString("startAddress", "Origem Registrada")
                val endAddress = obj.optString("endAddress", "Destino Registrado")
                val dateCreated = obj.optString("dateCreated", "")
                val totalDist = obj.optDouble("totalDistanceMeters", 0.0)
                val durationSec = obj.optLong("durationSeconds", 0L)

                val waypointsArray = obj.optJSONArray("waypoints") ?: JSONArray()
                val waypoints = mutableListOf<LatLngData>()
                for (j in 0 until waypointsArray.length()) {
                    val wObj = waypointsArray.getJSONObject(j)
                    waypoints.add(
                        LatLngData(
                            latitude = wObj.getDouble("lat"),
                            longitude = wObj.getDouble("lng"),
                            timestamp = wObj.optLong("ts", System.currentTimeMillis())
                        )
                    )
                }

                list.add(
                    Itinerary(
                        id = id,
                        name = name,
                        startAddress = startAddress,
                        endAddress = endAddress,
                        waypoints = waypoints,
                        dateCreated = dateCreated,
                        totalDistanceMeters = if (totalDist > 0.0) totalDist else calculateTotalDistance(waypoints),
                        durationSeconds = durationSec
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveItinerary(context: Context, itinerary: Itinerary) {
        val currentList = getItineraries(context).toMutableList()
        currentList.removeAll { it.id == itinerary.id }
        currentList.add(0, itinerary)

        saveAllItineraries(context, currentList)
    }

    fun renameItinerary(context: Context, id: String, newName: String) {
        val currentList = getItineraries(context).toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = currentList[index]
            currentList[index] = old.copy(name = newName)
            saveAllItineraries(context, currentList)
        }
    }

    fun deleteItinerary(context: Context, id: String) {
        val currentList = getItineraries(context).toMutableList()
        currentList.removeAll { it.id == id }
        saveAllItineraries(context, currentList)

        if (getActiveItineraryId(context) == id) {
            setActiveItineraryId(context, null)
        }
    }

    fun getActiveItineraryId(context: Context): String? {
        return getPrefs(context).getString(KEY_ACTIVE_ITINERARY_ID, null)
    }

    fun setActiveItineraryId(context: Context, id: String?) {
        getPrefs(context).edit().putString(KEY_ACTIVE_ITINERARY_ID, id).apply()
    }

    fun getActiveItinerary(context: Context): Itinerary? {
        val activeId = getActiveItineraryId(context) ?: return null
        return getItineraries(context).find { it.id == activeId }
    }

    private fun saveAllItineraries(context: Context, list: List<Itinerary>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("startAddress", item.startAddress)
                put("endAddress", item.endAddress)
                put("dateCreated", item.dateCreated)
                put("totalDistanceMeters", item.totalDistanceMeters)
                put("durationSeconds", item.durationSeconds)

                val waypointsArray = JSONArray()
                for (wp in item.waypoints) {
                    val wObj = JSONObject().apply {
                        put("lat", wp.latitude)
                        put("lng", wp.longitude)
                        put("ts", wp.timestamp)
                    }
                    waypointsArray.put(wObj)
                }
                put("waypoints", waypointsArray)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ITINERARY_LIST, array.toString()).apply()
    }

    fun calculateTotalDistance(waypoints: List<LatLngData>): Double {
        if (waypoints.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until waypoints.size - 1) {
            total += distanceBetween(waypoints[i], waypoints[i + 1])
        }
        return total
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(Locale("pt", "BR"), "%.3f km", meters / 1000.0)
        } else {
            "${meters.roundToInt()} m"
        }
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "--"
        val mins = seconds / 60
        val hrs = mins / 60
        val remMins = mins % 60
        return if (hrs > 0) "${hrs}h ${remMins}min" else "${remMins}min"
    }

    fun calculateRemainingDistanceAlongItinerary(
        currentLocation: LatLngData,
        itinerary: Itinerary
    ): Double {
        val waypoints = itinerary.waypoints
        if (waypoints.isEmpty()) return 0.0

        var closestIndex = 0
        var minDistance = Double.MAX_VALUE

        for (i in waypoints.indices) {
            val dist = distanceBetween(currentLocation, waypoints[i])
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = i
            }
        }

        var totalRemaining = 0.0
        for (i in closestIndex until waypoints.size - 1) {
            totalRemaining += distanceBetween(waypoints[i], waypoints[i + 1])
        }

        return totalRemaining
    }

    fun exportToGpx(context: Context, itinerary: Itinerary): File {
        val gpxDir = File(context.cacheDir, "gpx")
        gpxDir.mkdirs()
        val file = File(gpxDir, "${itinerary.name.replace("[^a-zA-Z0-9]".toRegex(), "_")}.gpx")

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"AlarmBus GPS\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(itinerary.name).append("</name>\n")
        sb.append("    <trkseg>\n")

        for (wp in itinerary.waypoints) {
            sb.append("      <trkpt lat=\"").append(wp.latitude).append("\" lon=\"").append(wp.longitude).append("\"/>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")

        file.writeText(sb.toString())
        return file
    }

    private fun distanceBetween(p1: LatLngData, p2: LatLngData): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0].toDouble()
    }
}
