package com.focusguard.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.app.data.StorageManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val storage = StorageManager(context)
            if (storage.shieldActive) {
                // Re-enable accessibility service hint
                storage.shieldActive = false // Will be re-enabled by user
            }
        }
    }
}
