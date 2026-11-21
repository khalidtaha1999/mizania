package com.mizania.app

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

/** The week's free money: what counts, the week's days, carrying over, and the nightly notes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyTest {
    private lateinit var app: Application
    private lateinit var db: Db
    private var acc = 0L
    private var fuel = 0L
    private var health = 0L
    private var rent = 0L
    private var bal = 2000.0
    private var n = 0

    /** Noon on a day of this week: 0 = the week's first day. Never later than now. */
    private fun dayOfWeek(i: Int): Long {
        val r = Weekly.range(db)
        return minOf(r.from + i * Period.DAY + 12 * Period.HOUR, System.currentTimeMillis() - 60_000)
    }

    private fun spend(time: Long, amount: Double, cat: Long?): Long {
        bal -= amount; n++
        return db.insertTxn(time, amount, false, "SHOP", "شراء", cat, "", bal, "TESTBANK", "", "w$n", false, acc, "1111*2222")
    }

    private fun notifications(): List<String> =
        Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            .map { (it.extras.getCharSequence("android.title") ?: "").toString() }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        setUpWith(salaryDaysAgo = 10)
    }

    private fun setUpWith(salaryDaysAgo: Int) {
        Shadows.shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS")
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        db = Db.get(app)
        db.setSetting("lang", "ar"); Lang.apply("ar"); db.setSetting("onboarded", "1")
        acc = db.saveAccount(Account(0, "Main", "Test bank"))
        db.categories().forEach { db.deleteCategory(it.id) }
        fuel = db.saveCategory(Category(0, "بنزين", 80.0, Db.DAILY, "🚗"))
        health = db.saveCategory(Category(0, "الصحة", 0.0, Db.DAILY, "💊"))
        rent = db.saveCategory(Category(0, "إيجار", 275.0, Db.FIXED, "🏠"))
        Weekly.setCap(db, 50.0)
        salary(salaryDaysAgo)
    }

    private fun salary(daysAgo: Int) {
        bal += 1000.0; n++
        val t = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
        }.timeInMillis
        db.insertTxn(t, 1000.0, true, "", "راتب", null, "", bal, "TESTBANK", "", "s$n", false, acc, "1111*2222")
    }

    private fun weekday(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK)

    @Test fun weekStartsOnPaydaysWeekday() {
        val r = Weekly.range(db)
        val payday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -10) }
        assertEquals(weekday(payday.timeInMillis), weekday(r.from))
        assertEquals(7, Weekly.days(r))
        assertEquals(weekday(payday.timeInMillis), weekday(Weekly.range(db, offset = -1).from))
    }

    @Test fun theDaysBeforeTheNextSalaryAreAShortWeek() {
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        app.deleteDatabase("mizania.db")
        setUpWith(salaryDaysAgo = 29)
        val r = Weekly.range(db)
        val d = Weekly.days(r)
        assertTrue("short: $d", d in 1..6)
        assertEquals(50.0 * d / 7, Weekly.week(db).cap, 0.01)
    }

    @Test fun onlyUnbudgetedAndUnsortedSpendingCounts() {
        val t = dayOfWeek(0)
        spend(t, 12.0, health)   // no budget: counts
        spend(t, 8.0, null)      // not sorted yet: counts
        spend(t, 30.0, fuel)     // has its own budget: does not
        spend(t, 275.0, rent)    // a commitment: does not
        val w = Weekly.week(db)
        assertEquals(20.0, w.spent, 0.001)
        assertEquals(30.0, w.left, 0.001)
        assertFalse(w.over)
    }

    @Test fun anyLineWithZeroBudgetCountsWhateverItsKind() {
        val t = dayOfWeek(0)
        val gifts = db.saveCategory(Category(0, "هدايا", 0.0, Db.FIXED, "🎁"))
        spend(t, 12.0, health); spend(t, 9.0, gifts); spend(t, 30.0, fuel)
        assertEquals(21.0, Weekly.week(db).spent, 0.001)
        // Giving a line a budget takes it out.
        db.saveCategory(db.categories().first { it.id == health }.copy(budget = 20.0))
        assertEquals(9.0, Weekly.week(db).spent, 0.001)
    }

    @Test fun lastWeekIsNotThisWeek() {
        spend(Weekly.range(db, offset = -1).from + 12 * Period.HOUR, 40.0, health)
        assertEquals(0.0, Weekly.week(db).spent, 0.001)
        assertEquals(40.0, Weekly.week(db, offset = -1).spent, 0.001)
    }

    @Test fun leftoverCarriesOnlyWhenChosen() {
        spend(Weekly.range(db, offset = -1).from + 12 * Period.HOUR, 30.0, health)
        assertEquals(50.0, Weekly.week(db).cap, 0.001)
        Weekly.setCarries(db, true)
        assertEquals("20 left last week comes along", 70.0, Weekly.week(db).cap, 0.001)
    }

    @Test fun nearAndOverAreSaidOnceAWeek() {
        val t = dayOfWeek(0)
        spend(t, 42.0, health)
        Weekly.notify(app, db)
        assertTrue(notifications().toString(), notifications().any { it.contains("قربتوا") })
        spend(t, 15.0, null)
        Weekly.notify(app, db)
        assertTrue(notifications().toString(), notifications().any { it.contains("تجاوزتوا") })
        assertFalse("over said once", db.firstTime("weekly-over:${Weekly.range(db).from}"))
    }

    @Test fun offMeansNothing() {
        Weekly.setCap(db, 0.0)
        assertFalse(Weekly.on(db))
        spend(dayOfWeek(0), 70.0, health)
        Weekly.notify(app, db)
        assertTrue(notifications().isEmpty())
    }

    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.timeInMillis
    private fun ymd(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let {
        "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH) + 1}-${it.get(Calendar.DAY_OF_MONTH)}"
    }

    @Test fun usualPaydayKeepsTheNextSalaryOnThe25thEvenWhenOneCameEarly() {
        // Without a usual payday: a month after the last salary.
        assertEquals("2026-11-23", ymd(Budget.expectedAfter(db, day(2026, 10, 23))))
        db.setSetting("payday", "25")
        assertEquals("came two days early", "2026-11-25", ymd(Budget.expectedAfter(db, day(2026, 10, 23))))
        assertEquals("on time", "2026-12-25", ymd(Budget.expectedAfter(db, day(2026, 11, 25))))
        assertEquals("came late", "2026-11-25", ymd(Budget.expectedAfter(db, day(2026, 10, 27))))
        db.setSetting("payday", "31")
        assertEquals("short month", "2027-2-28", ymd(Budget.expectedAfter(db, day(2027, 1, 31))))
    }

    @Test fun theWeekNeverOffersMoreThanIsReallyThere() {
        // Plenty in the bank: the plan decides.
        spend(dayOfWeek(0), 5.0, health)
        val rich = Weekly.week(db)
        assertFalse(rich.limitedByBank)
        assertEquals(45.0, rich.left, 0.001)
        // Nearly empty at the end of the month: only what is free until payday.
        bal = 25.0
        spend(dayOfWeek(0), 12.0, null)
        val al = Budget.allowance(db)
        val w = Weekly.week(db)
        assertTrue("limited by the bank", w.limitedByBank)
        assertEquals(kotlin.math.max(0.0, al.spendable!!), w.left, 0.001)
        assertTrue(w.left < w.byPlan)
    }
}
