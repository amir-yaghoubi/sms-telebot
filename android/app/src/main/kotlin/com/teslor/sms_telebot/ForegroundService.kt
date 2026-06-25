// Copyright (c) 2025-2026 Pavel D. (teslor)
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.teslor.sms_telebot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs permanently in the background and provides a notification.
 * Also manages the TelegramPoller coroutine for two-way SMS.
 */
class ForegroundService : Service() {

    companion object {
        private const val TAG = "ForegroundService"
        private const val CHANNEL_ID = "sms_telebot_foreground"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_RELOAD_CONTROL_BOT = "com.teslor.sms_telebot.RELOAD_CONTROL_BOT"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startPollerIfEnabled()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD_CONTROL_BOT) {
            Log.d(TAG, "Reloading control bot config")
            restartPoller()
            return START_STICKY
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dbManager = DbManager.getInstance(this)
        val serviceTitle = dbManager.getSetting("l10nServiceTitle") ?: "SMS Telebot is active"
        val serviceText = dbManager.getSetting("l10nServiceText") ?: "Monitoring events"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(serviceTitle)
            .setContentText(serviceText)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        pollerJob?.cancel()
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed, poller cancelled")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPollerIfEnabled() {
        val dbManager = DbManager.getInstance(this)
        if (!dbManager.getBoolSetting("controlBotEnabled")) return

        pollerJob?.cancel()
        pollerJob = serviceScope.launch {
            val poller = TelegramPoller(
                applicationContext,
                dbManager,
                SecureStorageManager.getInstance(applicationContext)
            )
            poller.run()
        }
        Log.i(TAG, "TelegramPoller started")
    }

    private fun restartPoller() {
        pollerJob?.cancel()
        startPollerIfEnabled()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "SMS Telebot foreground",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
