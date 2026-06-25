// Copyright (c) 2025-2026 Pavel D. (teslor)
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.teslor.sms_telebot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsOutSender {

    private const val TAG = "SmsOutSender"

    sealed class SmsOutResult {
        object Success : SmsOutResult()
        data class Failure(val reason: String) : SmsOutResult()
    }

    fun send(context: Context, number: String, text: String): SmsOutResult {
        // Validate number
        val cleaned = number.replace(Regex("[\\s\\-()]+"), "")
        if (!isValidNumber(cleaned)) {
            return SmsOutResult.Failure("invalid_number")
        }

        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            return SmsOutResult.Failure("no_permission")
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            if (parts.size == 1) {
                smsManager.sendTextMessage(cleaned, null, text, null, null)
            } else {
                smsManager.sendMultipartTextMessage(cleaned, null, parts, null, null)
            }

            Log.i(TAG, "SMS sent to $cleaned (${parts.size} part(s))")
            SmsOutResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed: ${e.message}", e)
            SmsOutResult.Failure("send_error: ${e.message}")
        }
    }

    private fun isValidNumber(number: String): Boolean {
        if (number.length < 7 || number.length > 15) return false
        return number.matches(Regex("[+]?[0-9]+"))
    }
}
