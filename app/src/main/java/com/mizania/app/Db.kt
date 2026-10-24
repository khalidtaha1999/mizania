package com.mizania.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

/** [color] is ARGB; [dueDay] 1..28 = monthly payment day for reminders, 0 = none. */
data class Category(
    val id: Long, val name: String, val budget: Double, val type: Int,
    val icon: String = "", val color: Long = 0xFF5B6CFF, val dueDay: Int = 0,
)

data class GoalEntry(val id: Long, val amount: Double, val time: Long, val txnId: Long?, val note: String, val merchant: String)

data class Goal(val id: Long, val name: String, val target: Double, val saved: Double, val deadline: Long?, val icon: String)

/** A rotating savings circle: [monthly] paid each month for [months] months from [startMonth] ("yyyy-MM"). */
data class Jamiya(
    val id: Long, val name: String, val monthly: Double, val months: Int,
    val startMonth: String, val myTurn: Int, val paidMonths: Set<Int>, val received: Boolean,
)

/** Default emoji and color for the built-in budget names (Arabic or English). */
fun defaultLook(name: String): Pair<String, Long> {
    val n = name.lowercase()
    fun has(vararg k: String) = k.any { n.contains(it) }
    return when {
        has("إيجار", "rent") -> "🏠" to 0xFF5B6CFF
        has("جمعية", "jamiya") -> "🤝" to 0xFF8E59D9
        has("زوج", "spouse") -> "💝" to 0xFFE0559B
        has("كهرباء", "فواتير", "bill") -> "💡" to 0xFFF2A516
        has("دين", "ديون", "قسط", "أقساط", "debt", "loan", "instal") -> "💳" to 0xFFE5533D
        has("مونة", "أكل", "سوبرماركت", "مواد غذائية", "grocer", "food", "supermarket") -> "🛒" to 0xFF2EA36A
        has("مواصلات", "بنزين", "وقود", "transport", "fuel") -> "🚗" to 0xFF1E88E5
        has("طلعات", "مطاعم", "ترفيه", "outing", "restaurant", "dining") -> "☕" to 0xFF8D6E63
        has("صحة", "صيدلية", "دواء", "health", "pharmac") -> "💊" to 0xFF00ACC1
        has("سكن", "housing") -> "🏠" to 0xFF5B6CFF
        has("طوارئ", "emergenc") -> "🚑" to 0xFFD84343
        has("ادخار", "saving") -> "💰" to 0xFF26A69A
        else -> "📦" to 0xFF78909C
    }
}

data class Account(val id: Long, val name: String, val bank: String) {
    val label: String get() = if (bank.isBlank()) name else "$name · $bank"
}

data class Txn(
    val id: Long,
    val time: Long,
    val amount: Double,
    val income: Boolean,
    val merchant: String,
    val kind: String,
    val categoryId: Long?,
    val note: String,
    val balance: Double?,
    val sender: String,
    val body: String,
    val manual: Boolean,
    val accountId: Long?,
    val acctNo: String = "",
)

/** A salary deposit read from an SMS. */
data class Salary(val time: Long, val amount: Double)

/** One bank account's money as best known: [balance] null when the bank never told us. */
data class BankBalance(
    val accountId: Long,
    val acctNo: String,
    val balance: Double?,
    /** When the balance was last reported by the bank or set by hand. */
    val asOf: Long?,
    /** True when movements after the last reported balance were applied on top of it. */
    val estimated: Boolean,
    val lastSeen: Long,
)

data class Rule(val id: Long, val keyword: String, val categoryId: Long)

/** [owedToMe] true = "إلي" (someone owes me), false = "علي" (I owe someone). */
data class Debt(
    val id: Long,
    val person: String,
    val owedToMe: Boolean,
    val amount: Double,
    val note: String,
    val createdAt: Long,
    val paid: Double,
    val added: Double,
) {
    val total: Double get() = amount + added
    val remaining: Double get() = total - paid
    val closed: Boolean get() = remaining <= 0.0001
}

