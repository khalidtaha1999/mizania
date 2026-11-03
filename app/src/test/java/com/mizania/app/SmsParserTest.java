package com.mizania.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Bank SMS the app must keep reading the same way. Every account number, name and amount here is made up.
 * When a new wording shows up, add it here first, then change the parser until everything passes.
 */
public class SmsParserTest {

    private static final double EPS = 0.0005;

    private static SmsParser.Result parse(String sms) {
        SmsParser.Result r = SmsParser.parse(sms);
        assertNotNull("should be read as a transaction:\n" + sms, r);
        return r;
    }

    private static void out(SmsParser.Result r, double amount, String kind) {
        assertFalse("should be money out", r.income);
        assertFalse(r.internal);
        assertEquals(amount, r.amount, EPS);
        assertEquals(kind, r.kind);
    }

    // ---------- Islamic International Arab Bank: one field per line ----------

    @Test public void islamicPurchase() {
        SmsParser.Result r = parse("حركة شراء TOP AND TOP\nAMMAN JORDAN JO\nالمبلغ: JOD 4.540\nحساب: 1111*2222\nتاريخ: 16:21:01 21/10/2026\nالرصيد: JOD 3.260");
        out(r, 4.540, "شراء");
        assertTrue(r.merchant.startsWith("TOP AND TOP"));
        assertEquals(3.260, r.balance, EPS);
        assertEquals("1111*2222", r.acctNo);
    }

    @Test public void islamicSalary() {
        SmsParser.Result r = parse("تحويل راتب\nالمبلغ: JOD 1,270.833\nحساب: 1111*2222\nتاريخ: 15:44:47 22/10/2026\nالرصيد: JOD 1,273.093");
        assertTrue(r.income);
        assertEquals("راتب", r.kind);
        assertEquals(1270.833, r.amount, EPS);
        assertEquals(1273.093, r.balance, EPS);
    }

    @Test public void islamicCliqOut() {
        SmsParser.Result r = parse("حركة كليك\nالمبلغ: JOD 50.000\nحساب: 1111*2222\nإلى: SOME PERSON NAME\nتاريخ: 15:52:47 22/10/2026\nالرصيد: JOD 1,223.093");
        out(r, 50, "كليك");
        assertEquals("SOME PERSON NAME", r.merchant);
    }

    @Test public void islamicBillPayment() {
        SmsParser.Result r = parse("دفع eFAWATEERcom\nالمبلغ: JOD 18.000\nحساب: 1111*2222\nتاريخ: 16:07:28 22/10/2026\nالرصيد: JOD 1,205.093");
        out(r, 18, "دفع فاتورة");
        assertEquals("eFAWATEERcom", r.merchant);
    }

