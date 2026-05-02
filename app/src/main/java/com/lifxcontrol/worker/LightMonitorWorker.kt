package com.lifxcontrol.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lifxcontrol.data.api.LifxApiClient
import com.lifxcontrol.data.local.PreferencesManager
import com.lifxcontrol.data.local.dataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class LightMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "light_monitor_channel"
        const val WORK_NAME = "light_monitor_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LightMonitorWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()
        val token = prefs[PreferencesManager.API_TOKEN] ?: return Result.success()
        val locationId = prefs[PreferencesManager.SELECTED_LOCATION_ID] ?: return Result.success()
        val locationName = prefs[PreferencesManager.SELECTED_LOCATION_NAME] ?: "Home"
        val previousStatesJson = prefs[PreferencesManager.LIGHT_POWER_STATES_JSON]

        return try {
            val api = LifxApiClient.create(token)
            val response = api.getLightsBySelector("location:$locationId")

            if (!response.isSuccessful) return Result.success()

            val currentLights = response.body() ?: return Result.success()
            val gson = Gson()

            val previousStates: Map<String, String> = if (previousStatesJson != null) {
                gson.fromJson(previousStatesJson, object : TypeToken<Map<String, String>>() {}.type)
            } else {
                emptyMap()
            }

            val newlyOnLights = currentLights.filter { light ->
                previousStates[light.id] == "off" && light.power == "on"
            }

            if (newlyOnLights.isNotEmpty()) {
                val labels = newlyOnLights.joinToString(", ") { it.label }
                sendNotification(labels, locationName)
            }

            val prefsManager = PreferencesManager(applicationContext)
            val currentStates = currentLights.associate { it.id to it.power }
            prefsManager.saveLightPowerStatesJson(gson.toJson(currentStates))

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(lightLabels: String, locationName: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(com.lifxcontrol.R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(
                    com.lifxcontrol.R.string.notification_channel_desc
                )
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Light on at $locationName")
            .setContentText("$lightLabels switched on")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
