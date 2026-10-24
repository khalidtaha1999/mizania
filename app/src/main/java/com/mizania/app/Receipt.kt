package com.mizania.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF receipts and statements built from what the app recorded.
 * Every page says plainly that it comes from Mizania, not from the bank.
 */
object Receipt {

    private const val W = 420   // A5 width in points
    private const val H = 595   // A5 height in points
    private const val M = 32f   // margin

    private val pine = Color.rgb(0x16, 0x27, 0x1F)
    private val gold = Color.rgb(0xB0, 0x7F, 0x07)
    private val muted = Color.rgb(0x5F, 0x6B, 0x63)
    private val line = Color.rgb(0xD5, 0xDB, 0xD3)

    private fun stamp(t: Long) = SimpleDateFormat("d/M/yyyy  HH:mm", Locale.US).format(Date(t))
    private fun day(t: Long) = SimpleDateFormat("d/M/yyyy", Locale.US).format(Date(t))

    class Doc(
        val title: String,
        val subtitle: String,
        val bigAmount: String?,
        val rows: List<Pair<String, String>>,
        val tableHeader: List<String> = emptyList(),
        val table: List<List<String>> = emptyList(),
    )

    // ---------------- what goes on each receipt ----------------

    fun forTxn(ctx: Context, id: Long): Doc? {
        val db = Db.get(ctx)
        val t = db.txn(id) ?: return null
        val cat = db.category(t.categoryId)
        val acc = db.accounts().firstOrNull { it.id == t.accountId }
        val rows = ArrayList<Pair<String, String>>()
        rows += tr("التاريخ", "Date") to stamp(t.time)
        rows += tr("المبلغ", "Amount") to fmt(t.amount)
        rows += tr("الحركة", "Type") to if (t.income) tr("دخل", "Money in") else tr("صرف", "Money out")
        rows += (if (t.income) tr("من", "From") else tr("إلى / المزوّد", "To / provider")) to merchantLabel(t.merchant).ifEmpty { "—" }
        rows += tr("طريقة التحويل", "Method") to method(t)
        if (cat != null) rows += tr("البند", "Budget line") to cat.name
        db.debtForTxn(id)?.let { did -> db.debt(did)?.let { d -> rows += tr("على دين", "Debt") to "${d.person} (${directionLabel(d.owedToMe)})" } }
        db.jamiyaForTxn(id)?.let { jid ->
            db.jamiyas().firstOrNull { it.id == jid }?.let { j ->
                rows += tr("جمعية", "Jamiya") to j.name + tr("، شهر ", ", month ") + JamiyaMonths.label(j, JamiyaMonths.indexAt(j, t.time))
            }
        }
        db.goalForTxn(id)?.let { gid -> db.goals().firstOrNull { it.id == gid }?.let { g -> rows += tr("ادخار", "Savings") to g.name } }
        if (acc != null) rows += tr("الحساب", "Account") to (acc.label + if (t.acctNo.isNotEmpty()) "  ${t.acctNo}" else "")
        if (t.note.isNotBlank()) rows += tr("ملاحظة", "Note") to t.note
        rows += tr("رقم الحركة", "Transaction no.") to "#$id"
        return Doc(tr("إيصال حركة", "Transaction receipt"), t.title, (if (t.income) "+" else "") + fmt(t.amount), rows)
    }

    private fun method(t: Txn): String = when {
        t.manual -> tr("نقداً", "Cash")
        t.kind.contains("كليك") -> tr("كليك (CliQ)", "CliQ")
        t.kind.contains("حوالة") || t.kind.contains("تحويل") -> tr("حوالة بنكية", "Bank transfer")
        t.kind.contains("شراء") -> tr("شراء بالبطاقة", "Card purchase")
        t.kind.contains("سحب") -> tr("سحب من الصراف", "ATM withdrawal")
        t.kind.contains("راتب") -> tr("راتب", "Salary")
        t.kind.contains("إيداع") -> tr("إيداع", "Deposit")
        else -> t.kindText.ifEmpty { "—" }
    }

