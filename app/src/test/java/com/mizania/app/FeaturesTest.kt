package com.mizania.app

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Bills calendar and reminders, yearly expenses, unusual spending, price rises, runway, refunds, the cash tile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp")
class FeaturesTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var app: Application
    private lateinit var db: Db
    private var acc = 0L
    private var balance = 0.0
    private val SENDER = "ARABIISLAMI"
    private val ACCT = "1111*2222"
    private var food = 0L

    private fun at(daysAgo: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    private val now0 = System.currentTimeMillis()
    private fun minutesAgo(m: Int) = now0 - m * 60_000L
    private fun stamp(t: Long) = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.US).format(t)
    private fun jod(v: Double) = String.format(Locale.US, "%,.3f", v)

    private fun sms(t: Long, head: String, amount: Double, out: Boolean, extra: String = ""): Long {
        balance += if (out) -amount else amount
        val body = "$head\nالمبلغ: JOD ${jod(amount)}\nحساب: $ACCT\n${extra}تاريخ: ${stamp(t)}\nالرصيد: JOD ${jod(balance)}"
        return Processor.handle(app, SENDER, body, t, live = true, accountId = acc)
    }

    private fun notifications(): List<String> =
        Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            .map { (it.extras.getCharSequence("android.title") ?: "").toString() }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS", android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS)
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        db = Db.get(app)
        db.setSetting("lang", "ar"); Lang.apply("ar"); db.setSetting("onboarded", "1")
        acc = db.saveAccount(Account(0, "Main", "Test bank"))
        db.addSender(SENDER, acc)
        db.categories().forEach { db.deleteCategory(it.id) }
        food = db.saveCategory(Category(0, "طعام", 150.0, Db.DAILY, "🍔"))
        balance = 500.0
    }

    /** Messages are sent in time order, as a phone receives them: the balance chain relies on it. */
    private fun salary() = sms(at(20, 9), "تحويل راتب", 1000.0, false)

    @Test fun billReminderTwoDaysBefore() {
        salary()
        val inTwo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }.get(Calendar.DAY_OF_MONTH).coerceAtMost(28)
        val internet = db.saveCategory(Category(0, "إنترنت", 25.0, Db.FIXED, "🌐", dueDay = inTwo))
        val bills = Watch.upcomingBills(db)
        val b = bills.firstOrNull { it.cat.id == internet }
        assertNotNull("bill listed", b)
        Watch.remindBills(app, db)
        if (inTwo == Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }.get(Calendar.DAY_OF_MONTH))
            assertTrue("reminder posted: ${notifications()}", notifications().any { it.contains("إنترنت") && it.contains("بعد يومين") })
        // Once only.
        val n = notifications().size
        Watch.remindBills(app, db)
        assertEquals(n, notifications().size)
        // Paid: no longer a reminder, marked paid.
        val pay = sms(minutesAgo(30), "دفع eFAWATEERcom", 25.0, true)
        db.updateTxn(pay, internet, "")
        assertTrue(Watch.upcomingBills(db).first { it.cat.id == internet }.paid)
    }

    @Test fun yearlyExpenseIsHeldDayByDayAndPaidWithoutHittingTheDay() {
        salary()
        val id = Annual.add(db, "ترخيص السيارة", "🚗", 365.0, 1, 1)
        // Pretend it was added 50 days ago and is due in 50 days.
        db.setSetting("annual:$id", "365.0|${now0 + 50 * Period.DAY}|${now0 - 50 * Period.DAY}")
        val st = Annual.state(db, Annual.items(db).first())
        assertEquals("half of it is already held", 182.5, st.held, 1.0)
        val before = Budget.allowance(db)
        assertTrue("held counts as set aside", before.committed >= 182.0)
        // Pay it now, from the bank, sorted onto its line.
        val pay = sms(minutesAgo(20), "دفع eFAWATEERcom", 365.0, true)
        db.updateTxn(pay, id, "")
        val after = Budget.allowance(db)
        assertEquals("paying does not count as today's spending", before.spentToday, after.spentToday, 0.01)
        assertEquals("only the part not yet held leaves the free money", before.spendable!! - 182.5, after.spendable!!, 1.0)
        val next = Annual.items(db).first()
        assertTrue("moved to next year", next.due > now0 + 300 * Period.DAY)
    }

    @Test fun unusualWeekIsSpotted() {
        for (w in 9 downTo 2) { val t = sms(at(w * 7, 13), "حركة شراء MARKET", 10.0, true); db.updateTxn(t, food, "") }
        for (d in 4 downTo 1) { val t = sms(at(d, 14), "حركة شراء RESTAURANT", 12.0, true); db.updateTxn(t, food, "") }
        val end = Period.day(0).to
        println("UNUSUAL week=" + db.spentByCategory(end - 7 * Period.DAY, end) + " hist=" + db.spentByCategory(end - 63 * Period.DAY, end - 7 * Period.DAY) +
            " kinds=" + db.txns(0, end).map { it.kind + ":" + it.income + ":" + it.amount })
        val u = Watch.unusualWeeks(db)
        assertTrue("food flagged: $u", u.any { it.name == "طعام" && it.week >= 48.0 })
        Watch.warnUnusual(app, db)
        assertTrue(notifications().any { it.contains("غير معتاد") })
    }

    @Test fun subscriptionPriceRise() {
        for (m in listOf(95, 65, 35)) sms(at(m, 10), "حركة شراء NETFLIX.COM", 7.09, true)
        val id = sms(at(5, 10), "حركة شراء NETFLIX.COM", 8.5, true)
        val r = Watch.priceRise(db, db.txn(id)!!)
        assertNotNull(r)
        assertEquals(7.09, r!!.before, 0.001)
        assertTrue(notifications().any { it.contains("غلي سعره") })
    }

    @Test fun runwayDays() {
        for (d in 30 downTo 1) { val t = sms(at(d, 15), "حركة شراء GROCERY", 10.0, true); db.updateTxn(t, food, "") }
        val days = Watch.runwayDays(db)
        assertNotNull(days)
        println("RUNWAY balance=$balance days=$days")
        assertTrue("about balance / (300/days of history)", days!! in 15..25)
    }

    @Test fun refundGoesBackOnTheLine() {
        salary()
        val buy = sms(minutesAgo(3), "حركة شراء SHOE STORE", 30.0, true)
        db.updateTxn(buy, food, "")
        val spentBefore = Budget.allowance(db).spentToday
        val incomeBefore = db.otherIncome(0, Long.MAX_VALUE)
        // The bank says "استرداد".
        val back = sms(minutesAgo(1), "حركة استرداد SHOE STORE", 30.0, false)
        val t = db.txn(back)!!
        assertEquals("booked as a refund", Db.REFUND, t.kind)
        assertEquals("on the purchase's line", food, t.categoryId)
        assertEquals("line back to zero", 0.0, db.spentByCategory(at(1), Long.MAX_VALUE)[food] ?: 0.0, 0.01)
        assertEquals("not income", incomeBefore, db.otherIncome(0, Long.MAX_VALUE), 0.01)
        assertEquals("today's room comes back", spentBefore - 30.0, Budget.allowance(db).spentToday, 0.01)
        db.unmarkRefund(back)
        assertEquals("undo: income again", incomeBefore + 30.0, db.otherIncome(0, Long.MAX_VALUE), 0.01)
    }

    @Test fun refundWordingIsParsed() {
        salary()
        val r = SmsParser.parse("حركة استرداد ZARA AMMAN\nالمبلغ: JOD 15.000\nحساب: $ACCT\nالرصيد: JOD 100.000")
        assertNotNull(r)
        assertTrue(r!!.income)
        assertEquals("استرداد", r.kind)
        assertTrue(r.merchant.startsWith("ZARA"))
        assertNull("a person's transfer back is not a refund", Processor.refundMatch(db,
            db.txn(sms(minutesAgo(5), "حركة كليك واردة", 5.0, false, "من: AHMAD\n"))!!))
    }

    @Test fun cashTileOpensTheCashSheet() {
        val i = Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_ADD_CASH, true)
        ActivityScenario.launch<MainActivity>(i).use {
            rule.waitForIdle()
            assertTrue(rule.onAllNodesWithText("مصروف نقدي", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test fun monthScreenShowsTheNewCards() {
        Annual.add(db, "تأمين", "🛡", 120.0, 6, 1)
        db.saveCategory(Category(0, "إيجار", 275.0, Db.FIXED, "🏠", dueDay = 1))
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForIdle()
            rule.onAllNodesWithText("الخطة", useUnmergedTree = true)[0].performClick()
            rule.waitForIdle()
            for (t in listOf("الفواتير الجاية", "مصاريف سنوية", "تأمين"))
                assertTrue("$t shown", rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
    }

}
