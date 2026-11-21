package com.mizania.app

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Renders screens to PNG files for design review (only when SHOTS_DIR is set, so normal test runs skip it).
 * Made-up data throughout.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShotsTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val out = System.getenv("SHOTS_DIR")

    private fun seed(lang: String, theme: String, bigText: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
            "android.permission.POST_NOTIFICATIONS")
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        val db = Db.get(app)
        db.setSetting("lang", lang); db.setSetting("theme", theme); db.setSetting("onboarded", "1")
        Weekly.setCap(db, 50.0)
        db.setSetting("big_text", if (bigText) "1" else "0")
        val acc = db.saveAccount(Account(0, "Main", "Bank"))
        db.addSender("TESTBANK", acc)
        db.categories().forEach { db.deleteCategory(it.id) }
        val rent = db.saveCategory(Category(0, if (lang == "ar") "إيجار" else "Rent", 275.0, Db.FIXED, "🏠", dueDay = 1))
        val net = db.saveCategory(Category(0, if (lang == "ar") "إنترنت" else "Internet", 25.0, Db.FIXED, "🌐",
            dueDay = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }.get(Calendar.DAY_OF_MONTH).coerceAtMost(28)))
        val save = db.saveCategory(Category(0, if (lang == "ar") "ادخار" else "Savings", 100.0, Db.RESERVE, "💰"))
        val food = db.saveCategory(Category(0, if (lang == "ar") "طعام" else "Food", 150.0, Db.DAILY, "🍔"))
        val fuel = db.saveCategory(Category(0, if (lang == "ar") "بنزين" else "Fuel", 60.0, Db.DAILY, "⛽"))
        var bal = 300.0
        fun t(daysAgo: Int, minutes: Int, amount: Double, income: Boolean, shop: String, kind: String, cat: Long?): Long {
            bal += if (income) amount else -amount
            val time = if (daysAgo == 0) System.currentTimeMillis() - minutes * 60_000L else Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, minutes % 60)
            }.timeInMillis
            return db.insertTxn(time, amount, income, shop, kind, cat, "", bal, "TESTBANK", "", "h$daysAgo-$minutes-$shop",
                false, acc, "1111*2222")
        }
        t(12, 1, 1000.0, true, "", "راتب", null)
        t(11, 2, 275.0, false, "LANDLORD", "كليك", rent)
        for (d in 10 downTo 1) { t(d, 3, 6.5, false, "GROCERY STORE", "شراء", food); if (d % 3 == 0) t(d, 4, 12.0, false, "FUEL STATION", "شراء", fuel) }
        t(0, 40, 1.35, false, "AL SALAMAT COFFEE", "شراء", null)
        t(0, 20, 4.25, false, "FALAFEL HOUSE", "شراء", food)
        Annual.add(db, if (lang == "ar") "ترخيص السيارة" else "Car licence", "🚗", 180.0, 3, 15)
    }

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shoot(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        scenario!!.onActivity { act ->
            val v = act.window.decorView
            val img = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(img))
            File(out!!).mkdirs()
            File(out, "$name.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun tap(text: String) {
        val n = rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
        if (n.fetchSemanticsNodes().isNotEmpty()) { n[0].performClick(); rule.waitForIdle() }
    }

    private fun run(lang: String, theme: String, big: Boolean, tag: String) {
        assumeTrue(out != null)
        seed(lang, theme, big)
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }.use {
            val ar = lang == "ar"
            shoot("$tag-today")
            tap(if (ar) "الحركات" else "Activity"); shoot("$tag-txns")
            tap(if (ar) "الخطة" else "Plan"); shoot("$tag-month")
            tap(if (ar) "فلوسي" else "My money"); shoot("$tag-money")
            tap(if (ar) "أنا" else "Me"); shoot("$tag-me")
            tap(if (ar) "وين راحت مصاريك" else "Where it went"); shoot("$tag-reports")
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle()
            tap(if (ar) "قارن الأشهر" else "Compare months"); shoot("$tag-compare")
        }
    }

    @Config(qualifiers = "w375dp-h812dp-xhdpi")
    @Test fun arDark() = run("ar", "dark", false, "ar-dark")

    @Config(qualifiers = "w375dp-h812dp-xhdpi")
    @Test fun arLight() = run("ar", "light", false, "ar-light")

    @Config(qualifiers = "w375dp-h812dp-xhdpi")
    @Test fun enDark() = run("en", "dark", false, "en-dark")

    @Config(qualifiers = "w375dp-h2200dp-xhdpi")
    @Test fun arDarkTall() = run("ar", "dark", false, "ar-tall")

    @Config(qualifiers = "w375dp-h2200dp-xhdpi")
    @Test fun arLightTall() = run("ar", "light", false, "ar-ltall")

    @Config(qualifiers = "w840dp-h1200dp-xhdpi")
    @Test fun arTablet() = run("ar", "dark", false, "ar-tablet")

    @Config(qualifiers = "w375dp-h812dp-xhdpi")
    @Test fun arBigText() = run("ar", "dark", true, "ar-big")
}
