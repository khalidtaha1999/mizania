package com.mizania.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Gets every incoming SMS; only messages from the senders you picked are read. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val watched = Db.get(ctx).senderAccounts()
        if (watched.isEmpty()) return

        // Long messages arrive split into parts: join them per sender.
        val bodies = LinkedHashMap<String, StringBuilder>()
        val times = HashMap<String, Long>()
        for (m in parts) {
            if (m == null) continue
            val from = m.originatingAddress ?: continue
            bodies.getOrPut(from) { StringBuilder() }.append(m.messageBody ?: "")
            times[from] = m.timestampMillis
        }
        for ((from, body) in bodies) {
            val key = Db.norm(from)
            if (!watched.containsKey(key)) continue
            Processor.handle(ctx, from, body.toString(), times[from] ?: System.currentTimeMillis(),
                live = true, accountId = watched[key])
        }
    }
}