/** [kind] PAY = a repayment, ADD = the debt grew (borrowed / lent more). */
data class DebtEntry(
    val id: Long, val debtId: Long, val kind: Int, val amount: Double,
    val time: Long, val note: String, val txnId: Long?,
)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "mizania.db", null, 8) {

    companion object {
        /** Fixed monthly bill (rent, debt...). */
        const val FIXED = 0
        /** Day-to-day spending that feeds the daily allowance. */
        const val DAILY = 1
        /** Money put aside (emergency, savings). */
        const val RESERVE = 2

        const val PAY = 0
        const val ADD = 1

        /** Note on an entry that fixes a total by hand, so it is shown as "تعديل". */
        const val CORRECTION = "تعديل"

        @Volatile private var instance: Db? = null

        fun get(ctx: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(ctx.applicationContext).also {
                    instance = it
                    // Receivers and the widget can start the app without the activity: load the language here.
                    Lang.apply(it.setting("lang", Lang.SYSTEM))
                }
            }

        fun norm(address: String): String = address.trim().lowercase(Locale.ROOT)

        fun ruleKey(merchant: String): String = merchant.trim().uppercase(Locale.ROOT)

        private val brandSkip = setOf("AL", "EL", "THE", "CO", "LLC", "LTD", "STATION", "STATIONS",
            "ال", "محطة", "محطات", "شركة", "مؤسسة")
        private val brandGeneric = setOf("JORDAN", "AMMAN", "الأردن", "الاردن", "عمان", "CAFE", "COFFEE", "MARKET",
            "SUPERMARKET", "MINI", "RESTAURANT", "PHARMACY", "BAKERY", "STORE", "SHOP", "TRADING", "GENERAL", "GAS",
            "PETROL", "FUEL", "MALL", "CITY", "NEW", "GROUP", "INTERNATIONAL", "PAY", "WWW", "سوبرماركت", "مطعم",
            "مطاعم", "صيدلية", "مخبز", "مخابز", "مول", "سوق", "مكتبة")

        /**
         * The shop's brand without the branch: "MANASEER TAHQFEH" and "AL MANASEER MADEENAH" both give "MANASEER".
         * A generic first word keeps the next one too ("CAFE NERO ABDOUN" → "CAFE NERO").
         * A one-word name, or one that can't be split safely, stays whole.
         */
        private val articles = setOf("AL", "EL", "THE", "ال")

        /** The words of a name, without "AL" / "THE" in front of them. */
        private fun words(name: String): List<String> =
            ruleKey(name).split(Regex("[\\s\\-_/.,*#()]+")).filter { it.isNotEmpty() && it !in articles }

        /** Same word, allowing a typo or a different spelling in longer words ("MANASEER" / "MANASIR"). */
        private fun sameWord(a: String, b: String): Boolean {
            if (a == b) return true
            val n = minOf(a.length, b.length)
            val allowed = when { n >= 8 -> 2; n >= 5 -> 1; else -> 0 }
            if (allowed == 0 || kotlin.math.abs(a.length - b.length) > allowed) return false
            return distance(a, b) <= allowed
        }

        /** Edit distance between two strings. */
        private fun distance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                }
                prev = cur
            }
            return prev[b.length]
        }

        /**
         * Another branch of the same shop, decided without asking:
         * at least 3 words in common ("MANASEER GAS STATION TAHQFEH" / "MANASEER GAS STATION MADEENAH"),
         * or names that are almost the same letter for letter (85% or more).
         * One shared word is not enough: "BLACK HOUSE" and "BLACK CAT" stay apart.
         */
        fun sameVendor(a: String, b: String): Boolean {
            val wa = words(a); val wb = words(b)
            if (wa.isEmpty() || wb.isEmpty()) return false
            val shared = wa.toMutableList().let { left -> wb.count { w -> left.firstOrNull { sameWord(w, it) }?.also { left.remove(it) } != null } }
            if (shared >= 3) return true
            val ja = wa.joinToString(" "); val jb = wb.joinToString(" ")
            val longest = maxOf(ja.length, jb.length)
            if (longest < 6) return false
            return 1.0 - distance(ja, jb).toDouble() / longest >= 0.85
        }

        fun brandKey(merchant: String): String {
            val full = ruleKey(merchant)
            val words = full.split(Regex("[\\s\\-_/.,*#()]+")).filter { it.isNotEmpty() }
            val sig = words.dropWhile { it in brandSkip }
            if (sig.size < 2) return full
            val first = sig[0]
            val key = when {
                first.length >= 3 && first !in brandGeneric && first.none { it.isDigit() } -> first
                sig.size >= 3 && first.none { it.isDigit() } -> first + " " + sig[1]
                else -> return full
            }
            return if (full.contains(key)) key else full
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "budget REAL NOT NULL DEFAULT 0, ctype INTEGER NOT NULL DEFAULT 1, sort INTEGER NOT NULL DEFAULT 0, " +
                "icon TEXT NOT NULL DEFAULT '', color INTEGER NOT NULL DEFAULT 0, due_day INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE txns(id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, amount REAL NOT NULL, " +
                "income INTEGER NOT NULL, merchant TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL DEFAULT '', " +
                "category_id INTEGER, note TEXT NOT NULL DEFAULT '', balance REAL, sender TEXT NOT NULL DEFAULT '', " +
                "body TEXT NOT NULL DEFAULT '', hash TEXT UNIQUE, ignored INTEGER NOT NULL DEFAULT 0, " +
                "manual INTEGER NOT NULL DEFAULT 0, account_id INTEGER, acct_no TEXT NOT NULL DEFAULT '', " +
                "to_acct TEXT NOT NULL DEFAULT '', bal_acct TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX txns_time ON txns(time)")
        db.execSQL(
            "CREATE TABLE rules(id INTEGER PRIMARY KEY AUTOINCREMENT, keyword TEXT NOT NULL UNIQUE, " +
                "category_id INTEGER NOT NULL)"
        )
        db.execSQL("CREATE TABLE senders(address TEXT PRIMARY KEY, account_id INTEGER)")
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
        db.execSQL("CREATE TABLE alerts_sent(k TEXT PRIMARY KEY)")
        createV2Tables(db)
        createV3Tables(db)
        createV4Tables(db)
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 -> v2: accounts and debts. Existing senders and transactions go to the first account.
            db.execSQL("ALTER TABLE txns ADD COLUMN account_id INTEGER")
            db.execSQL("ALTER TABLE senders ADD COLUMN account_id INTEGER")
            createV2Tables(db)
            val acc = insertDefaultAccount(db)
            db.execSQL("UPDATE txns SET account_id=?", arrayOf<Any>(acc))
            db.execSQL("UPDATE senders SET account_id=?", arrayOf<Any>(acc))
        }
        if (oldVersion < 3) {
            // v2 -> v3: a look and a due day per budget line, savings goals, jamiya.
            db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE categories ADD COLUMN due_day INTEGER NOT NULL DEFAULT 0")
            db.rawQuery("SELECT id, name FROM categories", null).use { c ->
                while (c.moveToNext()) {
                    val (icon, color) = defaultLook(c.getString(1))
                    db.execSQL("UPDATE categories SET icon=?, color=? WHERE id=?", arrayOf<Any>(icon, color, c.getLong(0)))
                }
            }
            createV3Tables(db)
        }
        if (oldVersion < 4) {
            // v3 -> v4: which bank account each SMS was about, and hand-set balances.
            db.execSQL("ALTER TABLE txns ADD COLUMN acct_no TEXT NOT NULL DEFAULT ''")
            createV4Tables(db)
            db.rawQuery("SELECT id, body FROM txns WHERE body <> ''", null).use { c ->
                while (c.moveToNext()) {
                    val no = SmsParser.acctNo(c.getString(1))
                    if (no.isNotEmpty()) db.execSQL("UPDATE txns SET acct_no=? WHERE id=?", arrayOf<Any>(no, c.getLong(0)))
                }
            }
            // Months now start on the day the salary lands.
            db.execSQL("INSERT OR REPLACE INTO settings(k, v) VALUES('cycle_mode', 'salary')")
        }
        if (oldVersion < 5) {
            // v4 -> v5: transfers between your own accounts, and which account a balance belongs to.
            // Stored messages are read again on the next start (Processor.repairIfNeeded).
            db.execSQL("ALTER TABLE txns ADD COLUMN to_acct TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE txns ADD COLUMN bal_acct TEXT NOT NULL DEFAULT ''")
            // Hand-set balances were keyed by the old spelling ("300*9876"); use the one-spelling form.
            db.rawQuery("SELECT id, acct_no FROM balance_fix", null).use { c ->
                while (c.moveToNext()) {
                    db.execSQL("UPDATE balance_fix SET acct_no=? WHERE id=?",
                        arrayOf<Any>(SmsParser.canonical(c.getString(1) ?: ""), c.getLong(0)))
                }
            }
        }
        if (oldVersion < 6) {
            // v5 -> v6: savings goal entries and jamiya months can come from a booked transaction.
            // (Tables made by an earlier step of this same upgrade already have the column.)
            if (!hasColumn(db, "goal_entries", "txn_id")) db.execSQL("ALTER TABLE goal_entries ADD COLUMN txn_id INTEGER")
            if (!hasColumn(db, "jamiya_paid", "txn_id")) db.execSQL("ALTER TABLE jamiya_paid ADD COLUMN txn_id INTEGER")
        }
        if (oldVersion < 6) {
            // Rules saved from badly read messages hold the whole SMS instead of a shop name: drop them.
            db.execSQL(
                "DELETE FROM rules WHERE length(keyword) > 40 OR keyword LIKE '%حساب%' OR keyword LIKE '%بمبلغ%' " +
                    "OR keyword LIKE '%الرصيد%' OR keyword LIKE '%JOD%' OR keyword LIKE '%*%' OR keyword LIKE '%" + "\n" + "%'"
            )
        }
        if (oldVersion < 7) upgradeV7(db)
        // v8 only added the breakdown tables of a version that was rolled back. Nothing to do: the number
        // stays at 8 so phones that had that version open their data without a downgrade error.
    }

    private fun upgradeV7(db: SQLiteDatabase) {
        // v6 -> v7: a goal entry can be a correction ("تعديل") rather than money put in or taken out.
        if (!hasColumn(db, "goal_entries", "note")) db.execSQL("ALTER TABLE goal_entries ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            while (c.moveToNext()) if (c.getString(1) == column) return@use true
            false
        }

    private fun createV4Tables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE balance_fix(id INTEGER PRIMARY KEY AUTOINCREMENT, account_id INTEGER NOT NULL, " +
                "acct_no TEXT NOT NULL DEFAULT '', balance REAL NOT NULL, time INTEGER NOT NULL)"
        )
    }

    private fun createV3Tables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE goals(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, target REAL NOT NULL, " +
                "deadline INTEGER, icon TEXT NOT NULL DEFAULT '🎯')"
        )
        db.execSQL(
            "CREATE TABLE goal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT, goal_id INTEGER NOT NULL, " +
                "amount REAL NOT NULL, time INTEGER NOT NULL, txn_id INTEGER, note TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE jamiya(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, monthly REAL NOT NULL, " +
                "months INTEGER NOT NULL, start_month TEXT NOT NULL, my_turn INTEGER NOT NULL, " +
                "received INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE jamiya_paid(jamiya_id INTEGER NOT NULL, month_index INTEGER NOT NULL, txn_id INTEGER, " +
                "PRIMARY KEY(jamiya_id, month_index))"
        )
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, bank TEXT NOT NULL DEFAULT '')")
        db.execSQL(
            "CREATE TABLE debts(id INTEGER PRIMARY KEY AUTOINCREMENT, person TEXT NOT NULL, owed_to_me INTEGER NOT NULL, " +
                "amount REAL NOT NULL, note TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE debt_entries(id INTEGER PRIMARY KEY AUTOINCREMENT, debt_id INTEGER NOT NULL, " +
                "kind INTEGER NOT NULL, amount REAL NOT NULL, time INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', " +
                "txn_id INTEGER)"
        )
        db.execSQL("CREATE INDEX debt_entries_debt ON debt_entries(debt_id)")
    }

    private fun insertDefaultAccount(db: SQLiteDatabase): Long =
        db.insert("accounts", null, ContentValues().apply { put("name", tr("الحساب 1", "Account 1")); put("bank", "") })

    private fun seed(db: SQLiteDatabase) {
        // Generic starting lines with no amounts: each person sets their own budget.
        val defaults = listOf(
            Triple(tr("السكن والإيجار", "Housing & rent"), 0.0, FIXED),
            Triple(tr("الفواتير (كهرباء، ماء، إنترنت، هاتف)", "Bills (electricity, water, internet, phone)"), 0.0, FIXED),
            Triple(tr("الأقساط والديون", "Instalments & debts"), 0.0, FIXED),
            Triple(tr("جمعية", "Jamiya"), 0.0, FIXED),
            Triple(tr("المواد الغذائية والسوبرماركت", "Groceries & supermarket"), 0.0, FIXED),
            Triple(tr("المواصلات والوقود", "Transport & fuel"), 0.0, DAILY),
            Triple(tr("المطاعم والترفيه", "Restaurants & outings"), 0.0, DAILY),
            Triple(tr("الصحة والصيدلية", "Health & pharmacy"), 0.0, DAILY),
            Triple(tr("مشتريات متنوعة", "Miscellaneous"), 0.0, DAILY),
            Triple(tr("الطوارئ", "Emergencies"), 0.0, RESERVE),
            Triple(tr("ادخار", "Savings"), 0.0, RESERVE),
        )
        var billsId = -1L
        defaults.forEachIndexed { i, (name, budget, type) ->
            val (icon, color) = defaultLook(name)
            val id = db.insert("categories", null, ContentValues().apply {
                put("name", name); put("budget", budget); put("ctype", type); put("sort", i)
                put("icon", icon); put("color", color)
            })
            if (i == 1) billsId = id
        }
        // Jordan's mobile and internet providers go to bills out of the box.
        for (k in listOf("ZAIN", "ORANGE", "UMNIAH")) {
            db.insert("rules", null, ContentValues().apply { put("keyword", k); put("category_id", billsId) })
        }
        db.insert("settings", null, ContentValues().apply { put("k", "salary"); put("v", "0") })
        db.insert("settings", null, ContentValues().apply { put("k", "cycle_day"); put("v", "1") })
        db.insert("settings", null, ContentValues().apply { put("k", "cycle_mode"); put("v", "salary") })
        insertDefaultAccount(db)
    }

    // ---------- settings ----------

    fun setting(key: String, def: String): String =
        readableDatabase.rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) ?: def else def
        }

    fun setSetting(key: String, value: String) {
        writableDatabase.insertWithOnConflict("settings", null,
            ContentValues().apply { put("k", key); put("v", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun cycleDay(): Int = (setting("cycle_day", "1").toIntOrNull() ?: 1).coerceIn(1, 28)

    fun salary(): Double = setting("salary", "0").toDoubleOrNull() ?: 0.0

    /** The user has no salary: months run on a fixed day and salary is hidden everywhere. */
    fun noSalary(): Boolean = setting("no_salary", "0") == "1"

    // ---------- accounts ----------

    fun accounts(): List<Account> =
        readableDatabase.rawQuery("SELECT id,name,bank FROM accounts ORDER BY id", null).use { c ->
            val out = ArrayList<Account>()
            while (c.moveToNext()) out += Account(c.getLong(0), c.getString(1), c.getString(2) ?: "")
            out
        }

    fun saveAccount(a: Account): Long {
        val v = ContentValues().apply { put("name", a.name); put("bank", a.bank) }
        return if (a.id <= 0) writableDatabase.insert("accounts", null, v)
        else {
            writableDatabase.update("accounts", v, "id=?", arrayOf(a.id.toString()))
            a.id
        }
    }

    /** Removes the account and its senders; its transactions stay, without an account. */
    fun deleteAccount(id: Long) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            w.delete("senders", "account_id=?", arrayOf(id.toString()))
            w.execSQL("UPDATE txns SET account_id=NULL WHERE account_id=?", arrayOf<Any>(id))
            w.delete("accounts", "id=?", arrayOf(id.toString()))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** How many transactions belong to the account (for the delete confirmation). */
    fun accountTxnCount(id: Long): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM txns WHERE account_id=?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /**
     * Deletes the account and everything that came from it: its transactions, what they booked into
     * debts, goals and the jamiya, its balance corrections and settings, and its senders.
     */
    fun deleteAccountWithData(id: Long) {
        val w = writableDatabase
        val ids = "SELECT id FROM txns WHERE account_id=$id"
        w.beginTransaction()
        try {
            w.execSQL("DELETE FROM debt_entries WHERE txn_id IN ($ids)")
            w.execSQL("DELETE FROM goal_entries WHERE txn_id IN ($ids)")
            w.execSQL("DELETE FROM jamiya_paid WHERE txn_id IN ($ids)")
            w.execSQL("DELETE FROM txns WHERE account_id=?", arrayOf<Any>(id))
            w.delete("balance_fix", "account_id=?", arrayOf(id.toString()))
            w.execSQL("DELETE FROM settings WHERE k LIKE ?", arrayOf<Any>("excl:$id:%"))
            w.delete("senders", "account_id=?", arrayOf(id.toString()))
            w.delete("accounts", "id=?", arrayOf(id.toString()))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    // ---------- categories ----------

    fun categories(): List<Category> =
        readableDatabase.rawQuery(
            "SELECT id,name,budget,ctype,icon,color,due_day FROM categories ORDER BY ctype, sort, id", null
        ).use { c ->
            val out = ArrayList<Category>()
            while (c.moveToNext()) {
                val name = c.getString(1)
                val look = defaultLook(name)
                out += Category(
                    c.getLong(0), name, c.getDouble(2), c.getInt(3),
                    icon = c.getString(4).let { if (it.isNullOrEmpty()) look.first else it },
                    color = c.getLong(5).let { if (it == 0L) look.second else it },
                    dueDay = c.getInt(6),
                )
            }
            out
        }

    fun category(id: Long?): Category? = if (id == null) null else categories().firstOrNull { it.id == id }

    fun saveCategory(cat: Category): Long {
        val v = ContentValues().apply {
            put("name", cat.name); put("budget", cat.budget); put("ctype", cat.type)
            put("icon", cat.icon); put("color", cat.color); put("due_day", cat.dueDay)
        }
        return if (cat.id <= 0) {
            v.put("sort", 1000)
            writableDatabase.insert("categories", null, v)
        } else {
            writableDatabase.update("categories", v, "id=?", arrayOf(cat.id.toString()))
            cat.id
        }
    }

    fun deleteCategory(id: Long) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            w.execSQL("UPDATE txns SET category_id=NULL WHERE category_id=?", arrayOf<Any>(id))
            w.delete("rules", "category_id=?", arrayOf(id.toString()))
            w.delete("categories", "id=?", arrayOf(id.toString()))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    // ---------- transactions ----------

    fun insertTxn(
        time: Long, amount: Double, income: Boolean, merchant: String, kind: String,
        categoryId: Long?, note: String, balance: Double?, sender: String, body: String,
        hash: String?, manual: Boolean, accountId: Long?, acctNo: String = "",
        toAcct: String = "", balAcct: String = "", ignored: Boolean = false,
    ): Long {
        val v = ContentValues().apply {
            put("time", time); put("amount", amount); put("income", if (income) 1 else 0)
            put("merchant", merchant); put("kind", kind)
            if (categoryId != null) put("category_id", categoryId) else putNull("category_id")
            put("note", note)
            if (balance != null) put("balance", balance) else putNull("balance")
            put("sender", sender); put("body", body)
            if (hash != null) put("hash", hash) else putNull("hash")
            put("manual", if (manual) 1 else 0)
            if (accountId != null) put("account_id", accountId) else putNull("account_id")
            put("acct_no", acctNo); put("to_acct", toAcct); put("bal_acct", balAcct)
            put("ignored", if (ignored) 1 else 0)
        }
        return writableDatabase.insertWithOnConflict("txns", null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private val txnCols = "id,time,amount,income,merchant,kind,category_id,note,balance,sender,body,manual,account_id,acct_no"

    private fun readTxn(c: Cursor) = Txn(
        id = c.getLong(0),
        time = c.getLong(1),
        amount = c.getDouble(2),
        income = c.getInt(3) == 1,
        merchant = c.getString(4) ?: "",
        kind = c.getString(5) ?: "",
        categoryId = if (c.isNull(6)) null else c.getLong(6),
        note = c.getString(7) ?: "",
        balance = if (c.isNull(8)) null else c.getDouble(8),
        sender = c.getString(9) ?: "",
        body = c.getString(10) ?: "",
        manual = c.getInt(11) == 1,
        accountId = if (c.isNull(12)) null else c.getLong(12),
        acctNo = c.getString(13) ?: "",
    )

    private fun queryTxns(where: String, args: Array<String>, tail: String = "ORDER BY time DESC"): List<Txn> =
        readableDatabase.rawQuery("SELECT $txnCols FROM txns WHERE ignored=0 AND $where $tail", args).use { c ->
            val out = ArrayList<Txn>()
            while (c.moveToNext()) out += readTxn(c)
            out
        }

    fun txn(id: Long): Txn? = queryTxns("id=?", arrayOf(id.toString())).firstOrNull()

    /** When the first counted transaction happened, or null when there is none. */
    fun firstTxnTime(): Long? =
        readableDatabase.rawQuery("SELECT MIN(time) FROM txns WHERE ignored=0", null)
            .use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    fun txns(from: Long, to: Long): List<Txn> = queryTxns("time>=? AND time<?", arrayOf(from.toString(), to.toString()))

    /** Spending under one budget line in [from, to), newest first. [catId] null = no budget line yet. */
    fun txnsInCategory(catId: Long?, from: Long, to: Long): List<Txn> =
        if (catId == null) queryTxns("income=0 AND category_id IS NULL AND time>=? AND time<?", arrayOf(from.toString(), to.toString()))
        else queryTxns("income=0 AND category_id=? AND time>=? AND time<?", arrayOf(catId.toString(), from.toString(), to.toString()))

    fun recent(limit: Int): List<Txn> = queryTxns("1=1", arrayOf(), "ORDER BY time DESC LIMIT $limit")

    /** Spends with no budget line and not booked against a debt. */
    fun pending(): List<Txn> = queryTxns(
        "income=0 AND category_id IS NULL AND id NOT IN (SELECT txn_id FROM debt_entries WHERE txn_id IS NOT NULL)",
        arrayOf()
    )

    fun updateTxn(id: Long, categoryId: Long?, note: String) {
        val v = ContentValues().apply {
            if (categoryId != null) put("category_id", categoryId) else putNull("category_id")
            put("note", note)
        }
        writableDatabase.update("txns", v, "id=?", arrayOf(id.toString()))
    }

    /** Hidden from every list and total, but kept so a re-import does not bring it back. */
    fun ignoreTxn(id: Long) {
        writableDatabase.execSQL("UPDATE txns SET ignored=1 WHERE id=?", arrayOf<Any>(id))
        writableDatabase.delete("debt_entries", "txn_id=?", arrayOf(id.toString()))
        writableDatabase.delete("jamiya_paid", "txn_id=?", arrayOf(id.toString()))
        unlinkSaving(id)
    }

    /**
     * Transactions marked "not spending", newest first. Moves between your own accounts are left out:
     * those come back by removing the name from "your names in transfers".
     */
    fun ignoredTxns(limit: Int = 300): List<Txn> =
        readableDatabase.rawQuery(
            "SELECT $txnCols FROM txns WHERE ignored=1 AND to_acct='' ORDER BY time DESC LIMIT $limit", null
        ).use { c ->
            val out = ArrayList<Txn>()
            while (c.moveToNext()) out += readTxn(c)
            out
        }.filter { !isOwnName(it.merchant) }

    /** Puts a "not spending" transaction back: it counts again and waits to be sorted if it has no line. */
    fun unignoreTxn(id: Long) {
        writableDatabase.execSQL("UPDATE txns SET ignored=0 WHERE id=?", arrayOf<Any>(id))
    }

    /** Money in between [from, to) that is not salary (CliQ received, deposits, transfers in). */
    fun otherIncome(from: Long, to: Long): Double =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM txns WHERE ignored=0 AND income=1 AND kind<>'راتب' AND time>=? AND time<?",
            arrayOf(from.toString(), to.toString())
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }

    /** Spend per category between [from, to). Key null = not yet classified. */
    fun spentByCategory(from: Long, to: Long): Map<Long?, Double> =
        readableDatabase.rawQuery(
            "SELECT category_id, SUM(amount) FROM txns WHERE ignored=0 AND income=0 AND time>=? AND time<? GROUP BY category_id",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            val out = HashMap<Long?, Double>()
            while (c.moveToNext()) out[if (c.isNull(0)) null else c.getLong(0)] = c.getDouble(1)
            out
        }

    /** Every salary deposit, oldest first. */
    fun salaries(): List<Salary> =
        readableDatabase.rawQuery(
            "SELECT time, amount FROM txns WHERE ignored=0 AND income=1 AND kind='راتب' ORDER BY time", null
        ).use { c ->
            val out = ArrayList<Salary>()
            while (c.moveToNext()) out += Salary(c.getLong(0), c.getDouble(1))
            out
        }

    /** One change to one bank account's money, in time order. */
    private class Ev(
        val time: Long, val seq: Long, val delta: Double, val absolute: Double?,
        val txnId: Long, val amount: Double, val income: Boolean,
        /** A transaction whose money-in/out may be corrected from the balance. */
        val flippable: Boolean,
    )

    /**
     * Every bank account's movements: each transaction moves money out of or into its account
     * (both legs for a transfer between your accounts), and a reported or hand-set balance
     * replaces the running figure. Ignored transactions still count: the money did move.
     */
    private fun balanceEvents(): Map<Pair<Long, String>, List<Ev>> {
        val events = HashMap<Pair<Long, String>, MutableList<Ev>>()
        fun add(key: Pair<Long, String>, e: Ev) { events.getOrPut(key) { ArrayList() } += e }
        readableDatabase.rawQuery(
            "SELECT id, account_id, acct_no, to_acct, bal_acct, time, amount, income, balance, kind, body FROM txns " +
                "WHERE account_id IS NOT NULL AND manual=0", null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val acc = c.getLong(1)
                val from = c.getString(2) ?: ""
                val to = c.getString(3) ?: ""
                val balAcct = (c.getString(4) ?: "").ifEmpty { from }
                val time = c.getLong(5)
                val amt = c.getDouble(6)
                val income = c.getInt(7) == 1
                val bal = if (c.isNull(8)) null else c.getDouble(8)
                val kind = c.getString(9) ?: ""
                // When the SMS says "صادرة" or "واردة", that wins; the balance only decides the rest.
                val stated = SmsParser.statedDirection(c.getString(10) ?: "") != null
                if (to.isNotEmpty()) {
                    add(acc to from, Ev(time, id * 2, -amt, if (balAcct == from) bal else null, id, amt, false, false))
                    add(acc to to, Ev(time, id * 2 + 1, amt, if (balAcct == to) bal else null, id, amt, true, false))
                } else {
                    add(acc to from, Ev(time, id * 2, if (income) amt else -amt, if (balAcct == from) bal else null,
                        id, amt, income, kind != "راتب" && amt > 0 && !stated))
                    if (bal != null && balAcct != from) add(acc to balAcct, Ev(time, id * 2 + 1, 0.0, bal, id, 0.0, false, false))
                }
            }
        }
        readableDatabase.rawQuery("SELECT account_id, acct_no, balance, time, id FROM balance_fix", null).use { c ->
            while (c.moveToNext()) {
                // Hand-set balances come after any transaction at the same moment.
                add(c.getLong(0) to (c.getString(1) ?: ""),
                    Ev(c.getLong(3), Long.MAX_VALUE / 2 + c.getLong(4), 0.0, c.getDouble(2), -1, 0.0, false, false))
            }
        }
        for (l in events.values) l.sortWith(compareBy<Ev> { it.time }.thenBy { it.seq })
        return events
    }

    /**
     * Money in each bank account: the latest balance the bank reported (or you typed in),
     * plus or minus anything that moved after it.
     */
    fun bankBalances(): List<BankBalance> {
        val out = ArrayList<BankBalance>()
        for ((key, list) in balanceEvents()) {
            var bal: Double? = null
            var asOf: Long? = null
            var estimated = false
            for (e in list) {
                when {
                    e.absolute != null -> { bal = e.absolute; asOf = e.time; estimated = false }
                    bal != null -> { bal += e.delta; estimated = true }
                }
            }
            out += BankBalance(key.first, key.second, bal, asOf, estimated, list.maxOf { it.time })
        }
        return out.sortedWith(compareBy<BankBalance> { it.accountId }.thenByDescending { it.lastSeen })
    }

    /**
     * Total money in the accounts you count, as it stood just before [time].
     * Null when no account had a known balance by then.
     */
    fun totalBalanceAt(time: Long): Double? {
        var total: Double? = null
        for ((key, list) in balanceEvents()) {
            if (isExcluded(key.first, key.second)) continue
            var bal: Double? = null
            for (e in list) {
                if (e.time >= time) break
                bal = e.absolute ?: bal?.plus(e.delta)
            }
            if (bal != null) total = (total ?: 0.0) + bal
        }
        return total
    }

    /**
     * Decides money in or out from the balance, whatever the SMS wording says:
     * if the balance went up by the amount since the previous known balance of the same
     * bank account, it was money in; if it went down by the amount, money out.
     * Salaries and transfers between your accounts are left alone. Returns the ids changed.
     */
    fun reconcileDirections(): List<Long> {
        val flips = ArrayList<Pair<Long, Boolean>>()
        for (list in balanceEvents().values) {
            var prev: Double? = null
            for (e in list) {
                var delta = e.delta
                if (e.flippable && e.absolute != null && prev != null) {
                    val d = e.absolute - prev
                    val shouldBeIn = when {
                        kotlin.math.abs(d - e.amount) <= 0.006 -> true
                        kotlin.math.abs(d + e.amount) <= 0.006 -> false
                        else -> null
                    }
                    if (shouldBeIn != null && shouldBeIn != e.income) {
                        flips += e.txnId to shouldBeIn
                        delta = if (shouldBeIn) e.amount else -e.amount
                    }
                }
                prev = e.absolute ?: prev?.plus(delta)
            }
        }
        if (flips.isEmpty()) return emptyList()
        val w = writableDatabase
        w.beginTransaction()
        try {
            for ((id, toIncome) in flips) {
                val kind = readableDatabase.rawQuery("SELECT kind FROM txns WHERE id=?", arrayOf(id.toString()))
                    .use { if (it.moveToFirst()) it.getString(0) ?: "" else "" }
                val newKind = if (toIncome) when (kind) {
                    "كليك" -> "كليك وارد"; "حوالة صادرة" -> "حوالة واردة"; "خصم", "حركة", "شراء" -> "إيداع"; else -> kind
                } else when (kind) {
                    "كليك وارد" -> "كليك"; "حوالة واردة" -> "حوالة صادرة"; "إيداع" -> "خصم"; else -> kind
                }
                w.execSQL(
                    "UPDATE txns SET income=?, kind=?, category_id=CASE WHEN ?=1 THEN NULL ELSE category_id END WHERE id=?",
                    arrayOf<Any>(if (toIncome) 1 else 0, newKind, if (toIncome) 1 else 0, id),
                )
                w.delete("debt_entries", "txn_id=?", arrayOf(id.toString()))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return flips.map { it.first }
    }

    // ---------- your own names (transfers to yourself at another bank or wallet) ----------

    fun ownNames(): List<String> =
        setting("own_names", "").split("|").map { it.trim() }.filter { it.isNotEmpty() }

    fun isOwnName(name: String): Boolean {
        val n = ruleKey(name)
        return n.isNotEmpty() && ownNames().any { ruleKey(it) == n }
    }

    /** Remembers [name] as you, and hides every transfer to or from it as "between your accounts". */
    fun addOwnName(name: String) {
        if (name.isBlank() || isOwnName(name)) return
        setSetting("own_names", (ownNames() + name.trim()).joinToString("|"))
        markOwnTransfers()
    }

    fun removeOwnName(name: String) {
        setSetting("own_names", ownNames().filter { ruleKey(it) != ruleKey(name) }.joinToString("|"))
        writableDatabase.execSQL(
            "UPDATE txns SET ignored=0 WHERE manual=0 AND to_acct='' AND UPPER(TRIM(merchant))=?",
            arrayOf<Any>(ruleKey(name)),
        )
    }

    fun markOwnTransfers() {
        for (n in ownNames()) {
            writableDatabase.execSQL(
                "UPDATE txns SET ignored=1, category_id=NULL WHERE manual=0 AND UPPER(TRIM(merchant))=?",
                arrayOf<Any>(ruleKey(n)),
            )
        }
    }

    /**
     * Re-reads stored bank messages with the current SMS reader. [arrived] maps a message hash to
     * when it reached the phone; messages not found there keep their time.
     */
    /**
     * True when this sender's real transactions say "قيد" (Arab Bank does): then a message from it without
     * "قيد" or a deposit is a notice, not money moving. Other banks are left alone.
     */
    fun senderUsesQaid(sender: String): Boolean =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM txns WHERE sender=? AND body LIKE '%قيد%'", arrayOf(sender))
            .use { it.moveToFirst() && it.getInt(0) >= 3 }

    fun reparseAll(arrived: Map<String, Long>) {
        data class Old(
            val id: Long, val body: String, val hash: String?, val cat: Long?, val income: Boolean, val time: Long,
            val accountId: Long?, val sender: String,
        )
        val rows = ArrayList<Old>()
        readableDatabase.rawQuery(
            "SELECT id, body, hash, category_id, income, time, account_id, sender FROM txns WHERE manual=0 AND body<>''", null
        ).use { c ->
            while (c.moveToNext()) {
                rows += Old(c.getLong(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getLong(3),
                    c.getInt(4) == 1, c.getLong(5), if (c.isNull(6)) null else c.getLong(6), c.getString(7) ?: "")
            }
        }
        val qaid = rows.map { it.sender }.distinct().associateWith { senderUsesQaid(it) }
        val notices = ArrayList<Triple<Old, String, Double>>()
        val w = writableDatabase
        w.beginTransaction()
        try {
            for (o in rows) {
                val r = SmsParser.parse(o.body)?.takeIf { qaid[o.sender] != true || SmsParser.isPosted(o.body) }
                if (r == null) {
                    // Not a money movement after all (an offer, a reminder, a balance notice): hide it.
                    w.execSQL("UPDATE txns SET ignored=1, category_id=NULL WHERE id=?", arrayOf<Any>(o.id))
                    w.delete("debt_entries", "txn_id=?", arrayOf(o.id.toString()))
                    val n = SmsParser.balanceNotice(o.body)
                    if (n != null && o.accountId != null) notices += Triple(o, n[0], n[1].toDouble())
                    continue
                }
                val own = r.internal || isOwnName(r.merchant)
                val cat = if (r.income || own) null else o.cat ?: matchRule(r.merchant)
                val v = ContentValues().apply {
                    put("amount", r.amount); put("income", if (r.income) 1 else 0)
                    put("kind", r.kind); put("merchant", r.merchant); put("acct_no", r.acctNo)
                    put("to_acct", r.toAcct); put("bal_acct", r.balAcct)
                    if (own) put("ignored", 1)
                    if (r.balance != null) put("balance", r.balance) else putNull("balance")
                    if (cat != null) put("category_id", cat) else putNull("category_id")
                    put("time", o.hash?.let { arrived[it] } ?: o.time)
                }
                w.update("txns", v, "id=?", arrayOf(o.id.toString()))
                if (r.income != o.income) w.delete("debt_entries", "txn_id=?", arrayOf(o.id.toString()))
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        for ((o, acct, bal) in notices) {
            setBalance(o.accountId ?: continue, acct, bal, o.hash?.let { arrived[it] } ?: o.time)
        }
    }

    /** A fixed bill or savings line marked "done" for the month starting at [cycleFrom]. */
    fun isPaid(catId: Long, cycleFrom: Long) = setting("paid:$catId:$cycleFrom", "0") == "1"

    fun setPaid(catId: Long, cycleFrom: Long, paid: Boolean) = setSetting("paid:$catId:$cycleFrom", if (paid) "1" else "0")

    /** A bank account left out of "your balance" (e.g. an old card). */
    fun isExcluded(accountId: Long, acctNo: String) = setting("excl:$accountId:$acctNo", "0") == "1"

    fun setExcluded(accountId: Long, acctNo: String, excluded: Boolean) =
        setSetting("excl:$accountId:$acctNo", if (excluded) "1" else "0")

    /**
     * Removes a balance that was read from the SMS that arrived at [time] with [body]: any saved balance
     * within two minutes of it equal to one of the numbers in the text.
     */
    fun dropBalanceFromSms(time: Long, body: String) {
        val numbers = Regex("\\d[\\d,]*\\.?\\d*").findAll(body).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()
        if (numbers.isEmpty()) return
        val drop = ArrayList<Long>()
        readableDatabase.rawQuery(
            "SELECT id, balance FROM balance_fix WHERE time BETWEEN ? AND ?",
            arrayOf((time - 120_000).toString(), (time + 120_000).toString()),
        ).use { c -> while (c.moveToNext()) if (numbers.any { kotlin.math.abs(it - c.getDouble(1)) < 0.0005 }) drop += c.getLong(0) }
        drop.forEach { writableDatabase.delete("balance_fix", "id=?", arrayOf(it.toString())) }
    }

    fun setBalance(accountId: Long, acctNo: String, balance: Double, time: Long = System.currentTimeMillis()) {
        val dup = readableDatabase.rawQuery(
            "SELECT 1 FROM balance_fix WHERE account_id=? AND acct_no=? AND time=? LIMIT 1",
            arrayOf(accountId.toString(), acctNo, time.toString())
        ).use { it.moveToFirst() }
        if (dup) return
        writableDatabase.insert("balance_fix", null, ContentValues().apply {
            put("account_id", accountId); put("acct_no", acctNo); put("balance", balance)
            put("time", time)
        })
    }

    // ---------- rules ----------

    fun rules(): List<Rule> =
        readableDatabase.rawQuery("SELECT id,keyword,category_id FROM rules ORDER BY keyword", null).use { c ->
            val out = ArrayList<Rule>()
            while (c.moveToNext()) out += Rule(c.getLong(0), c.getString(1), c.getLong(2))
            out
        }

    /** Only a shop or provider name makes a rule, never message text. */
    fun validRuleKey(keyword: String): Boolean {
        val k = ruleKey(keyword)
        return k.length in 2..40 && !k.contains("\n") && !k.contains("حساب") && !k.contains("بمبلغ") &&
            !k.contains("الرصيد") && !k.contains("JOD") && !k.contains("*")
    }

    fun addRule(keyword: String, categoryId: Long) {
        val k = ruleKey(keyword)
        if (!validRuleKey(k)) return
        writableDatabase.insertWithOnConflict("rules", null,
            ContentValues().apply { put("keyword", k); put("category_id", categoryId) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateRule(id: Long, keyword: String, categoryId: Long) {
        val k = ruleKey(keyword)
        if (!validRuleKey(k)) return
        writableDatabase.delete("rules", "keyword=? AND id<>?", arrayOf(k, id.toString()))
        writableDatabase.update("rules", ContentValues().apply { put("keyword", k); put("category_id", categoryId) },
            "id=?", arrayOf(id.toString()))
    }

    /**
     * Takes a transaction off its budget line, back to "to sort", and out of any goal, debt or jamiya.
     * The shop's rule that books it into that line goes too, or it would come straight back.
     * Returns true when a rule was removed.
     */
    fun unbook(txnId: Long): Boolean {
        val t = txn(txnId) ?: return false
        val cat = t.categoryId ?: return false
        unlinkAll(txnId)
        updateTxn(txnId, null, t.note)
        val key = ruleKey(t.merchant)
        val drop = if (key.isEmpty()) emptyList() else rules().filter { it.categoryId == cat && key.contains(it.keyword) }
        drop.forEach { deleteRule(it.id) }
        return drop.isNotEmpty()
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("rules", "id=?", arrayOf(id.toString()))
    }

    /** Longest matching keyword wins, so "ZAIN CASH" can beat "ZAIN". */
    fun matchRule(text: String): Long? {
        val t = ruleKey(text)
        if (t.isEmpty()) return null
        val rs = rules()
        rs.filter { t.contains(it.keyword) }.maxByOrNull { it.keyword.length }?.let { return it.categoryId }
        // Another branch: 3+ words in common, or almost the same name.
        return rs.filter { sameVendor(t, it.keyword) }.maxByOrNull { it.keyword.length }?.categoryId
    }

    /**
     * A rule for a name that starts the same way ("MANASEER TAHQFEH" for "MANASEER MADEENAH").
     * Only a suggestion to confirm: "BLACK HOUSE" and "BLACK CAT" also start the same way.
     */
    fun similarRule(merchant: String): Rule? {
        val t = ruleKey(merchant)
        val b = brandKey(t)
        if (b == t) return null
        return rules().filter { it.keyword != t && brandKey(it.keyword) == b }.maxByOrNull { it.keyword.length }
    }

    /** After a new rule is saved, classify the waiting transactions it matches. Returns how many. */
    fun applyRulesToPending(): Int {
        var n = 0
        for (t in pending()) {
            val cat = matchRule(t.merchant) ?: continue
            updateTxn(t.id, cat, t.note)
            n++
        }
        return n
    }

    // ---------- watched senders ----------

    /** Watched sender address (normalized) -> account id. */
    fun senderAccounts(): Map<String, Long?> =
        readableDatabase.rawQuery("SELECT address, account_id FROM senders ORDER BY address", null).use { c ->
            val out = LinkedHashMap<String, Long?>()
            while (c.moveToNext()) out[c.getString(0)] = if (c.isNull(1)) null else c.getLong(1)
            out
        }

    fun senders(): List<String> = senderAccounts().keys.toList()

    fun addSender(address: String, accountId: Long) {
        val a = norm(address)
        if (a.isEmpty()) return
        writableDatabase.insertWithOnConflict("senders", null,
            ContentValues().apply { put("address", a); put("account_id", accountId) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeSender(address: String) {
        writableDatabase.delete("senders", "address=?", arrayOf(norm(address)))
    }

    // ---------- debts ----------

    fun debts(): List<Debt> =
        readableDatabase.rawQuery(
            "SELECT d.id, d.person, d.owed_to_me, d.amount, d.note, d.created_at, " +
                "COALESCE((SELECT SUM(amount) FROM debt_entries e WHERE e.debt_id=d.id AND e.kind=0),0), " +
                "COALESCE((SELECT SUM(amount) FROM debt_entries e WHERE e.debt_id=d.id AND e.kind=1),0) " +
                "FROM debts d ORDER BY d.created_at DESC",
            null
        ).use { c ->
            val out = ArrayList<Debt>()
            while (c.moveToNext()) {
                out += Debt(c.getLong(0), c.getString(1), c.getInt(2) == 1, c.getDouble(3),
                    c.getString(4) ?: "", c.getLong(5), c.getDouble(6), c.getDouble(7))
            }
            out
        }

    fun debt(id: Long): Debt? = debts().firstOrNull { it.id == id }

    fun saveDebt(id: Long, person: String, owedToMe: Boolean, amount: Double, note: String): Long {
        val v = ContentValues().apply {
            put("person", person); put("owed_to_me", if (owedToMe) 1 else 0)
            put("amount", amount); put("note", note)
        }
        return if (id <= 0) {
            v.put("created_at", System.currentTimeMillis())
            writableDatabase.insert("debts", null, v)
        } else {
            writableDatabase.update("debts", v, "id=?", arrayOf(id.toString()))
            id
        }
    }

    fun deleteDebt(id: Long) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            w.delete("debt_entries", "debt_id=?", arrayOf(id.toString()))
            w.delete("debts", "id=?", arrayOf(id.toString()))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun debtEntries(debtId: Long): List<DebtEntry> =
        readableDatabase.rawQuery(
            "SELECT id,debt_id,kind,amount,time,note,txn_id FROM debt_entries WHERE debt_id=? ORDER BY time DESC",
            arrayOf(debtId.toString())
        ).use { c ->
            val out = ArrayList<DebtEntry>()
            while (c.moveToNext()) {
                out += DebtEntry(c.getLong(0), c.getLong(1), c.getInt(2), c.getDouble(3), c.getLong(4),
                    c.getString(5) ?: "", if (c.isNull(6)) null else c.getLong(6))
            }
            out
        }

    fun addDebtEntry(debtId: Long, kind: Int, amount: Double, time: Long, note: String, txnId: Long?): Long {
        val v = ContentValues().apply {
            put("debt_id", debtId); put("kind", kind); put("amount", amount); put("time", time); put("note", note)
            if (txnId != null) put("txn_id", txnId) else putNull("txn_id")
        }
        return writableDatabase.insert("debt_entries", null, v)
    }

    fun deleteDebtEntry(id: Long) {
        writableDatabase.delete("debt_entries", "id=?", arrayOf(id.toString()))
    }

    /** The debt a bank transaction was booked against, if any. */
    fun debtForTxn(txnId: Long): Long? =
        readableDatabase.rawQuery("SELECT debt_id FROM debt_entries WHERE txn_id=? LIMIT 1", arrayOf(txnId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

    /**
     * Books a transaction against a debt (or unbooks it with null).
     * Money out to a debt I owe = repayment; money out to a debt owed to me = I lent more, and the reverse for money in.
     */
    fun linkTxnToDebt(t: Txn, debtId: Long?) {
        writableDatabase.delete("debt_entries", "txn_id=?", arrayOf(t.id.toString()))
        if (debtId == null) return
        val d = debt(debtId) ?: return
        val kind = if (t.income == d.owedToMe) PAY else ADD
        addDebtEntry(debtId, kind, t.amount, t.time, t.merchant.ifEmpty { t.kind }, t.id)
    }

    // ---------- goals ----------

    fun goals(): List<Goal> =
        readableDatabase.rawQuery(
            "SELECT g.id, g.name, g.target, COALESCE((SELECT SUM(amount) FROM goal_entries e WHERE e.goal_id=g.id),0), " +
                "g.deadline, g.icon FROM goals g ORDER BY g.id",
            null
        ).use { c ->
            val out = ArrayList<Goal>()
            while (c.moveToNext()) {
                out += Goal(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3),
                    if (c.isNull(4)) null else c.getLong(4), c.getString(5) ?: "🎯")
            }
            out
        }

    fun saveGoal(id: Long, name: String, target: Double, deadline: Long?, icon: String): Long {
        val v = ContentValues().apply {
            put("name", name); put("target", target); put("icon", icon)
            if (deadline != null) put("deadline", deadline) else putNull("deadline")
        }
        return if (id <= 0) writableDatabase.insert("goals", null, v)
        else { writableDatabase.update("goals", v, "id=?", arrayOf(id.toString())); id }
    }

    fun deleteGoal(id: Long) {
        writableDatabase.delete("goal_entries", "goal_id=?", arrayOf(id.toString()))
        writableDatabase.delete("goals", "id=?", arrayOf(id.toString()))
    }

    /** Positive adds to the goal, negative takes out. */
    fun addToGoal(id: Long, amount: Double, note: String = "") {
        writableDatabase.insert("goal_entries", null, ContentValues().apply {
            put("goal_id", id); put("amount", amount); put("time", System.currentTimeMillis()); put("note", note)
        })
    }

    /** Sets what a goal holds to [value] with one correction line (not counted as put in or taken out). */
    fun setGoalSaved(id: Long, value: Double) {
        val now = goals().firstOrNull { it.id == id }?.saved ?: return
        val diff = value - now
        if (kotlin.math.abs(diff) >= 0.0005) addToGoal(id, diff, CORRECTION)
    }

    /** A goal's history, newest first; [label] names the transaction it came from, if any. */
    fun goalEntries(goalId: Long): List<GoalEntry> =
        readableDatabase.rawQuery(
            "SELECT e.id, e.amount, e.time, e.txn_id, e.note, t.merchant FROM goal_entries e " +
                "LEFT JOIN txns t ON t.id = e.txn_id WHERE e.goal_id=? ORDER BY e.time DESC, e.id DESC",
            arrayOf(goalId.toString())
        ).use { c ->
            val out = ArrayList<GoalEntry>()
            while (c.moveToNext()) {
                out += GoalEntry(c.getLong(0), c.getDouble(1), c.getLong(2), if (c.isNull(3)) null else c.getLong(3),
                    c.getString(4) ?: "", c.getString(5) ?: "")
            }
            out
        }

    fun deleteGoalEntry(id: Long) {
        writableDatabase.delete("goal_entries", "id=?", arrayOf(id.toString()))
    }

    /** Sets what has been paid back on a debt to [value] with one correction line. */
    fun setDebtPaid(id: Long, value: Double) {
        val d = debt(id) ?: return
        val diff = value - d.paid
        if (kotlin.math.abs(diff) >= 0.0005) addDebtEntry(id, PAY, diff, System.currentTimeMillis(), CORRECTION, null)
    }

    /** A budget line that is money put aside toward goals (named ادخار / توفير / Savings). */
    fun isSavings(cat: Category?): Boolean = cat != null &&
        listOf("ادخار", "توفير", "حصالة", "saving").any { cat.name.lowercase().contains(it) }

    /** The goal a transaction was saved into, if any. */
    fun goalForTxn(txnId: Long): Long? =
        readableDatabase.rawQuery("SELECT goal_id FROM goal_entries WHERE txn_id=? LIMIT 1", arrayOf(txnId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Puts a transaction's amount into a goal (moving it if it was in another one). */
    fun linkSaving(txnId: Long, goalId: Long) {
        val t = txn(txnId) ?: return
        unlinkSaving(txnId)
        writableDatabase.insert("goal_entries", null, ContentValues().apply {
            put("goal_id", goalId); put("amount", t.amount); put("time", t.time); put("txn_id", txnId)
        })
    }

    fun unlinkSaving(txnId: Long) {
        writableDatabase.delete("goal_entries", "txn_id=?", arrayOf(txnId.toString()))
    }

    /** Spending booked under a savings line that has not gone into any goal yet. */
    fun unlinkedSavings(): List<Txn> {
        val ids = categories().filter { isSavings(it) }.map { it.id }
        if (ids.isEmpty()) return emptyList()
        return queryTxns(
            "income=0 AND category_id IN (${ids.joinToString(",")}) AND id NOT IN " +
                "(SELECT txn_id FROM goal_entries WHERE txn_id IS NOT NULL)",
            arrayOf(),
        )
    }

    /**
     * Keeps goals in step with a transaction's budget line: booked under a savings line, it goes
     * into [goalId] (or stays where it is, or goes to the only goal); booked elsewhere, it leaves.
     */
    fun syncSaving(txnId: Long, goalId: Long? = null) {
        val t = txn(txnId) ?: return
        if (t.income || !isSavings(category(t.categoryId))) { unlinkSaving(txnId); return }
        val target = goalId ?: goalForTxn(txnId) ?: goals().singleOrNull()?.id ?: return
        linkSaving(txnId, target)
    }

    /** A budget line for paying off what you owe (قسط الدين, دين…). */
    fun isDebtLine(cat: Category?): Boolean = cat != null &&
        listOf("دين", "ديون", "قسط", "أقساط", "اقساط", "debt", "loan", "instal").any { cat.name.lowercase().contains(it) }

    /** Debts you owe that are still open. */
    fun openDebtsIOwe(): List<Debt> = debts().filter { !it.owedToMe && !it.closed }

    /**
     * Booked under a debt line, a spend becomes a repayment of [debtId] (or the debt it already
     * pays, or your only open debt). Moving it to another line takes the repayment back off.
     */
    fun syncDebt(txnId: Long, debtId: Long? = null) {
        val t = txn(txnId) ?: return
        val current = debtForTxn(txnId)
        if (t.income || !isDebtLine(category(t.categoryId))) {
            if (current != null && debtId == null) linkTxnToDebt(t, null)
            return
        }
        val target = debtId ?: current ?: openDebtsIOwe().singleOrNull()?.id ?: return
        if (target != current) linkTxnToDebt(t, target)
    }

    /** A budget line for the jamiya (savings circle). */
    fun isJamiyaLine(cat: Category?): Boolean = cat != null &&
        (cat.name.contains("جمعية") || cat.name.lowercase().contains("jamiya"))

    fun jamiyaForTxn(txnId: Long): Long? =
        readableDatabase.rawQuery("SELECT jamiya_id FROM jamiya_paid WHERE txn_id=? LIMIT 1", arrayOf(txnId.toString()))
            .use { if (it.moveToFirst()) it.getLong(0) else null }

    /**
     * Booked under the jamiya line, a spend ticks the month it was paid in [jamiyaId]
     * (or the one it already ticked, or your only jamiya). Moving it elsewhere unticks that month.
     */
    fun syncJamiya(txnId: Long, jamiyaId: Long? = null) {
        val t = txn(txnId) ?: return
        val current = jamiyaForTxn(txnId)
        writableDatabase.delete("jamiya_paid", "txn_id=?", arrayOf(txnId.toString()))
        if (t.income || !isJamiyaLine(category(t.categoryId))) return
        val all = jamiyas()
        val j = all.firstOrNull { it.id == (jamiyaId ?: current) } ?: all.singleOrNull() ?: return
        val i = JamiyaMonths.indexAt(j, t.time)
        if (i !in 0 until j.months) return
        writableDatabase.insertWithOnConflict("jamiya_paid", null, ContentValues().apply {
            put("jamiya_id", j.id); put("month_index", i); put("txn_id", txnId)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Does booking under [cat] put the money somewhere (a goal, a debt, a jamiya)? */
    fun needsLink(cat: Category?, income: Boolean): Boolean =
        !income && (isSavings(cat) || isDebtLine(cat) || isJamiyaLine(cat))

    /** Takes a transaction out of any goal, debt or jamiya. */
    fun unlinkAll(txnId: Long) {
        unlinkSaving(txnId)
        writableDatabase.delete("debt_entries", "txn_id=?", arrayOf(txnId.toString()))
        writableDatabase.delete("jamiya_paid", "txn_id=?", arrayOf(txnId.toString()))
    }

    /** Where a transaction's money went, in words, for the confirmation line. Null when nowhere. */
    fun linkSummary(txnId: Long): String? {
        val t = txn(txnId) ?: return null
        goalForTxn(txnId)?.let { g -> goals().firstOrNull { it.id == g }?.let { return tr("أُضيف ${fmt(t.amount)} إلى هدف «${it.name}»", "Added ${fmt(t.amount)} to the goal “${it.name}”") } }
        debtForTxn(txnId)?.let { d -> debt(d)?.let { return tr("احتُسب ${fmt(t.amount)} سداداً من دين ${it.person}", "Counted ${fmt(t.amount)} as a repayment on ${it.person}'s debt") } }
        jamiyaForTxn(txnId)?.let { j ->
            jamiyas().firstOrNull { it.id == j }?.let {
                return tr("سُجّل شهر ${JamiyaMonths.label(it, JamiyaMonths.indexAt(it, t.time))} مدفوعاً في «${it.name}»",
                    "Marked ${JamiyaMonths.label(it, JamiyaMonths.indexAt(it, t.time))} as paid in “${it.name}”")
            }
        }
        return null
    }

    /** Links every already-booked savings, debt and jamiya payment (used once after the update). */
    fun syncAllLinks() {
        val lines = categories().filter { isSavings(it) || isDebtLine(it) || isJamiyaLine(it) }.map { it.id }
        if (lines.isEmpty()) return
        for (t in queryTxns("income=0 AND category_id IN (${lines.joinToString(",")})", arrayOf())) syncLinks(t.id)
    }

    /** All three links at once, after a transaction's budget line was set or changed. */
    fun syncLinks(txnId: Long, goalId: Long? = null, debtId: Long? = null, jamiyaId: Long? = null) {
        syncSaving(txnId, goalId)
        syncDebt(txnId, debtId)
        syncJamiya(txnId, jamiyaId)
    }

    // ---------- jamiya ----------

    fun jamiyas(): List<Jamiya> {
        val paid = HashMap<Long, MutableSet<Int>>()
        readableDatabase.rawQuery("SELECT jamiya_id, month_index FROM jamiya_paid", null).use { c ->
            while (c.moveToNext()) paid.getOrPut(c.getLong(0)) { HashSet() }.add(c.getInt(1))
        }
        return readableDatabase.rawQuery(
            "SELECT id,name,monthly,months,start_month,my_turn,received FROM jamiya ORDER BY id", null
        ).use { c ->
            val out = ArrayList<Jamiya>()
            while (c.moveToNext()) {
                out += Jamiya(c.getLong(0), c.getString(1), c.getDouble(2), c.getInt(3), c.getString(4),
                    c.getInt(5), paid[c.getLong(0)] ?: emptySet(), c.getInt(6) == 1)
            }
            out
        }
    }

    fun saveJamiya(j: Jamiya): Long {
        val v = ContentValues().apply {
            put("name", j.name); put("monthly", j.monthly); put("months", j.months)
            put("start_month", j.startMonth); put("my_turn", j.myTurn); put("received", if (j.received) 1 else 0)
        }
        return if (j.id <= 0) writableDatabase.insert("jamiya", null, v)
        else { writableDatabase.update("jamiya", v, "id=?", arrayOf(j.id.toString())); j.id }
    }

    fun deleteJamiya(id: Long) {
        writableDatabase.delete("jamiya_paid", "jamiya_id=?", arrayOf(id.toString()))
        writableDatabase.delete("jamiya", "id=?", arrayOf(id.toString()))
    }

    fun setJamiyaPaid(id: Long, monthIndex: Int, paid: Boolean) {
        if (paid) {
            writableDatabase.insertWithOnConflict("jamiya_paid", null, ContentValues().apply {
                put("jamiya_id", id); put("month_index", monthIndex)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            writableDatabase.delete("jamiya_paid", "jamiya_id=? AND month_index=?", arrayOf(id.toString(), monthIndex.toString()))
        }
    }

    fun setJamiyaReceived(id: Long, received: Boolean) {
        writableDatabase.execSQL("UPDATE jamiya SET received=? WHERE id=?", arrayOf<Any>(if (received) 1 else 0, id))
    }

    // ---------- small stats ----------

    /** Category ids by how often they were picked lately, most used first. */
    fun topCategories(limit: Int): List<Long> =
        readableDatabase.rawQuery(
            "SELECT category_id, COUNT(*) n FROM txns WHERE category_id IS NOT NULL AND ignored=0 " +
                "GROUP BY category_id ORDER BY n DESC LIMIT $limit", null
        ).use { c ->
            val out = ArrayList<Long>()
            while (c.moveToNext()) out += c.getLong(0)
            out
        }

    /** Spend per day in [from, to), keyed by day index from [from]. */
    // ---------- one-off spends spread over the month ----------

    /** null = not asked yet, true = spread over the rest of the month, false = it was today's spending. */
    fun spreadChoice(txnId: Long): Boolean? = when (setting("spread:$txnId", "")) {
        "1" -> true
        "0" -> false
        else -> null
    }

    fun setSpread(txnId: Long, spread: Boolean) = setSetting("spread:$txnId", if (spread) "1" else "0")

    /** Transactions spread over the month: they don't count against the day they happened. */
    fun spreadIds(): Set<Long> =
        readableDatabase.rawQuery("SELECT k FROM settings WHERE k LIKE 'spread:%' AND v='1'", null).use { c ->
            val out = HashSet<Long>()
            while (c.moveToNext()) c.getString(0).removePrefix("spread:").toLongOrNull()?.let { out += it }
            out
        }

    // ---------- debt plans ----------

    /** The monthly installment planned for a debt, or null when there is no plan. */
    fun debtPlan(debtId: Long): Double? = setting("debtplan:$debtId", "").toDoubleOrNull()?.takeIf { it > 0 }

    fun setDebtPlan(debtId: Long, monthly: Double?) = setSetting("debtplan:$debtId", monthly?.takeIf { it > 0 }?.toString() ?: "")

    /** Repayments on a debt in [from, to), corrections left out. */
    fun debtPaidIn(debtId: Long, from: Long, to: Long): Double =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount),0) FROM debt_entries WHERE debt_id=? AND kind=? AND note<>? AND time>=? AND time<?",
            arrayOf(debtId.toString(), PAY.toString(), CORRECTION, from.toString(), to.toString()),
        ).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }

    fun spentPerDay(from: Long, to: Long, onlyCats: Set<Long>? = null): DoubleArray {
        val days = ((to - from + Period.HOUR) / Period.DAY).toInt().coerceAtLeast(1)
        val out = DoubleArray(days)
        val spread = spreadIds()
        for (t in txns(from, to)) {
            if (t.income || t.id in spread) continue
            if (onlyCats != null && (t.categoryId == null || t.categoryId !in onlyCats)) continue
            val i = ((t.time - from) / Period.DAY).toInt()
            if (i in 0 until days) out[i] += t.amount
        }
        return out
    }

    // ---------- reset ----------

    /** Deletes every transaction and sent-alert record; keeps budgets, accounts, senders, rules, debts and settings. */
    fun clearTransactions() {
        val w = writableDatabase
        w.beginTransaction()
        try {
            w.delete("txns", null, null)
            w.delete("alerts_sent", null, null)
            w.execSQL("DELETE FROM debt_entries WHERE txn_id IS NOT NULL")
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Back to a fresh install: default budgets and account, no senders, transactions or debts. */
    fun resetAll() {
        val w = writableDatabase
        w.beginTransaction()
        try {
            for (t in listOf("categories", "txns", "rules", "senders", "settings", "alerts_sent",
                    "accounts", "debts", "debt_entries", "goals", "goal_entries", "jamiya", "jamiya_paid",
                    "balance_fix")) {
                w.execSQL("DROP TABLE IF EXISTS $t")
            }
            onCreate(w)
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    // ---------- alerts ----------

    /** True the first time a key is seen, so each alert fires once per cycle. */
    fun firstTime(key: String): Boolean =
        writableDatabase.insertWithOnConflict("alerts_sent", null,
            ContentValues().apply { put("k", key) }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
}
