package com.lifxcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifxcontrol.worker.LightMonitorWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            LightMonitorWorker.schedule(context)
        }
    }
}
