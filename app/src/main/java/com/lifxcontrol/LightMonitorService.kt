package com.lifxcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lifxcontrol.data.api.LifxApiClient
import com.lifxcontrol.data.local.PreferencesManager
import com.lifxcontrol.data.local.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LightMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private val previousPowerStates = mutableMapOf<String, String>()
    private var isFirstPoll = true

    companion object {
        private const val PERSISTENT_NOTIFICATION_ID = 1
        private const val MONITORING_CHANNEL_ID = "light_monitoring_channel"
        private const val ALERT_CHANNEL_ID = "light_alert_channel"
        private const val POLL_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, LightMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LightMonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(PERSISTENT_NOTIFICATION_ID, buildMonitoringNotification("Starting…"))
        startConfigObserver()
    }

    private fun startConfigObserver() {
        serviceScope.launch {
            combine(
                applicationContext.dataStore.data.map { it[PreferencesManager.API_TOKEN] },
                applicationContext.dataStore.data.map { it[PreferencesManager.SELECTED_LOCATION_ID] },
                applicationContext.dataStore.data.map { it[PreferencesManager.SELECTED_LOCATION_NAME] }
            ) { token, locationId, locationName ->
                Triple(token, locationId, locationName)
            }.collect { (token, locationId, locationName) ->
                pollingJob?.cancel()
                previousPowerStates.clear()
                isFirstPoll = true

                if (!token.isNullOrBlank() && !locationId.isNullOrBlank()) {
                    val label = locationName ?: "home"
                    updateMonitoringNotification("Monitoring $label")
                    pollingJob = launch {
                        pollLoop(token, locationId, label)
                    }
                } else {
                    updateMonitoringNotification("Waiting for configuration")
                }
            }
        }
    }

    private suspend fun pollLoop(token: String, locationId: String, locationName: String) {
        val api = LifxApiClient.create(token)
        while (currentCoroutineContext().isActive) {
            try {
                val response = api.getLightsBySelector("location:$locationId")
                if (response.isSuccessful) {
                    val lights = response.body() ?: emptyList()

                    if (isFirstPoll) {
                        lights.forEach { previousPowerStates[it.id] = it.power }
                        isFirstPoll = false
                    } else {
                        val newlyOn = lights.filter { light ->
                            previousPowerStates[light.id] == "off" && light.power == "on"
                        }
                        if (newlyOn.isNotEmpty()) {
                            val labels = newlyOn.joinToString(", ") { it.label }
                            sendAlertNotification(labels, locationName)
                        }
                        lights.forEach { previousPowerStates[it.id] = it.power }
                    }
                }
            } catch (_: Exception) { }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun updateMonitoringNotification(status: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PERSISTENT_NOTIFICATION_ID, buildMonitoringNotification(status))
    }

    private fun buildMonitoringNotification(status: String): Notification =
        NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("LIFX Monitor")
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun sendAlertNotification(lightLabels: String, locationName: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Light on at $locationName")
            .setContentText("$lightLabels switched on")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(
                NotificationChannel(
                    MONITORING_CHANNEL_ID,
                    "Light Monitor Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent indicator while monitoring lights"
                    setShowBadge(false)
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Light Turned On",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when a light switches on"
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