    fun forDebt(ctx: Context, id: Long): Doc? {
        val db = Db.get(ctx)
        val d = db.debt(id) ?: return null
        val rows = arrayListOf(
            tr("الشخص", "Person") to d.person,
            tr("الدين", "Debt") to if (d.owedToMe) tr("لي (هو مقترض مني)", "Owed to me (they borrowed from me)")
                else tr("عليّ (أنا المقترض)", "I owe (I borrowed)"),
            tr("المبلغ الأصلي", "Original amount") to fmt(d.amount),
        )
        if (d.added > 0) rows += tr("زيادات", "Added") to fmt(d.added)
        rows += (if (d.owedToMe) tr("استرددت", "Paid back") else tr("سدّدت", "Paid")) to fmt(d.paid)
        rows += tr("المتبقي", "Remaining") to fmt(kotlin.math.max(0.0, d.remaining))
        if (d.note.isNotBlank()) rows += tr("ملاحظة", "Note") to d.note
        rows += tr("تاريخ الدين", "Debt date") to day(d.createdAt)
        val table = db.debtEntries(id).sortedBy { it.time }.map { e ->
            listOf(
                day(e.time),
                if (e.note == Db.CORRECTION) tr("تعديل", "Correction") else entryLabel(d, e.kind) + if (e.txnId != null) tr(" (بنك)", " (bank)") else "",
                fmt(e.amount),
            )
        }
        return Doc(tr("كشف دين", "Debt statement"), d.person, fmt(kotlin.math.max(0.0, d.remaining)) + tr(" متبقٍ", " left"), rows,
            listOf(tr("التاريخ", "Date"), tr("البيان", "Details"), tr("المبلغ", "Amount")), table)
    }

    fun forJamiya(ctx: Context, id: Long): Doc? {
        val db = Db.get(ctx)
        val j = db.jamiyas().firstOrNull { it.id == id } ?: return null
        val paid = j.paidMonths.count { it in 0 until j.months }
        val rows = arrayListOf(
            tr("الجمعية", "Jamiya") to j.name,
            tr("القسط الشهري", "Monthly payment") to fmt(j.monthly),
            tr("عدد الأشهر", "Months") to "${j.months}",
            tr("دوري", "My turn") to tr("الشهر ${j.myTurn}", "Month ${j.myTurn}") + " (${JamiyaMonths.label(j, j.myTurn - 1)})",
            tr("دفعت", "Paid") to monthsText(paid) + tr("، ", ", ") + fmt(paid * j.monthly),
            tr("قبضت", "Received") to if (j.received) tr("نعم، ", "Yes, ") + fmt(j.monthly * j.months) else tr("ليس بعد", "Not yet"),
        )
        val table = (0 until j.months).map { i ->
            listOf(JamiyaMonths.label(j, i), if (i in j.paidMonths) tr("مدفوع", "Paid") else "—", if (i == j.myTurn - 1) tr("دوري", "My turn") else "")
        }
        return Doc(tr("كشف جمعية", "Jamiya statement"), j.name, fmt(paid * j.monthly) + tr(" مدفوع", " paid"), rows,
            listOf(tr("الشهر", "Month"), tr("الدفع", "Payment"), ""), table)
    }

    fun forCategory(ctx: Context, cat: Category?, range: Range, periodLabel: String): Doc {
        val db = Db.get(ctx)
        val list = db.txnsInCategory(cat?.id, range.from, range.to).sortedBy { it.time }
        val total = list.sumOf { it.amount }
        val noLine = tr("بدون بند", "No line")
        val rows = arrayListOf(tr("البند", "Budget line") to (cat?.name ?: noLine), tr("الفترة", "Period") to periodLabel,
            tr("عدد الحركات", "Transactions") to "${list.size}", tr("المجموع", "Total") to fmt(total))
        if (cat != null && cat.budget > 0) rows += tr("الميزانية", "Budget") to fmt(cat.budget)
        val table = list.map { listOf(day(it.time), it.title, fmt(it.amount)) }
        return Doc(tr("كشف بند", "Budget line statement"), cat?.name ?: noLine, fmt(total), rows,
            listOf(tr("التاريخ", "Date"), tr("الجهة", "Party"), tr("المبلغ", "Amount")), table)
    }

