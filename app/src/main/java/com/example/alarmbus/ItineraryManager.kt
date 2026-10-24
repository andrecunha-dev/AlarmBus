package com.example.alarmbus

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LatLngData(
    val latitude: Double,
    val longitude: Double
)

data class Itinerary(
    val id: String,
    val name: String,
    val startAddress: String,
    val endAddress: String,
    val waypoints: List<LatLngData>,
    val dateCreated: String
)

object ItineraryManager {

    private const val PREFS_NAME = "alarmbus_itineraries"
    private const val KEY_ITINERARY_LIST = "itinerary_list_json"
    private const val KEY_ACTIVE_ITINERARY_ID = "active_itinerary_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                val startAddress = obj.optString("startAddress", "")
                val endAddress = obj.optString("endAddress", "")
                val dateCreated = obj.optString("dateCreated", "")

                val waypointsArray = obj.optJSONArray("waypoints") ?: JSONArray()
                val waypoints = mutableListOf<LatLngData>()
                for (j in 0 until waypointsArray.length()) {
                    val wObj = waypointsArray.getJSONObject(j)
                    waypoints.add(
                        LatLngData(
                            latitude = wObj.getDouble("lat"),
                            longitude = wObj.getDouble("lng")
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
                        dateCreated = dateCreated
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

                val waypointsArray = JSONArray()
                for (wp in item.waypoints) {
                    val wObj = JSONObject().apply {
                        put("lat", wp.latitude)
                        put("lng", wp.longitude)
                    }
                    waypointsArray.put(wObj)
                }
                put("waypoints", waypointsArray)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ITINERARY_LIST, array.toString()).apply()
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
