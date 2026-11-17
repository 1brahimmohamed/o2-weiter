package de.o2.weiter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val address = parts[0].originatingAddress
        if (!Matcher.isFromO2(address)) return
        val body = parts.joinToString("") { it.messageBody ?: "" }

        val store = Store(context)
        if (!store.enabled) {
            store.log("Disabled – ignored SMS from 80112")
            return
        }
        if (!Matcher.asksForWeiter(body)) return // e.g. "Zusatzvolumen erfolgreich aktiviert"

        val now = System.currentTimeMillis()
        if (!Matcher.shouldReply(address, body, store.lastSentAt, now)) {
            store.log("Skipped (already sent WEITER < 1 min ago)")
            return
        }

        // Reply from the same SIM that received the message (matters on dual-SIM).
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        )
        val kind = if (body.contains("80%")) "80% warning" else "volume used up"
        if (sendWeiter(context, subId)) {
            store.lastSentAt = now
            store.incrementToday()
            store.log("Sent WEITER ($kind)")
            notify(context, "Requested +2 GB", "Replied WEITER to 80112 ($kind). Today: ${store.todayCount}")
        } else {
            store.log("FAILED to send WEITER ($kind)")
            notify(context, "Could not send WEITER", "Open the app and check SMS permission.")
        }
    }

    companion object {
        fun sendWeiter(context: Context, subId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID): Boolean {
            if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
            return try {
                val sms = smsManager(context, subId)
                sms.sendTextMessage(Matcher.SENDER, null, Matcher.REPLY, null, null)
                true
            } catch (e: Exception) {
                false
            }
        }

        @Suppress("DEPRECATION")
        private fun smsManager(context: Context, subId: Int): SmsManager {
            val valid = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subId >= 0
            return if (Build.VERSION.SDK_INT >= 31) {
                val base = context.getSystemService(SmsManager::class.java)
                if (valid) base.createForSubscriptionId(subId) else base
            } else {
                if (valid) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
            }
        }

        fun notify(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("weiter", "Data top-ups", NotificationManager.IMPORTANCE_LOW)
            )
            val n = android.app.Notification.Builder(context, "weiter")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        }
    }
}
