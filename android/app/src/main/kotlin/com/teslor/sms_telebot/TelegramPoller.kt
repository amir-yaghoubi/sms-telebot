// Copyright (c) 2025-2026 Pavel D. (teslor)
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.teslor.sms_telebot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramPoller(
    private val context: Context,
    private val dbManager: DbManager,
    private val secretStorage: SecureStorageManager
) {
    companion object {
        private const val TAG = "TelegramPoller"
        private const val POLL_TIMEOUT_S = 25
        private const val RETRY_DELAY_MS = 5_000L
        private const val WIZARD_TIMEOUT_MS = 5 * 60 * 1000L
    }

    private enum class WizardStep { AWAITING_NUMBER, AWAITING_TEXT }

    private data class WizardSession(
        val step: WizardStep,
        val number: String? = null,
        val startedAt: Long = System.currentTimeMillis()
    )

    private val wizardSessions = mutableMapOf<Long, WizardSession>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout((POLL_TIMEOUT_S + 10).toLong(), TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun run() = coroutineScope {
        val secretResult = secretStorage.readSecret("control_bot")
        if (!secretResult.isSuccess || secretResult.data.isNullOrBlank()) {
            Log.w(TAG, "No control bot token configured")
            return@coroutineScope
        }
        val token = secretResult.data!!
        val chatId = dbManager.getSetting("controlBotChatId") ?: run {
            Log.w(TAG, "No control bot chatId configured")
            return@coroutineScope
        }
        val apiUrl = dbManager.getSetting("controlBotApiUrl")
            .let { if (it.isNullOrBlank()) "https://api.telegram.org" else it }

        var offset = dbManager.getSetting("controlBotUpdateOffset")?.toLongOrNull() ?: 0L
        Log.i(TAG, "Polling started from offset $offset")

        while (isActive) {
            try {
                val updates = fetchUpdates(token, chatId, apiUrl, offset)
                for (update in updates) {
                    val updateId = update.getLong("update_id")
                    offset = updateId + 1
                    dbManager.saveSetting("controlBotUpdateOffset", offset.toString())
                    val message = update.optJSONObject("message") ?: continue
                    val msgChatId = message.optJSONObject("chat")?.optLong("id") ?: continue
                    if (msgChatId.toString() != chatId) continue  // security: ignore other chats
                    processMessage(token, chatId, apiUrl, msgChatId, message)
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "Polling cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Poll error: ${e.message}")
                delay(RETRY_DELAY_MS)
            }
        }
    }

    private fun fetchUpdates(token: String, chatId: String, apiUrl: String, offset: Long): List<JSONObject> {
        val url = "$apiUrl/bot$token/getUpdates?timeout=$POLL_TIMEOUT_S&offset=$offset"
        val request = Request.Builder().url(url).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    throw CancellationException("Auth error ${response.code}")
                }
                return@use emptyList()
            }
            val body = response.body?.string() ?: return@use emptyList()
            val json = JSONObject(body)
            if (!json.optBoolean("ok")) return@use emptyList()
            val result = json.optJSONArray("result") ?: return@use emptyList()
            List(result.length()) { i -> result.getJSONObject(i) }
        }
    }

    private fun processMessage(
        token: String, chatId: String, apiUrl: String,
        msgChatId: Long, message: JSONObject
    ) {
        val text = message.optString("text", "").trim()
        val now = System.currentTimeMillis()

        // Check wizard session timeout for this chat
        val session = wizardSessions[msgChatId]
        if (session != null && now - session.startedAt > WIZARD_TIMEOUT_MS) {
            wizardSessions.remove(msgChatId)
            sendMessage(token, chatId, apiUrl, "⏱ Session timed out. Send /send to start again.")
            return
        }

        // Wizard in progress — consume message as wizard step
        if (session != null) {
            handleWizardStep(token, chatId, apiUrl, msgChatId, session, text)
            return
        }

        // /send <number> <text>
        if (text.startsWith("/send ")) {
            val args = text.removePrefix("/send ").trim()
            val spaceIdx = args.indexOf(' ')
            if (spaceIdx == -1) {
                sendMessage(token, chatId, apiUrl, "❌ Usage: /send <number> <message>")
                return
            }
            val number = args.substring(0, spaceIdx).trim()
            val smsText = args.substring(spaceIdx + 1).trim()
            sendSmsAndConfirm(token, chatId, apiUrl, number, smsText)
            return
        }

        // /send alone — start wizard
        if (text == "/send") {
            wizardSessions[msgChatId] = WizardSession(step = WizardStep.AWAITING_NUMBER)
            sendMessage(token, chatId, apiUrl, "📱 Enter the recipient's phone number:")
            return
        }

        // Reply to forwarded message
        val replyTo = message.optJSONObject("reply_to_message")
        if (replyTo != null) {
            val parentText = replyTo.optString("text", "")
            val number = extractPhoneNumber(parentText)
            if (number == null) {
                sendMessage(token, chatId, apiUrl,
                    "❌ Could not extract a phone number from that message.")
                return
            }
            sendSmsAndConfirm(token, chatId, apiUrl, number, text)
            return
        }

        // Unknown command — show help
        if (text.startsWith("/")) {
            sendMessage(token, chatId, apiUrl,
                "ℹ️ Commands:\n/send <number> <text> — send an SMS\nor reply to a forwarded SMS message.")
        }
    }

    private fun handleWizardStep(
        token: String, chatId: String, apiUrl: String,
        msgChatId: Long, session: WizardSession, text: String
    ) {
        when (session.step) {
            WizardStep.AWAITING_NUMBER -> {
                if (text == "/send") {
                    // Restart wizard
                    wizardSessions[msgChatId] = WizardSession(step = WizardStep.AWAITING_NUMBER)
                    sendMessage(token, chatId, apiUrl, "🔄 Started over. Enter recipient number:")
                    return
                }
                wizardSessions[msgChatId] = WizardSession(
                    step = WizardStep.AWAITING_TEXT,
                    number = text
                )
                sendMessage(token, chatId, apiUrl, "✉️ Enter your message:")
            }
            WizardStep.AWAITING_TEXT -> {
                val number = session.number ?: return
                wizardSessions.remove(msgChatId)
                sendSmsAndConfirm(token, chatId, apiUrl, number, text)
            }
        }
    }

    private fun extractPhoneNumber(text: String): String? {
        val firstLine = text.lines().firstOrNull() ?: return null
        val plain = firstLine.replace(Regex("<[^>]+>"), "").trim()
        val match = Regex("^[💬📞]\\s+(.+?)(?:\\s+\\([^)]+\\))?\\s+🕒").find(plain)
            ?: return null
        val candidate = match.groupValues[1].trim()
        if (!looksLikePhoneNumber(candidate)) return null
        return candidate
    }

    private fun looksLikePhoneNumber(s: String): Boolean {
        if (s.startsWith("+")) return true
        val digits = s.count { it.isDigit() }
        return digits >= 7
    }

    private fun sendSmsAndConfirm(
        token: String, chatId: String, apiUrl: String,
        number: String, smsText: String
    ) {
        val reply = when (val result = SmsOutSender.send(context, number, smsText)) {
            is SmsOutSender.SmsOutResult.Success ->
                "✅ SMS sent to $number"
            is SmsOutSender.SmsOutResult.Failure -> when {
                result.reason == "invalid_number" ->
                    "❌ Invalid number: \"$number\". Use format: +1234567890"
                result.reason == "no_permission" ->
                    "❌ SMS send permission not granted. Open the app to allow it."
                else ->
                    "❌ Failed to send SMS: ${result.reason.removePrefix("send_error: ")}"
            }
        }
        sendMessage(token, chatId, apiUrl, reply)
    }

    private fun sendMessage(token: String, chatId: String, apiUrl: String, text: String) {
        try {
            val body = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .build()
            val request = Request.Builder()
                .url("$apiUrl/bot$token/sendMessage")
                .post(body)
                .build()
            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed: ${e.message}")
        }
    }
}
