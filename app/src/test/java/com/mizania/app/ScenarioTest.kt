package com.mizania.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A month of one person's money, as the app sees it: a salary, rent and bills, groceries and coffee,
 * cash, a big fuel fill-up, a dinner paid for friends and their transfers back, a declined card,
 * a purchase in dollars. Every amount and account number here is made up.
 * After each step the numbers on the home screen are checked against what they must be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScenarioTest {
    private lateinit var app: Application
    private lateinit var db: Db
    private var acc = 0L
    private var balance = 0.0
    private val SENDER = "ARABIISLAMI"
    private val ACCT = "1111*2222"
    private val log = StringBuilder()

    private val day = 86_400_000L
    private fun at(daysAgo: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** The k-th moment of today, in order and never in the future, whenever the test runs. */
    private val startToday = at(0, 0)
    private val nowAtStart = System.currentTimeMillis()
    private fun tod(k: Int): Long = startToday + (nowAtStart - startToday) * k / 20

    private fun stamp(t: Long) = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.US).format(t)
    private fun jod(v: Double) = String.format(Locale.US, "%,.3f", v)

    /** The bank's SMS for a movement, in the one-field-per-line wording, with the new balance. */
    private fun sms(t: Long, head: String, amount: Double, out: Boolean, extra: String = ""): Long {
        balance += if (out) -amount else amount
        val body = "$head\nالمبلغ: JOD ${jod(amount)}\nحساب: $ACCT\n${extra}تاريخ: ${stamp(t)}\nالرصيد: JOD ${jod(balance)}"
        return Processor.handle(app, SENDER, body, t, live = true, accountId = acc)
    }

    private fun buy(t: Long, shop: String, amount: Double) = sms(t, "حركة شراء $shop", amount, true)

    private fun home(): Allowance = Budget.allowance(db)

    private fun note(what: String) {
        val a = home()
        log.append(String.format(Locale.US, "%-44s bal=%8.3f committed=%7.3f spendable=%8.3f | today: allowed=%6.3f spent=%6.3f left=%7.3f | tomorrow=%6.3f days=%d\n",
            what, a.balance ?: Double.NaN, a.committed, a.spendable ?: Double.NaN, a.allowance, a.spentToday, a.leftToday, a.tomorrow, a.daysLeft))
    }

    private var rent = 0L; private var bills = 0L; private var food = 0L; private var fuel = 0L; private var savings = 0L; private var clothes = 0L

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Db::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        db = Db.get(app)
        db.setSetting("lang", "ar"); Lang.apply("ar")
        acc = db.saveAccount(Account(0, "Main", "Test bank"))
        db.addSender(SENDER, acc)
        db.categories().forEach { db.deleteCategory(it.id) }
        rent = db.saveCategory(Category(0, "إيجار", 275.0, Db.FIXED, "🏠"))
        bills = db.saveCategory(Category(0, "فواتير", 40.0, Db.FIXED, "🧾"))
        savings = db.saveCategory(Category(0, "ادخار", 100.0, Db.RESERVE, "💰"))
        food = db.saveCategory(Category(0, "طعام", 150.0, Db.DAILY, "🍔"))
        fuel = db.saveCategory(Category(0, "بنزين", 60.0, Db.DAILY, "⛽"))
        clothes = db.saveCategory(Category(0, "ملابس", 30.0, Db.DAILY, "👕"))
    }

    @After fun printStory() { println("STORY:\n$log") }

    private fun near(msg: String, expected: Double, actual: Double, eps: Double = 0.005) =
        assertEquals(msg, expected, actual, eps)

    /** The home screen's numbers must always follow from the balance, the lines and today's spending. */
    private fun checkHome(spentTodayExpected: Double) {
        val a = home()
        val bal = db.bankBalances().filter { it.accountId == acc }.mapNotNull { it.balance }.sum()
        near("balance shown = last SMS balance", balance, a.balance!!)
        near("balance from bankBalances", balance, bal)
        near("spent today", spentTodayExpected, a.spentToday)
        near("left today = allowance - spent", a.allowance - a.spentToday, a.leftToday)
        assertTrue("allowance never negative", a.allowance >= 0)
        assertTrue("days left >= 1", a.daysLeft >= 1)
    }

    @Test fun aMonthOfMoney() {
        // Before the salary: the bank says what is there.
        balance = 40.0
        Processor.handle(app, SENDER, "رصيد حسابكم رقم $ACCT هو ${jod(balance)} JOD", at(21, 9), true, acc)
        note("start, balance notice 40")

        // Payday, 20 days ago.
        sms(at(20, 10), "تحويل راتب", 1000.0, false)
        val ci = Budget.cycle(db)
        assertTrue("cycle starts on payday", ci.bySalary)
        near("salary", 1000.0, ci.salary)
        note("salary 1000")
        near("committed = all fixed + reserve lines", 275.0 + 40 + 100, home().committed)

        // Rent goes out by CliQ and is sorted onto its line.
        val rentId = sms(at(19, 11), "حركة كليك", 275.0, true, "إلى: LANDLORD NAME\n")
        db.updateTxn(rentId, rent, ""); Processor.afterCategorized(app, rentId, rent, false)
        near("rent paid: no longer committed", 40.0 + 100, home().committed)
        note("rent paid + sorted")

        // Daily life over the past days, sorted.
        for (d in 18 downTo 1) {
            val g = buy(at(d, 18), "GROCERY STORE", 6.0)
            db.updateTxn(g, food, "")
            if (d % 4 == 0) { val f = buy(at(d, 8), "FUEL STATION", 10.0); db.updateTxn(f, fuel, "") }
        }
        note("18 days of groceries and fuel")
        checkHome(0.0)

        // Electricity bill, a bit over its line.
        val bill = sms(at(5, 12), "دفع eFAWATEERcom", 45.0, true)
        db.updateTxn(bill, bills, "")
        near("bill over its line: nothing left to hold for it", 100.0, home().committed)
        note("bill 45 on a 40 line")

        // Today: a coffee not sorted yet, and lunch on the food line.
        buy(tod(1), "AL SALAMAT COFFEE", 1.0)
        note("coffee 1 (unsorted)")
        checkHome(1.0)
        val lunch = buy(tod(2), "FALAFEL HOUSE", 5.0)
        db.updateTxn(lunch, food, "")
        checkHome(6.0)
        note("lunch 5 on food")

        // Spending on a fixed line that is already over: it is not "today's" spending, but the money is gone.
        val before = home()
        val more = buy(tod(3), "WATER COMPANY", 3.0)
        db.updateTxn(more, bills, "")
        val after = home()
        note("3 more on the over-budget bills line")
        near("not in today's spending", before.spentToday, after.spentToday)
        near("spendable drops by the 3", before.spendable!! - 3.0, after.spendable!!)

        // A declined card: nothing happens.
        val bBefore = balance
        val declined = Processor.handle(app, SENDER,
            "عميلنا الكريم لا يمكن اتمام الحركه لعدم وجود رصيد كافي في حسابكم رقم $ACCT رصيد حسابكم 4.798 JOD", tod(4), true, acc)
        assertTrue("declined SMS not stored", declined <= 0)
        near("balance unchanged after a declined card", bBefore, home().balance!!)
        checkHome(6.0)

        // Cash: counts today, and tomorrow knows it already left.
        val cash = db.insertTxn(tod(5), 2.0, false, "كشك", "كاش", food, "", null, "", "", null, true, null)
        assertTrue(cash > 0)
        checkHome(8.0)
        note("cash 2")

        // A big fill-up spread over the month: today stays calm.
        val tank = buy(tod(6), "FUEL STATION", 40.0)
        db.updateTxn(tank, fuel, "")
        checkHome(48.0)
        db.setSpread(tank, true)
        checkHome(8.0)
        note("tank 40, spread")

        // A purchase in dollars: the bank only states the dinar balance.
        val usdBefore = balance
        balance -= 7.09
        val usd = Processor.handle(app, SENDER,
            "حركة شراء NETFLIX.COM\nالمبلغ: USD 10.000\nحساب: $ACCT\nتاريخ: ${stamp(tod(7))}\nالرصيد: JOD ${jod(balance)}", tod(7), true, acc)
        val usdTxn = db.txn(usd)!!
        near("dollars booked in dinars from the balance", usdBefore - balance, usdTxn.amount)
        assertTrue("original amount kept in the note", usdTxn.note.contains("USD"))
        db.updateTxn(usd, food, usdTxn.note)
        note("netflix 10 USD")

        // Dinner for four, paid by me; I keep 15, three friends owe 15 each.
        val dinner = buy(tod(8), "RESTAURANT", 60.0)
        val spentBefore = home().spentToday
        db.payForOthers(dinner, food, "عشا", listOf("AHMAD" to 15.0, "SAMI ALI" to 15.0, "Omar" to 15.0), "")
        val d = db.txn(dinner)!!
        near("my share stays on the payment", 15.0, d.amount)
        assertEquals(food, d.categoryId)
        near("only my share counts today", spentBefore - 60.0 + 15.0, home().spentToday)
        val o = db.outing(dinner)
        assertNotNull(o)
        near("friends owe 45", 45.0, o!!.owed)
        assertEquals(3, db.debts().count { it.owedToMe && !it.closed })
        note("dinner 60 for 4")
        near("food line: only my share", 15.0, db.spentByCategory(Budget.cycle(db).range.from, Long.MAX_VALUE)[food]!! -
            (18 * 6.0 + 5.0 + 2.0 + usdTxn.amount))

        // Ahmad pays back by CliQ: closes his debt, not income.
        val incomeBefore = db.otherIncome(Budget.cycle(db).range.from, Long.MAX_VALUE)
        val back = sms(tod(9), "حركة كليك واردة", 15.0, false, "من: AHMAD\n")
        val ahmad = db.debts().first { it.person == "AHMAD" }
        assertTrue("Ahmad's debt closed by his transfer", ahmad.closed)
        near("repayment is not income", incomeBefore, db.otherIncome(Budget.cycle(db).range.from, Long.MAX_VALUE))
        note("Ahmad paid back 15")

        // Sami's bank shows his full name.
        sms(tod(10), "حركة كليك واردة", 15.0, false, "من: SAMI ALI HASSAN\n")
        assertTrue("Sami matched by his longer name", db.debts().first { it.person == "SAMI ALI" }.closed)

        // Undo the dinner split: friends' debts go, the whole 60 is mine again.
        db.clearOuting(dinner)
        near("undo restores the full amount", 60.0, db.txn(dinner)!!.amount)
        assertNull(db.outing(dinner))
        checkHome(home().spentToday)

        // Split a shop bill across two lines.
        val shop = buy(tod(11), "CITY MALL", 30.0)
        db.splitTxn(shop, food, listOf(clothes to 10.0))
        near("split: main keeps the rest", 20.0, db.txn(shop)!!.amount)
        near("split total", 10.0, db.splitTotal(shop))
        note("mall 30 split 20/10")

        // Ignore a transfer to my own other account: not spending, but the money left.
        val own = buy(tod(12), "MY OTHER ACCOUNT", 5.0)
        val sBefore = home().spentToday
        db.ignoreTxn(own)
        near("ignored: not today's spending", sBefore - 5.0, home().spentToday)
        near("ignored: balance still follows the bank", balance, home().balance!!)

        // Reports: the month's spending by line adds up to the stored transactions.
        val r = Budget.cycle(db).range
        val byLine = db.spentByCategory(r.from, r.to)
        val total = byLine.values.sum()
        assertTrue("month spending is positive", total > 0)
        assertFalse("no NaN in reports", total.isNaN())

        // Next payday arrives today: a fresh month, lines reset.
        sms(tod(13), "تحويل راتب", 1000.0, false)
        val next = Budget.cycle(db)
        assertTrue("new month starts today", next.range.from >= at(0, 0))
        // The new month starts at the start of payday: the 3 paid on bills earlier today already count in it.
        near("lines held again in full", 275.0 + 40 + 100 - 3.0, home().committed)
        note("next salary")
    }

    @Test fun noticeWithoutQaidStaysVisible() {
        // A bank that writes "قيد" on real movements: a message without it is kept aside, not lost.
        val S = "ARABBANK"
        db.addSender(S, acc)
        balance = 100.0
        for (i in 1..3) {
            balance -= 1.0
            Processor.handle(app, S, "تم قيد حركة شراء بمبلغ 1.000 JOD من حسابك $ACCT لدى: SHOP$i بتاريخ ${SimpleDateFormat("dd/MM/yyyy", Locale.US).format(at(3 - i, 10))} الرصيد ${jod(balance)} JOD", at(3 - i, 10), true, acc)
        }
        val id = Processor.handle(app, S, "حركة شراء بمبلغ 4.000 JOD من حسابك $ACCT لدى: PENDING SHOP", at(0, 11), true, acc)
        assertTrue("notice not counted as a movement", id <= 0)
        val aside = db.ignoredTxns(50)
        assertTrue("notice listed as left out", aside.any { it.kind.startsWith(Db.NOTICE_PREFIX) && it.amount == 4.0 })
        near("balance unchanged by a notice", 97.0, home().balance!!)
        val n = aside.first { it.kind.startsWith(Db.NOTICE_PREFIX) }
        db.unignoreTxn(n.id)
        assertEquals("شراء", db.txn(n.id)!!.kind)
    }

    @Test fun salaryLate() {
        // Last salary 33 days ago, the next one has not come yet.
        balance = 300.0
        sms(at(33, 10), "تحويل راتب", 300.0, false)
        buy(at(1, 12), "GROCERY STORE", 10.0)
        val a = home()
        note("salary 33 days ago, none since")
        assertTrue("cycle is marked late", a.cycle.late)
        println("LATE: daysLeft=${a.daysLeft} allowance=${a.allowance} spendable=${a.spendable}")
        assertTrue("a late salary must not let the whole balance be spent in one day", a.allowance < (a.spendable ?: 0.0) * 0.5)
    }

    @Test fun noBudgetLinesStillWorks() {
        db.categories().forEach { db.deleteCategory(it.id) }
        balance = 100.0
        sms(at(5, 10), "تحويل راتب", 500.0, false)
        buy(tod(1), "SHOP", 4.0)
        val a = home()
        note("no lines at all")
        near("spent today", 4.0, a.spentToday)
        assertFalse(a.allowance.isNaN())
    }

    @Test fun cashTypoCanBeFixedOrDeleted() {
        balance = 100.0
        sms(at(3, 10), "تحويل راتب", 500.0, false)
        val cash = db.insertTxn(tod(1), 20.0, false, "كشك", "كاش", food, "", null, "", "", null, true, null)
        near("typo counts today", 20.0, home().spentToday)
        db.setManualAmount(cash, 2.0)
        near("fixed amount", 2.0, home().spentToday)
        db.deleteManualTxn(cash)
        near("deleted", 0.0, home().spentToday)
        assertNull(db.txn(cash))
        // A bank transaction can never be deleted this way.
        val bank = buy(tod(2), "SHOP", 3.0)
        db.deleteManualTxn(bank)
        assertNotNull(db.txn(bank))
    }
}
