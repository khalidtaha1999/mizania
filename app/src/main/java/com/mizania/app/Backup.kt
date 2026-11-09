package com.mizania.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Whole-database backup as one JSON file, plus a CSV of transactions for Excel. */
object Backup {
    private val tables = listOf(
        "categories", "txns", "rules", "senders", "settings", "alerts_sent", "accounts",
        "debts", "debt_entries", "goals", "goal_entries", "jamiya", "jamiya_paid", "balance_fix",
    )

    fun fileName(ext: String): String =
        "mizania-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + "." + ext

    fun exportJson(db: Db): String {
        val root = JSONObject()
        root.put("app", "mizania")
        root.put("version", 4)
        root.put("exported_at", System.currentTimeMillis())
        val r = db.readableDatabase
        for (t in tables) {
            val arr = JSONArray()
            r.rawQuery("SELECT * FROM $t", null).use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val name = c.getColumnName(i)
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                            else -> o.put(name, c.getString(i))
                        }
                    }
                    arr.put(o)
                }
            }
            root.put(t, arr)
        }
        return root.toString()
    }

    /** Replaces everything with the backup's content. Throws when the file is not a Mizania backup. */
    fun restoreJson(db: Db, json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "mizania") { "not a mizania backup" }
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            for (t in tables) {
                if (!root.has(t)) continue
                w.delete(t, null, null)
                val arr = root.getJSONArray(t)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val v = ContentValues()
                    for (k in o.keys()) {
                        when (val x = o.get(k)) {
                            JSONObject.NULL -> v.putNull(k)
                            is Int -> v.put(k, x.toLong())
                            is Long -> v.put(k, x)
                            is Double -> v.put(k, x)
                            is Boolean -> v.put(k, if (x) 1 else 0)
                            else -> v.put(k, x.toString())
                        }
                    }
                    w.insert(t, null, v)
                }
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun exportCsv(db: Db): String = csvOf(db, db.recent(1_000_000))

    /** The transactions of one period, for Excel. */
    fun exportCsv(db: Db, from: Long, to: Long, accountId: Long? = null): String =
        csvOf(db, db.txns(from, to).filter { accountId == null || it.accountId == accountId })

    private fun csvOf(db: Db, list: List<Txn>): String {
        val cats = db.categories().associate { it.id to it.name }
        val accs = db.accounts().associate { it.id to it.name }
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("﻿") // BOM so Excel shows Arabic correctly
        sb.append(tr("التاريخ,المبلغ,دخل/صرف,الجهة,نوع الحركة,البند,الحساب,ملاحظة\n", "Date,Amount,In/Out,Party,Type,Budget line,Account,Note\n"))
        for (t in list) {
            sb.append(df.format(Date(t.time))).append(',')
            sb.append(String.format(Locale.US, "%.3f", t.amount)).append(',')
            sb.append(if (t.income) tr("دخل", "In") else tr("صرف", "Out")).append(',')
            sb.append(q(merchantLabel(t.merchant))).append(',')
            sb.append(q(t.kindText)).append(',')
            sb.append(q(t.categoryId?.let { cats[it] } ?: "")).append(',')
            sb.append(q(t.accountId?.let { accs[it] } ?: "")).append(',')
            sb.append(q(t.note)).append('\n')
        }
        return sb.toString()
    }

    fun write(ctx: Context, uri: Uri, text: String) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("cannot open file")
    }

    fun read(ctx: Context, uri: Uri): String =
        ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: error("cannot open file")
}
