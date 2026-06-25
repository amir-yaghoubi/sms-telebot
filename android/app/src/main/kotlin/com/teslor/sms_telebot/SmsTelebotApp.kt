// Copyright (c) 2025-2026 Pavel D. (teslor)
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.teslor.sms_telebot

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Application class that registers the device status receiver
 * and starts ForegroundService if control bot is configured.
 */
class SmsTelebotApp : Application() {

    private val deviceStatusReceiver = DeviceStatusReceiver()

    override fun onCreate() {
        super.onCreate()
        registerDeviceStatusReceiver()
        startForegroundServiceIfNeeded()
    }

    private fun registerDeviceStatusReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            this,
            deviceStatusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun startForegroundServiceIfNeeded() {
        val dbManager = DbManager.getInstance(this)
        if (dbManager.getBoolSetting("controlBotEnabled")) {
            ContextCompat.startForegroundService(
                this, Intent(this, ForegroundService::class.java)
            )
        }
    }
}
