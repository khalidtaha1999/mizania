package com.mizania.app

import android.content.Context
import android.content.res.Resources

/**
 * App language. Follows the phone (Arabic if the phone is in Arabic, English otherwise)
 * until the user picks one in settings; the pick is kept in the "lang" setting.
 */
object Lang {
    const val SYSTEM = ""
    const val AR = "ar"
    const val EN = "en"

    @Volatile var ar: Boolean = phoneArabic()
        private set

    fun phoneArabic(): Boolean = Resources.getSystem().configuration.locales[0].language == "ar"

    fun choice(ctx: Context): String = Db.get(ctx).setting("lang", SYSTEM)

    fun apply(choice: String) {
        ar = when (choice) {
            AR -> true
            EN -> false
            else -> phoneArabic()
        }
    }

    fun set(ctx: Context, choice: String) {
        Db.get(ctx).setSetting("lang", choice)
        apply(choice)
    }
}

/** The text in the current app language. */
fun tr(ar: String, en: String): String = if (Lang.ar) ar else en

/** Currency after amounts. */
fun currency(): String = tr("د.أ", "JOD")

/** "N يوم" / "N days". */
fun daysText(n: Int): String = tr("$n يوم", if (n == 1) "1 day" else "$n days")

/** "N شهر" / "N months". */
fun monthsText(n: Int): String = tr("$n شهر", if (n == 1) "1 month" else "$n months")

/** "N حركة" / "N transactions". */
fun txnsText(n: Int): String = tr("$n حركة", if (n == 1) "1 transaction" else "$n transactions")

/**
 * The SMS reader stores the kind of a movement in Arabic (it is matched on in the code);
 * this shows it in the app language.
 */
fun kindLabel(kind: String): String {
    if (Lang.ar) return if (kind == "كاش") "نقدي" else kind
    return when {
        kind == "تحويل بين حساباتك" -> "Transfer between your accounts"
        kind == "راتب" -> "Salary"
        kind == "كليك" -> "CliQ sent"
        kind == "كليك وارد" -> "CliQ received"
        kind == "شراء" -> "Purchase"
        kind == "سحب نقدي" -> "Cash withdrawal"
        kind == "حوالة صادرة" -> "Outgoing transfer"
        kind.startsWith("حوالة لحساب ") -> "Transfer to account " + kind.removePrefix("حوالة لحساب ")
        kind == "حوالة واردة" -> "Incoming transfer"
        kind == "إيداع" -> "Deposit"
        kind == "رسوم بنكية" -> "Bank fees"
        kind == "خصم" -> "Debit"
        kind == "حركة" -> "Transaction"
        kind == "كاش" -> "Cash"
        else -> kind
    }
}

/** Merchant names the SMS reader makes up itself ("راتب", "صراف آلي"…), in the app language. */
fun merchantLabel(m: String): String {
    if (Lang.ar) return if (m == "كاش") "نقدي" else m
    return when {
        m == "راتب" -> "Salary"
        m == "صراف آلي" -> "ATM"
        m == "البنك" -> "Bank"
        m == "كاش" -> "Cash"
        m.startsWith("من ") && m.contains(" إلى ") ->
            "From " + m.removePrefix("من ").replace(" إلى ", " to ")
        else -> m
    }
}

val Txn.kindText: String get() = kindLabel(kind)

/** The name shown for a transaction: who it went to, or its kind. */
val Txn.title: String get() = merchantLabel(merchant).ifEmpty { kindText }
