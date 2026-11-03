package com.mizania.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max

object Streak {
    /**
     * Days in a row, ending today, that day-to-day spending stayed within the daily limit.
     * Only days since the first transaction count; 0 when no daily budget is set.
     */
    fun current(db: Db): Int {
        val cats = db.categories()
        val daily = cats.filter { it.type == Db.DAILY }
        val limit = daily.sumOf { it.budget } / Budget.cycle(db).range.days
        if (limit <= 0) return 0
        val first = db.firstTxnTime() ?: return 0
        val today = Period.day()
        val from = max(Period.day(-89).from, Period.day(-((today.from - first) / Period.DAY).toInt()).from)
        val per = db.spentPerDay(from, today.to, daily.map { it.id }.toSet())
        var n = 0
        for (i in per.indices.reversed()) {
            if (per[i] <= limit + 0.001) n++ else break
        }
        return n
    }
}

/** A picture of the month to share: spent, income, what's left, the top lines and the streak. */
object MonthCard {
    private const val W = 1080
    private const val H = 1350

    private fun font(ctx: Context, path: String, fallback: Typeface): Typeface =
        try { Typeface.createFromAsset(ctx.assets, path) } catch (e: Exception) { fallback }

    private fun paint(tf: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; this.color = color
    }

    private fun text(c: Canvas, s: String, p: TextPaint, x: Float, y: Float, width: Float,
                     align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt())
            .setAlignment(align)
            .setTextDirection(if (Lang.ar) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    fun render(ctx: Context, db: Db, offset: Int): File {
        val ci = Budget.cycle(db, offset)
        val r = ci.range
        val cats = db.categories()
        val spent = db.spentByCategory(r.from, r.to)
        val out = spent.values.sum()
        val income = ci.salary + db.otherIncome(r.from, r.to)
        val top = cats.map { it to (spent[it.id] ?: 0.0) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(4)
        val streak = if (offset == 0) Streak.current(db) else 0

        val regular = font(ctx, "fonts/plex-regular.ttf", Typeface.DEFAULT)
        val bold = font(ctx, "fonts/plex-bold.ttf", Typeface.DEFAULT_BOLD)
        val numbers = font(ctx, "fonts/readex.ttf", bold)
        val white = 0xFFFFFFFF.toInt()
        val soft = 0xCCFFFFFF.toInt()

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFF7C5CFF.toInt(), 0xFF3B82F6.toInt(), Shader.TileMode.CLAMP)
        })
        val m = 80f
        val inner = W - 2 * m
        var y = 90f
        y += text(c, tr("ميزانيتي", "My Budget"), paint(bold, 34f, 0xFFF0BE45.toInt()), m, y, inner) + 6f
        y += text(c, tr("شهري", "My month") + "  " + Period.label(Period.MODE_MONTH, r), paint(bold, 52f, white), m, y, inner) + 50f

        y += text(c, tr("صرفت", "Spent"), paint(regular, 36f, soft), m, y, inner)
        y += text(c, fmt(out), paint(numbers, 96f, white), m, y, inner) + 30f

        if (income > 0) {
            val left = income - out
            val half = inner / 2
            val sx = if (Lang.ar) m + half else m
            val lx = if (Lang.ar) m else m + half
            text(c, tr("الدخل", "Income"), paint(regular, 32f, soft), sx, y, half)
            text(c, if (left >= 0) tr("تبقّى", "Left over") else tr("زيادة", "Over"), paint(regular, 32f, soft), lx, y, half)
            y += 46f
            text(c, fmt(income), paint(numbers, 48f, white), sx, y, half)
            text(c, fmt(kotlin.math.abs(left)), paint(numbers, 48f, if (left >= 0) 0xFF7CF5C8.toInt() else 0xFFFFC2CC.toInt()), lx, y, half)
            y += 90f
        }

        if (top.isNotEmpty()) {
            y += text(c, tr("أكثر ما صرفت عليه", "Where it went"), paint(bold, 36f, white), m, y, inner) + 20f
            val maxV = top.first().second
            for ((cat, v) in top) {
                // Name at the start side (right in Arabic), amount at the end side.
                text(c, "${cat.icon}  ${cat.name}", paint(regular, 34f, white), if (Lang.ar) m + inner * 0.38f else m, y, inner * 0.62f)
                text(c, fmt(v), paint(numbers, 34f, white), m, y, inner, Layout.Alignment.ALIGN_OPPOSITE)
                y += 52f
                val barW = (inner * (v / maxV)).toFloat()
                val bx = if (Lang.ar) W - m - barW else m
                c.drawRoundRect(RectF(m, y, W - m, y + 14f), 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
                c.drawRoundRect(RectF(bx, y, bx + barW, y + 14f), 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white })
                y += 44f
            }
        }

        if (streak >= 2) {
            y += 20f
            text(c, tr("🔥 ${daysText(streak)} متتالية ضمن حدّي اليومي", "🔥 ${daysText(streak)} in a row within my daily limit"),
                paint(bold, 38f, white), m, y, inner)
        }
        text(c, tr("صُنعت بتطبيق ميزانيتي", "Made with My Budget"), paint(regular, 28f, soft), m, H - 90f, inner, Layout.Alignment.ALIGN_CENTER)

        val dir = File(ctx.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "month.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    fun share(ctx: Context, db: Db, offset: Int = 0) {
        val file = render(ctx, db, offset)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, tr("مشاركة ملخص الشهر", "Share month summary"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
