package com.mizania.app

import android.provider.DocumentsContract
import android.content.Intent
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


    fun write(ctx: Context, uri: Uri, text: String) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("cannot open file")
    }

    fun read(ctx: Context, uri: Uri): String =
        ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: error("cannot open file")
}

/**
 * A backup file written by itself once a week into a folder the user picked (Documents, a memory card…).
 * The newest eight are kept; older automatic ones are deleted. Manual backups are never touched.
 */
object AutoBackup {
    private const val TREE = "autobackup_tree"
    private const val LAST = "autobackup_last"
    private const val ERROR = "autobackup_error"
    private const val PREFIX = "mizania-auto-"
    private const val KEEP = 8

    fun folder(db: Db): Uri? = db.setting(TREE, "").takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    fun last(db: Db): Long = db.setting(LAST, "0").toLongOrNull() ?: 0L
    fun error(db: Db): String = db.setting(ERROR, "")

    /** A short name for the folder, like "Documents/Mizania". */
    fun folderName(db: Db): String = folder(db)?.let {
        try { DocumentsContract.getTreeDocumentId(it).substringAfter(':').ifBlank { tr("الذاكرة الرئيسية", "Main storage") } }
        catch (e: Exception) { it.lastPathSegment ?: "" }
    } ?: ""

    fun choose(ctx: Context, db: Db, tree: Uri) {
        ctx.contentResolver.takePersistableUriPermission(tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        db.setSetting(TREE, tree.toString())
        run(ctx, db)
    }

    fun stop(ctx: Context, db: Db) {
        folder(db)?.let { u ->
            try { ctx.contentResolver.releasePersistableUriPermission(u,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        }
        db.setSetting(TREE, ""); db.setSetting(ERROR, "")
    }

    /** Called every night: writes one when a week has passed since the last. */
    fun runIfDue(ctx: Context, db: Db, now: Long = System.currentTimeMillis()) {
        if (folder(db) != null && now - last(db) >= 7 * Period.DAY - Period.HOUR) run(ctx, db, now)
    }

    fun run(ctx: Context, db: Db, now: Long = System.currentTimeMillis()): Boolean {
        val tree = folder(db) ?: return false
        return try {
            val r = ctx.contentResolver
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = PREFIX + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + ".json"
            val doc = DocumentsContract.createDocument(r, dir, "application/json", name) ?: error("cannot create file")
            Backup.write(ctx, doc, Backup.exportJson(db))
            prune(ctx, tree)
            db.setSetting(LAST, now.toString()); db.setSetting(ERROR, "")
            true
        } catch (e: Exception) {
            db.setSetting(ERROR, e.message ?: e.javaClass.simpleName)
            false
        }
    }

    private fun prune(ctx: Context, tree: Uri) {
        val r = ctx.contentResolver
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = ArrayList<Pair<String, String>>()
        r.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(PREFIX)) found += c.getString(0) to name
            }
        }
        found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            try { DocumentsContract.deleteDocument(r, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } catch (_: Exception) {}
        }
    }
}