    // ---------------- drawing ----------------

    private class Fonts(ctx: Context) {
        val regular: Typeface = load(ctx, "fonts/plex-regular.ttf") ?: Typeface.DEFAULT
        val bold: Typeface = load(ctx, "fonts/plex-bold.ttf") ?: Typeface.DEFAULT_BOLD
        val numbers: Typeface = load(ctx, "fonts/readex.ttf") ?: bold

        private fun load(ctx: Context, path: String): Typeface? =
            try { Typeface.createFromAsset(ctx.assets, path) } catch (e: Exception) { null }
    }

    private fun paint(tf: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; this.color = color
    }

    /** Draws text in a box, right-to-left in Arabic; returns its height. */
    private fun text(c: Canvas, s: String, p: TextPaint, x: Float, y: Float, width: Float, align: Layout.Alignment): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width.toInt().coerceAtLeast(10))
            .setAlignment(align)
            .setTextDirection(if (Lang.ar) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setLineSpacing(0f, 1.15f)
            .build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    fun render(ctx: Context, doc: Doc, fileName: String): File {
        val f = Fonts(ctx)
        val pdf = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f
        val inner = W - 2 * M
        val made = SimpleDateFormat("d/M/yyyy HH:mm", Locale.US).format(Date())

        fun finish() {
            val cc = c ?: return
            val foot = tr("صادر من تطبيق «ميزانيتي» بتاريخ $made. ملخّص من رسائل البنك على الهاتف، وليس مستنداً رسمياً من البنك.",
                "Made with the My Budget app on $made. A summary of bank SMS on this phone, not an official bank document.")
            val lp = Paint().apply { color = line; strokeWidth = 0.8f }
            cc.drawLine(M, H - 52f, W - M, H - 52f, lp)
            text(cc, foot, paint(f.regular, 7.5f, muted), M, H - 46f, inner, Layout.Alignment.ALIGN_NORMAL)
            text(cc, tr("صفحة $pageNo", "Page $pageNo"), paint(f.regular, 7.5f, muted), M, H - 22f, inner, Layout.Alignment.ALIGN_OPPOSITE)
            pdf.finishPage(page!!)
        }

        fun newPage() {
            if (page != null) finish()
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page!!.canvas
            y = M
        }

        fun ensure(h: Float) { if (y + h > H - 64f) newPage() }

        newPage()
        val cc0 = c!!
        // Header band.
        val band = Paint().apply { color = pine }
        cc0.drawRect(0f, 0f, W.toFloat(), 96f, band)
        text(cc0, tr("ميزانيتي", "My Budget"), paint(f.bold, 10f, Color.rgb(0xF0, 0xBE, 0x45)), M, 18f, inner, Layout.Alignment.ALIGN_NORMAL)
        text(cc0, doc.title, paint(f.bold, 20f, Color.WHITE), M, 34f, inner, Layout.Alignment.ALIGN_NORMAL)
        text(cc0, doc.subtitle, paint(f.regular, 10f, Color.rgb(0xD6, 0xDD, 0xD8)), M, 66f, inner, Layout.Alignment.ALIGN_NORMAL)
        y = 112f
        doc.bigAmount?.let {
            y += text(cc0, it, paint(f.numbers, 26f, gold), M, y, inner, Layout.Alignment.ALIGN_NORMAL) + 10f
        }

        // Label / value rows.
        val labelP = paint(f.regular, 10f, muted)
        val valueP = paint(f.bold, 10.5f, pine)
        val lp = Paint().apply { color = line; strokeWidth = 0.6f }
        for ((k, v) in doc.rows) {
            ensure(28f)
            val cc = c!!
            // Label at the start side (right in Arabic), value at the end side.
            val h1 = text(cc, k, labelP, if (Lang.ar) M + inner * 0.55f else M, y + 6f, inner * 0.45f, Layout.Alignment.ALIGN_NORMAL)
            val h2 = text(cc, v, valueP, if (Lang.ar) M else M + inner * 0.45f, y + 6f, inner * 0.55f, Layout.Alignment.ALIGN_OPPOSITE)
            y += maxOf(h1, h2) + 12f
            cc.drawLine(M, y, W - M, y, lp)
        }

        // Table.
        if (doc.table.isNotEmpty()) {
            y += 16f
            val cols = doc.tableHeader.size.coerceAtLeast(doc.table.first().size)
            val colW = inner / cols
            // Column 0 sits at the start side, like the rest of the page (right in Arabic).
            fun colX(i: Int) = if (Lang.ar) W - M - (i + 1) * colW + 6f else M + i * colW + 6f
            val headP = paint(f.bold, 9.5f, Color.WHITE)
            val cellP = paint(f.regular, 9.5f, pine)
            fun header() {
                val cc = c!!
                cc.drawRect(M, y, W - M, y + 22f, Paint().apply { color = pine })
                doc.tableHeader.forEachIndexed { i, h ->
                    text(cc, h, headP, colX(i), y + 5f, colW - 12f, Layout.Alignment.ALIGN_NORMAL)
                }
                y += 22f
            }
            ensure(48f)
            header()
            doc.table.forEachIndexed { r, row ->
                if (y + 22f > H - 64f) { newPage(); header() }
                val cc = c!!
                if (r % 2 == 1) cc.drawRect(M, y, W - M, y + 20f, Paint().apply { color = Color.rgb(0xF1, 0xF4, 0xEF) })
                var rowH = 20f
                row.forEachIndexed { i, cell ->
                    val h = text(cc, cell, cellP, colX(i), y + 4f, colW - 12f, Layout.Alignment.ALIGN_NORMAL)
                    rowH = maxOf(rowH, h + 8f)
                }
                y += rowH
            }
        }
        finish()

        val dir = File(ctx.cacheDir, "receipts").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val out = File(dir, fileName)
        out.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return out
    }

    /**
     * Saves a rendered PDF to the phone: Downloads/Mizania on Android 10+, otherwise the app's
     * Documents folder. Returns where it went, for the message to the user.
     */
    fun save(ctx: Context, doc: Doc?, fileName: String): String? {
        doc ?: return null
        val file = render(ctx, doc, fileName)
        val name = fileName.removeSuffix(".pdf") + "-" +
            SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".pdf"
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/Mizania")
                }
                val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                tr("التنزيلات ← Mizania", "Downloads → Mizania")
            } else {
                val dir = File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "Mizania").apply { mkdirs() }
                file.copyTo(File(dir, name), overwrite = true)
                dir.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Opens the share sheet (WhatsApp, Drive, Files…) for a rendered PDF. */
    fun share(ctx: Context, doc: Doc?, fileName: String) {
        doc ?: return
        val file = render(ctx, doc, fileName)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, doc.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** "Share" and "Save to phone" side by side, for any receipt or statement. */
@Composable
fun ReceiptButtons(label: String, fileName: String, build: (Context) -> Receipt.Doc?) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Receipt.share(ctx, build(ctx), fileName) }, modifier = Modifier.weight(1f)) {
                Text(tr("مشاركة", "Share"))
            }
            OutlinedButton(
                onClick = {
                    val where = Receipt.save(ctx, build(ctx), fileName)
                    Toast.makeText(ctx, if (where != null) tr("حُفظ الملف في $where", "Saved to $where") else tr("تعذّر الحفظ", "Couldn't save"), Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.weight(1f),
            ) { Text(tr("حفظ على الجهاز", "Save to phone")) }
        }
    }
}
