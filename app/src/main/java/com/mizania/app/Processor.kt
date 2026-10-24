package com.mizania.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import java.security.MessageDigest
import java.util.Locale

fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v) + " " + currency()

/** Lets the open screen refresh when an SMS is recorded in the background. */
object Bus {
    @Volatile var listener: (() -> Unit)? = null
    fun changed() {
        Handler(Looper.getMainLooper()).post { listener?.invoke() }
    }
}

data class InboxSender(val address: String, val count: Int, val sample: String)

object Processor {

    private fun hash(sender: String, body: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest((Db.norm(sender) + "|" + body.trim()).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses one SMS and stores it. [live] = it just arrived, so notify.
     * Returns the new transaction id, or -1 when it is not a transaction or was already stored.
     */
    fun handle(ctx: Context, sender: String, body: String, smsTime: Long, live: Boolean, accountId: Long?): Long {
        val db = Db.get(ctx)
        // A bank that writes "قيد" on every real movement: a message without it is only a notice.
        val r = SmsParser.parse(body)?.takeIf { SmsParser.isPosted(body) || !db.senderUsesQaid(sender) }
        if (r == null) {
            // "رصيد حسابك ... هو X": not a movement, but a fresh balance worth keeping.
            val n = SmsParser.balanceNotice(body)
            if (n != null && accountId != null) {
                Db.get(ctx).setBalance(accountId, n[0], n[1].toDouble(), if (smsTime > 0) smsTime else System.currentTimeMillis())
                AllowanceWidget.refreshAll(ctx)
                if (live) Bus.changed()
            }
            return -1
        }
        // Order by when the SMS arrived: the bank's own date can be a business date
        // (a purchase at 1 am is often stamped with the day before) and breaks the balance chain.
        val time: Long = if (smsTime > 0) smsTime else r.time ?: System.currentTimeMillis()
        // Moving money between your own accounts (or to yourself elsewhere) is not spending.
        val own = r.internal || db.isOwnName(r.merchant)
        val catId: Long? = if (r.income || own) null else db.matchRule(r.merchant)
        val id = db.insertTxn(
            time = time, amount = r.amount, income = r.income, merchant = r.merchant, kind = r.kind,
            categoryId = catId, note = "", balance = r.balance, sender = sender, body = body,
            hash = hash(sender, body), manual = false, accountId = accountId, acctNo = r.acctNo,
            toAcct = r.toAcct, balAcct = r.balAcct, ignored = own,
        )
        if (id < 0) return -1
        AllowanceWidget.refreshAll(ctx)
        if (live && !own) {
            // The balance has the final word on money in vs out (e.g. a CliQ that was actually received).
            db.reconcileDirections()
            val t = db.txn(id) ?: return id
            when {
                t.income && t.kind == "راتب" && !db.noSalary() -> Notifier.info(ctx, id, tr("وصل الراتب: ${fmt(t.amount)}", "Salary arrived: ${fmt(t.amount)}"),
                    tr("بدأ شهر جديد في الميزانية", "A new budget month has started") +
                        (t.balance?.let { tr(" — رصيدك ${fmt(it)}", " — balance ${fmt(it)}") } ?: ""))
                t.income -> Notifier.info(ctx, id, tr("وصلك ${fmt(t.amount)} (${t.kindText})", "You received ${fmt(t.amount)} (${t.kindText})"),
                    merchantLabel(t.merchant) + (t.balance?.let { tr(" — رصيدك الآن ${fmt(it)}", " — balance now ${fmt(it)}") } ?: ""))
                t.categoryId == null -> Notifier.ask(ctx, id, t.amount, t.title)
                else -> afterCategorized(ctx, id, t.categoryId, notifyRecorded = true)
            }
            Bus.changed()
        }
        return id
    }

    /**
     * Called once a spend has a category (automatically or by the user):
     * tells what is left in that category and fires the 80% / 100% alerts once per month.
     */
    fun afterCategorized(ctx: Context, txnId: Long, catId: Long, notifyRecorded: Boolean) {
        val db = Db.get(ctx)
        val cat = db.category(catId) ?: return
        val txn = db.txn(txnId) ?: return
        // Savings go into the goal, debt payments onto the debt, jamiya payments tick the month.
        db.syncLinks(txnId)
        val cycle = Budget.cycle(db).range
        val spent = db.spentByCategory(cycle.from, cycle.to)[catId] ?: 0.0
        val left = cat.budget - spent

        if (cat.budget > 0 && txn.time >= cycle.from) {
            val pct = spent / cat.budget
            val base = "${cat.id}:${cycle.from}"
            if (pct >= 1.0 && db.firstTime("$base:100")) {
                db.firstTime("$base:80")
                Notifier.alert(ctx, cat.id, tr("تجاوزت ميزانية ${cat.name}", "Over budget: ${cat.name}"),
                    tr("صرفت ${fmt(spent)} من ${fmt(cat.budget)} — بزيادة ${fmt(-left)}",
                        "Spent ${fmt(spent)} of ${fmt(cat.budget)} — ${fmt(-left)} over"))
                return
            }
            if (pct >= 0.8 && pct < 1.0 && db.firstTime("$base:80")) {
                Notifier.alert(ctx, cat.id, tr("اقتربت من نهاية ميزانية ${cat.name}", "${cat.name} is almost used up"),
                    tr("صرفت ${(pct * 100).toInt()}% — المتبقي ${fmt(left)}", "Spent ${(pct * 100).toInt()}% — ${fmt(left)} left"))
                return
            }
        }
        if (notifyRecorded) {
            val tail = if (cat.budget > 0) tr(" — المتبقي ${fmt(left)}", " — ${fmt(left)} left") else ""
            Notifier.info(ctx, txnId, tr("${fmt(txn.amount)} على ${cat.name}", "${fmt(txn.amount)} on ${cat.name}"), merchantLabel(txn.merchant) + tail)
        }
    }

    fun canReadSms(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Distinct senders in the inbox, most recent first, with a sample message each. */
    fun inboxSenders(ctx: Context, scan: Int = 1500): List<InboxSender> {
        if (!canReadSms(ctx)) return emptyList()
        val counts = LinkedHashMap<String, Int>()
        val samples = HashMap<String, String>()
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null, null, Telephony.Sms.DEFAULT_SORT_ORDER
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n < scan) {
                n++
                val a = c.getString(0) ?: continue
                counts[a] = (counts[a] ?: 0) + 1
                if (!samples.containsKey(a)) samples[a] = c.getString(1) ?: ""
            }
        }
        return counts.map { (a, n) -> InboxSender(a, n, samples[a] ?: "") }
    }

    /** Pulls watched senders' messages since [from] from the inbox. Returns how many were added. */
    fun importInbox(ctx: Context, from: Long, to: Long = Long.MAX_VALUE): Int {
        if (!canReadSms(ctx)) return 0
        val watched = Db.get(ctx).senderAccounts()
        if (watched.isEmpty()) return 0
        var added = 0
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?", arrayOf(from.toString(), to.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val a = c.getString(0) ?: continue
                val key = Db.norm(a)
                if (!watched.containsKey(key)) continue
                if (handle(ctx, a, c.getString(1) ?: "", c.getLong(2), live = false, accountId = watched[key]) > 0) added++
            }
        }
        Db.get(ctx).reconcileDirections()
        return added
    }

    /**
     * Reads every stored bank SMS again with the current reader, and puts each one at the time
     * the SMS arrived (from the inbox). Budget lines, notes and ignored marks are kept.
     * Runs once after an update that changes how messages are read.
     */
    fun repairIfNeeded(ctx: Context) {
        val db = Db.get(ctx)
        val version = "10"
        if (db.setting("repair", "0") == version) return
        val arrived = HashMap<String, Long>()
        if (canReadSms(ctx)) {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?", arrayOf((System.currentTimeMillis() - 400 * Period.DAY).toString()), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val a = c.getString(0) ?: continue
                    val body = c.getString(1) ?: ""
                    arrived[hash(a, body)] = c.getLong(2)
                    // Older versions took the number in a refused-transaction SMS as the balance: drop it.
                    if (SmsParser.isDeclined(body)) db.dropBalanceFromSms(c.getLong(2), body)
                }
            }
        }
        db.reparseAll(arrived)
        // Messages the older reader skipped (e.g. "دفع eFAWATEERcom") were never stored: pick up the recent ones.
        importInbox(ctx, System.currentTimeMillis() - 40 * Period.DAY)
        db.reconcileDirections()
        db.syncAllLinks()
        if (canReadSms(ctx)) db.setSetting("repair", version)
    }
}