    @Test public void dateIsRead() {
        SmsParser.Result r = parse("حركة شراء X\nالمبلغ: JOD 1.000\nحساب: 1111*2222\nتاريخ: 16:21:01 21/10/2026\nالرصيد: JOD 3.000");
        assertNotNull(r.time);
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(r.time);
        assertEquals(21, c.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(9, c.get(java.util.Calendar.MONTH));
    }

    // ---------- Arab Bank style: one sentence ----------

    @Test public void sentencePurchase() {
        SmsParser.Result r = parse("تم شراء بمبلغ 5.250 JOD من ZAIN من حساب 1111*600 الرصيد 20.000 JOD");
        out(r, 5.25, "شراء");
        assertEquals("ZAIN", r.merchant);
        assertEquals(20.0, r.balance, EPS);
    }

    @Test public void postedPurchaseWithMerchantLabel() {
        String sms = "تم قيد حركة شراء بمبلغ 3.500 JOD من حسابك 1111*600 لدى: CAFE ONE AMMAN JO بتاريخ 01/11/2026 الرصيد 96.500 JOD";
        SmsParser.Result r = parse(sms);
        out(r, 3.5, "شراء");
        assertEquals("CAFE ONE", r.merchant);
        assertTrue(SmsParser.isPosted(sms));
    }

    @Test public void salaryDeposit() {
        String sms = "تم ايداع راتب بمبلغ 500.000 JOD لحساب 1111*600 الرصيد 520.000 JOD";
        SmsParser.Result r = parse(sms);
        assertTrue(r.income);
        assertEquals("راتب", r.kind);
        assertEquals(500, r.amount, EPS);
        assertTrue(SmsParser.isPosted(sms));
    }

    @Test public void cliqOutStated() {
        SmsParser.Result r = parse("حوالة كليك صادرة بمبلغ 10.000 JOD من حساب 1111*600 الى AHMAD الرصيد 9.000 JOD");
        out(r, 10, "كليك");
        assertEquals("AHMAD", r.merchant);
    }

    @Test public void transferBetweenOwnAccounts() {
        SmsParser.Result r = parse("تم تحويل مبلغ 100.000 JOD من حساب 1111*300 الى حساب 1111*301 الرصيد 50.000 JOD");
        assertTrue(r.internal);
        assertEquals(100, r.amount, EPS);
    }

    @Test public void arabicDigits() {
        SmsParser.Result r = parse("تم شراء بمبلغ ٥٫٢٥٠ JOD من حساب 1111*600 الرصيد ٢٠٫٠٠٠ JOD");
        assertEquals(5.25, r.amount, EPS);
        assertEquals(20.0, r.balance, EPS);
    }

    @Test public void balanceIsNeverTheAmount() {
        SmsParser.Result r = parse("حركة شراء X\nالرصيد: JOD 99.000\nالمبلغ: JOD 2.000\nحساب: 1111*2222");
        assertEquals(2.0, r.amount, EPS);
        assertEquals(99.0, r.balance, EPS);
    }

    // ---------- other currencies ----------

    @Test public void dollarsConvertedToDinars() {
        SmsParser.Result r = parse("حركة شراء NETFLIX.COM\nالمبلغ: USD 10.000\nحساب: 1111*2222\nتاريخ: 16:21:01 21/10/2026\nالرصيد: JOD 92.910");
        out(r, 7.09, "شراء");
        assertEquals("USD", r.currency);
        assertEquals(10.0, r.original, EPS);
        assertTrue(r.approx);
        assertEquals(92.91, r.balance, EPS);
    }

    @Test public void currencyAfterAmountIsNotDinars() {
        SmsParser.Result r = parse("حركة شراء AMAZON\nالمبلغ: 25.00 USD\nحساب: 1111*2222\nالرصيد: JOD 70.000");
        assertEquals("USD", r.currency);
        assertEquals(17.725, r.amount, EPS);
    }

    @Test public void bankConversionWins() {
        SmsParser.Result r = parse("حركة شراء SHOP\nالمبلغ: USD 20.00 (JOD 14.250)\nحساب: 1111*2222\nالرصيد: JOD 50.000");
        assertEquals(14.25, r.amount, EPS);
        assertFalse(r.approx);
    }

    @Test public void euroInSentence() {
        SmsParser.Result r = parse("تم شراء بمبلغ 15.000 EUR من حساب 1111*600 لدى: HOTEL X الرصيد 80.000 JOD");
        assertEquals("EUR", r.currency);
        assertEquals(11.55, r.amount, EPS);
    }

    @Test public void dinarsStayDinars() {
        SmsParser.Result r = parse("حركة شراء SHOP\nالمبلغ: JOD 7.000\nحساب: 1111*2222\nالرصيد: JOD 1.000");
        assertEquals("JOD", r.currency);
        assertFalse(r.approx);
    }

    // ---------- not transactions ----------

    @Test public void onlineShopWithoutBalanceIsStillAPurchase() {
        SmsParser.Result r = parse("حركة شراء SHOP.COM\nالمبلغ: JOD 7.000\nحساب: 1111*2222");
        out(r, 7, "شراء");
    }

    @Test public void balanceNotice() {
        String sms = "رصيد حسابكم رقم 1111*600 هو 13.000 JOD";
        assertNull(SmsParser.parse(sms));
        String[] n = SmsParser.balanceNotice(sms);
        assertNotNull(n);
        assertEquals(13.0, Double.parseDouble(n[1]), EPS);
    }

    @Test public void declinedIsNothing() {
        String a = "عميلنا الكريم لا يمكن اتمام الحركه لعدم وجود رصيد كافي في حسابكم رقم 600*1111 رصيد حسابكم 4.798 JOD";
        String b = "عميلنا الكريم لا يمكن إتمام الحركة لعدم كفاية الرصيد في حسابكم رقم 600*1111 بمبلغ 10.000 JOD";
        for (String s : new String[]{a, b}) {
            assertTrue(SmsParser.isDeclined(s));
            assertNull(SmsParser.parse(s));
            assertNull(SmsParser.balanceNotice(s));
        }
    }

    @Test public void otpIsNothing() {
        assertNull(SmsParser.parse("رمز التحقق الخاص بك هو 123456"));
        assertNull(SmsParser.parse("Your OTP is 123456 for a purchase of JOD 5.000"));
    }

    @Test public void adsAreNothing() {
        assertNull(SmsParser.parse("احصل على قرض بفائدة 5% اتصل الآن"));
        assertNull(SmsParser.parse("احصل على عرض خاص www.bank.com"));
    }

    @Test public void postedWording() {
        assertTrue(SmsParser.isPosted("تم قيد حركة شراء"));
        assertTrue(SmsParser.isPosted("تم إيداع مبلغ"));
        assertFalse(SmsParser.isPosted("حركة شراء بانتظار التنفيذ"));
    }

    @Test public void sameAccountTwoSpellings() {
        assertEquals(SmsParser.canonical("2222*1111"), SmsParser.canonical("1111*2222"));
    }
}
