package com.mizania.app

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

/** A day-to-day line running ahead of its budget, and the weekly backup without a folder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaceTest {
    private lateinit var app: Application
    private lateinit var db: Db
    private var acc = 0L
    private var food = 0L
    private var bal = 2000.0

    private fun at(daysAgo: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    private fun t(daysAgo: Int, amount: Double, income: Boolean, kind: String, cat: Long?, hour: Int = 12): Long {
        bal += if (income) amount else -amount
        return db.insertTxn(at(daysAgo, hour), amount, income, if (income) "" else "SHOP", kind, cat, "", bal, "TESTBANK",
            "", "p$daysAgo-$hour-$amount", false, acc, "1111*2222")
    }

    private fun notifications(): List<String> =
        Shadows.shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            .map { (it.extras.getCharSequence("android.title") ?: "").toString() }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS")
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        db = Db.get(app)
        db.setSetting("lang", "ar"); Lang.apply("ar"); db.setSetting("onboarded", "1")
        acc = db.saveAccount(Account(0, "Main", "Test bank"))
        db.categories().forEach { db.deleteCategory(it.id) }
        food = db.saveCategory(Category(0, "طعام", 150.0, Db.DAILY, "🍔"))
    }

    private fun cat() = db.categories().first { it.id == food }

    @Test fun aheadOfBudgetIsFastWithADailyAmountToGetBack() {
        t(12, 1000.0, true, "راتب", null, hour = 9)
        for (d in 12 downTo 1) t(d, 7.5, false, "شراء", food)
        val p = Watch.pace(db, cat())
        assertNotNull(p)
        p!!
        assertEquals(90.0, p.spent, 0.01)
        assertTrue("heading over: ${p.forecast}", p.forecast > 150)
        assertTrue(p.fast)
        assertEquals((150 - 90.0) / p.left, p.perDay, 0.01)
    }

    @Test fun onTrackIsNotFast() {
        t(12, 1000.0, true, "راتب", null, hour = 9)
        for (d in 12 downTo 1) t(d, 2.0, false, "شراء", food)
        val p = Watch.pace(db, cat())
        assertNotNull(p)
        assertFalse(p!!.fast)
    }

    @Test fun tooEarlyInTheMonthSaysNothing() {
        t(3, 1000.0, true, "راتب", null, hour = 9)
        t(2, 80.0, false, "شراء", food)
        assertNull(Watch.pace(db, cat()))
    }

    @Test fun fixedLinesHaveNoPace() {
        t(12, 1000.0, true, "راتب", null, hour = 9)
        val rent = db.saveCategory(Category(0, "إيجار", 300.0, Db.FIXED, "🏠"))
        t(11, 300.0, false, "كليك", rent)
        assertNull(Watch.pace(db, db.categories().first { it.id == rent }))
    }

    @Test fun spreadSpendCountsOnlyItsShareOfTheDaysGone() {
        t(12, 1000.0, true, "راتب", null, hour = 9)
        val big = t(10, 120.0, false, "شراء", food)
        val before = Watch.pace(db, cat())!!
        db.setSpread(big, true)
        val after = Watch.pace(db, cat())!!
        assertTrue("spread lowers what counts so far", after.spent < before.spent)
    }

    @Test fun warnsOnceAndAgainOnlyWhenClearlyWorse() {
        t(12, 1000.0, true, "راتب", null, hour = 9)
        for (d in 12 downTo 2) t(d, 7.5, false, "شراء", food)
        Watch.warnPace(app, db)
        assertTrue(notifications().toString(), notifications().any { it.contains("طعام") && it.contains("بسرعة") })
        val key = "pace:${Budget.cycle(db).range.from}:$food"
        val first = db.setting(key, "").toDouble()
        Watch.warnPace(app, db)
        assertEquals("not again the same night", first, db.setting(key, "").toDouble(), 0.001)
        t(1, 60.0, false, "شراء", food)
        Watch.warnPace(app, db)
        assertTrue("again when much worse", db.setting(key, "").toDouble() > first * 1.5)
    }

    @Test fun autoBackupWithoutAFolderDoesNothing() {
        assertNull(AutoBackup.folder(db))
        assertFalse(AutoBackup.run(app, db))
        AutoBackup.runIfDue(app, db)
        assertEquals(0L, AutoBackup.last(db))
    }
}
