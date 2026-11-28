package com.mizania.app

import android.app.Application
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

/** The three home-screen widgets fill in from the app's numbers; drawn to files when SHOTS_DIR is set. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetTest {
    private lateinit var app: Application
    private lateinit var db: Db
    private var acc = 0L
    private var bal = 900.0
    private var n = 0

    private fun at(daysAgo: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
    }.timeInMillis.coerceAtMost(System.currentTimeMillis() - 60_000)

    private fun t(daysAgo: Int, amount: Double, income: Boolean, kind: String, cat: Long?) {
        bal += if (income) amount else -amount; n++
        db.insertTxn(at(daysAgo), amount, income, if (income) "" else "SHOP $n", kind, cat, "", bal, "TESTBANK", "", "g$n", false, acc, "1111*2222")
    }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        db = Db.get(app)
        db.setSetting("lang", "ar"); Lang.apply("ar"); db.setSetting("onboarded", "1")
        acc = db.saveAccount(Account(0, "Main", "Test bank"))
        db.categories().forEach { db.deleteCategory(it.id) }
        val food = db.saveCategory(Category(0, "طعام", 150.0, Db.DAILY, "🍔", 0xFFE8743BL))
        val fuel = db.saveCategory(Category(0, "بنزين", 80.0, Db.DAILY, "🚗", 0xFF1E88E5L))
        val fun_ = db.saveCategory(Category(0, "ترفيه", 60.0, Db.DAILY, "🎮", 0xFF8E59D9L))
        val health = db.saveCategory(Category(0, "الصحة", 0.0, Db.DAILY, "💊", 0xFF2EA36AL))
        t(12, 1000.0, true, "راتب", null)
        t(6, 22.0, false, "شراء", food); t(5, 30.0, false, "شراء", fuel); t(3, 18.0, false, "شراء", fun_)
        t(1, 7.5, false, "شراء", health); t(0, 4.25, false, "شراء", null)
        Weekly.setCap(db, 50.0)
    }

    private fun draw(v: View, w: Int, h: Int, name: String) {
        val dir = System.getenv("SHOTS_DIR") ?: return
        val d = app.resources.displayMetrics.density
        val wp = (w * d).toInt(); val hp = (h * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(wp, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hp, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, wp, hp)
        val b = Bitmap.createBitmap(wp + 40, hp + 40, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(Color.parseColor("#2B3A55")); c.translate(20f, 20f)
        v.draw(c)
        File(dir).mkdirs()
        FileOutputStream(File(dir, "widget-$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(v: View, id: Int) = (v.findViewById<TextView>(id)).text.toString()

    @Test fun allThreeWidgetsFillIn() {
        val m = Shadows.shadowOf(AppWidgetManager.getInstance(app))
        val small = m.createWidget(AllowanceWidget::class.java, R.layout.widget_allowance)
        val week = m.createWidget(WeeklyWidget::class.java, R.layout.widget_week)
        val large = m.createWidget(AllowanceWidgetLarge::class.java, R.layout.widget_large)
        AllowanceWidget.refreshAll(app)

        val sv = m.getViewFor(small)
        assertTrue(text(sv, R.id.widget_title), text(sv, R.id.widget_title).contains("اليوم"))
        draw(sv, 250, 100, "small")

        val wv = m.getViewFor(week)
        assertTrue(text(wv, R.id.week_amount), text(wv, R.id.week_amount).contains("من 50.00"))
        draw(wv, 250, 130, "week")

        val lv = m.getViewFor(large)
        assertEquals(View.VISIBLE, lv.findViewById<View>(R.id.large_week).visibility)
        assertTrue(text(lv, R.id.line1_name).isNotBlank())
        assertTrue(text(lv, R.id.btn_sort), text(lv, R.id.btn_sort).contains("صنّف"))
        draw(lv, 330, 360, "large")

        // Dark theme: the white cards turn deep indigo.
        db.setSetting("theme", "dark")
        AllowanceWidget.refreshAll(app)
        draw(m.getViewFor(week), 250, 130, "week-dark")
        draw(m.getViewFor(large), 330, 360, "large-dark")
    }

    @Test fun weekWidgetSaysHowToTurnItOnWhenOff() {
        Weekly.setCap(db, 0.0)
        val m = Shadows.shadowOf(AppWidgetManager.getInstance(app))
        val week = m.createWidget(WeeklyWidget::class.java, R.layout.widget_week)
        AllowanceWidget.refreshAll(app)
        assertTrue(text(m.getViewFor(week), R.id.week_sub).contains("الإعدادات"))
    }
}
