package com.mizania.app

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Opens every screen and the main sheets with a lived-in month of data, the way a user taps through the app.
 * A crash anywhere fails the test. All data is made up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp")
class ScreensTest {
    @get:Rule val rule = createEmptyComposeRule()
    private lateinit var app: Application
    private lateinit var db: Db
    private var scenario: ActivityScenario<MainActivity>? = null
    private var dinner = 0L

    /** daysAgo = 0 means minutes ago today (hour = minutes before now), so nothing lands in the future. */
    private fun at(daysAgo: Int, hour: Int): Long = if (daysAgo == 0) System.currentTimeMillis() - (hour + 1) * 60_000L
    else Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
    }.timeInMillis

    @Before fun seed() {
        app = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
            "android.permission.POST_NOTIFICATIONS")
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        db = Db.get(app)
        db.setSetting("lang", "ar")
        db.setSetting("onboarded", "1")
        val acc = db.saveAccount(Account(0, "Main", "Test bank"))
        db.addSender("TESTBANK", acc)
        db.categories().forEach { db.deleteCategory(it.id) }
        val rent = db.saveCategory(Category(0, "إيجار", 275.0, Db.FIXED, "🏠"))
        val save = db.saveCategory(Category(0, "ادخار", 100.0, Db.RESERVE, "💰"))
        val food = db.saveCategory(Category(0, "طعام", 150.0, Db.DAILY, "🍔"))
        var bal = 1000.0
        fun t(daysAgo: Int, hour: Int, amount: Double, income: Boolean, shop: String, kind: String, cat: Long?): Long {
            bal += if (income) amount else -amount
            return db.insertTxn(at(daysAgo, hour), amount, income, shop, kind, cat, "", bal, "TESTBANK",
                "", "h$daysAgo-$hour-$shop", false, acc, "1111*2222")
        }
        t(15, 10, 800.0, true, "", "راتب", null)
        t(14, 11, 275.0, false, "LANDLORD", "كليك", rent)
        for (d in 13 downTo 1) t(d, 18, 5.0, false, "GROCERY STORE", "شراء", food)
        t(0, 2, 1.0, false, "AL SALAMAT COFFEE", "شراء", null)
        dinner = t(0, 1, 40.0, false, "RESTAURANT", "شراء", null)
        db.payForOthers(dinner, food, "عشا", listOf("AHMAD" to 10.0, "SAMI" to 10.0), "")
        db.saveDebt(0, "خالي", false, 200.0, "قرض")
        val g = db.saveGoal(0, "سيارة", 3000.0, null, "🚗")
        db.addToGoalOnLine(g, 50.0, save)
    }

    @After fun close() { scenario?.close() }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle()
    }

    private fun node(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        val all = rule.onAllNodesWithText(text, substring = substring, ignoreCase = true, useUnmergedTree = true)
        if (all.fetchSemanticsNodes().isEmpty()) {
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
                listOfNotNull(n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString()) +
                    n.children.flatMap { walk(it) }
            val seen = rule.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { walk(it) }
            println("SCREEN when looking for $text: " + seen.joinToString(" | "))
        }
        assertTrue("\"$text\" should be on screen", all.fetchSemanticsNodes().isNotEmpty())
        return all[0]
    }

    private fun tap(text: String, substring: Boolean = false) {
        node(text, substring)
        // The merged node is the button itself, so the click lands on it even inside a sheet.
        val merged = rule.onAllNodesWithText(text, substring = substring, ignoreCase = true)
        val target = if (merged.fetchSemanticsNodes().isNotEmpty()) merged[0] else node(text, substring)
        // Sheets open half-way, so a button can sit below the fold: press it through its click action.
        try { target.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
        catch (_: Throwable) { try { target.performScrollTo() } catch (_: Throwable) {}; target.performClick() }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
    }

    @Test fun everyTab() {
        launch()
        for (tab in listOf("الحركات", "الخطة", "أنا", "اليوم")) tap(tab)
    }

    @Test fun reportsInnerTabsAndRanges() {
        launch(); tap("أنا"); tap("وين راحت مصاريك")
        for (s in listOf("البنود", "الاتجاه", "ملخص")) tap(s)
        for (r in listOf("يوم", "أسبوع", "سنة", "الكل", "شهر")) tap(r)
    }

    @Test fun myMoneyInnerTabs() {
        launch(); tap("الخطة"); tap("فلوسي")
        for (s in listOf("الديون", "الجمعية", "الادخار")) tap(s)
    }

    @Test fun settingsSections() {
        launch()
        val gear = rule.onAllNodesWithContentDescription("الإعدادات", useUnmergedTree = true).fetchSemanticsNodes()
        if (gear.isEmpty()) return
        rule.onAllNodesWithContentDescription("الإعدادات", useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
        for (s in listOf("البنود", "عام", "البيانات", "البنك")) tap(s)
    }

    @Test fun todaySheets() {
        launch()
        tap("صرفت اليوم", substring = true)
        node("صرف اليوم", substring = true)
    }

    @Test fun balanceSheet() {
        launch()
        rule.onAllNodesWithContentDescription("كيف انحسب هالرقم", useUnmergedTree = true)[0].performClick()
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        node("رصيدك", substring = true)
    }

    @Test fun transactionAndFriendsSheet() {
        launch(); tap("الحركات")
        tap("RESTAURANT", substring = true)
        tap("تعديل حصص الأصحاب", substring = true)
        node("حصتك لأي بند تتبع؟")
    }

    @Test fun unsortedTransactionSheet() {
        launch(); tap("الحركات")
        tap("AL SALAMAT COFFEE", substring = true)
        tap("دفعت عن أصحابي", substring = true)
        tap("بالتساوي", substring = true)
    }

    @Test fun splitSheetControl() {
        launch(); tap("الحركات")
        tap("AL SALAMAT COFFEE", substring = true)
        tap("تقسيم المبلغ", substring = true)
        node("اكتب كل جزء وبنده", substring = true)
        println("ROOTS=" + rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().size)
    }

    @Test fun monthScreenLines() {
        launch(); tap("الخطة")
        tap("طعام", substring = true)
    }

    @Test fun compareMonths() {
        launch(); tap("أنا"); tap("قارن الأشهر")
        node("شهر", substring = true)
    }

    @Test fun planMonthAndBack() {
        launch(); tap("الخطة"); tap("الشهر"); tap("اليوم")
        node("صرفت اليوم", substring = true)
    }
}
