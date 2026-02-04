package io.upvalue.io

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class NtfyForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "ntfy_pebble_channel"
        private const val NOTIFICATION_ID = 1
    }

    private val ntfyReceiver = NtfyReceiver()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerNtfyReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(ntfyReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pebble Bridge",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Forwards ntfy messages to Pebble"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pebble Bridge")
            .setContentText("Listening for ntfy messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun registerNtfyReceiver() {
        val filter = IntentFilter(MainActivity.NTFY_ACTION)
        ContextCompat.registerReceiver(
            this,
            ntfyReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
