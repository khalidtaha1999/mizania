package com.mizania.app;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads bank SMS (Arab Bank wording first, generic JOD messages as a fallback)
 * and pulls out amount, direction, the other party, accounts, date and balance.
 * Pure Java with no Android imports so it can be tested on any JVM.
 *
 * Direction comes from the accounts named in the message:
 *   "من حساب X"  money left your account X,
 *   "لحساب X"    money arrived in your account X,
 *   both, and X and Y share the same customer number, a transfer between your own accounts.
 */
public final class SmsParser {

    public static final class Result {
        public double amount;
        public boolean income;
        public String kind = "";
        public String merchant = "";
        /** Balance after the movement; null when the SMS has none. */
        public Double balance;
        /** Date written in the SMS (may be the bank's business date); null when absent. */
        public Long time;
        /** Your account whose money changed (the sending one for a transfer between your accounts). */
        public String acctNo = "";
        /** Receiving account for a transfer between your own accounts; "" otherwise. */
        public String toAcct = "";
        /** The account the reported balance belongs to. */
        public String balAcct = "";
        /** Money moved between two of your own accounts: not spending, not income. */
        public boolean internal;

        @Override
        public String toString() {
            return String.format(Locale.US, "%s | %s | %.3f | %s | bal=%s@%s | acct=%s%s",
                    internal ? "MOVE" : income ? "IN" : "OUT", kind, amount, merchant, balance, balAcct, acctNo,
                    toAcct.isEmpty() ? "" : "->" + toAcct);
        }
    }

    private static final String NUM = "([0-9][0-9,]*(?:\\.[0-9]+)?)";
    private static final String MASK = "([0-9Xx]*\\*+[0-9Xx]*[0-9])";
    private static final Pattern AMOUNT_LABEL = Pattern.compile(
            "(?:المبلغ|بمبلغ|لمبلغ|مبلغ|بقيمة|قيمة)\\s*[:：]?\\s*(?:JOD|JD)?\\s*" + NUM);
    private static final Pattern JOD_BEFORE = Pattern.compile("(?:JOD|JD)\\s*" + NUM);
    private static final Pattern JOD_AFTER = Pattern.compile(NUM + "\\s*(?:JOD|JD|دينار)");
    /** "الرصيد 12.500" / "الرصيد المتوفر 63.900" / "الرصيد JOD85.400" / "الرصيد 7.250JOD". */
    private static final Pattern BALANCE = Pattern.compile(
            "الرصيد(?:\\s+(?:المتوفر|المتاح|الحالي|الجديد))?\\s*[:：]?\\s*(?:JOD|JD)?\\s*(-?[0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Pattern DATETIME = Pattern.compile(
            "(\\d{1,2})/(\\d{1,2})/(\\d{4})(?:\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?");
    /** Money left this account. */
    private static final Pattern FROM_ACCT = Pattern.compile("من\\s+حساب(?:كم|ك)?\\s*(?:رقم)?\\s*[:：]?\\s*" + MASK);
    /** Money arrived in this account. */
    private static final Pattern TO_ACCT = Pattern.compile("(?:لحساب|(?:إلى|الى)\\s+حساب)(?:كم|ك)?\\s*(?:رقم)?\\s*[:：]?\\s*" + MASK);
    /** Any account mention, for messages that say neither from nor to. */
    private static final Pattern ACCT = Pattern.compile("حساب(?:كم|ك)?\\s*(?:رقم)?\\s*[:：]?\\s*" + MASK);
    private static final Pattern TO_NAME = Pattern.compile("(?:إلى|الى|للمستفيد)\\s*[:：]?\\s*(?!حساب)([^\\n]+)");
    private static final Pattern FROM_NAME = Pattern.compile("(?:من|المرسل)\\s*[:：]?\\s*(?!حساب)([^\\n]+)");
    private static final Pattern MERCHANT_LABEL = Pattern.compile("(?:التاجر|لدى)\\s*[:：]\\s*([^\\n]+)");
    /** Words that end a merchant or person name. */
    private static final Pattern NAME_END = Pattern.compile(
            "\\s*(?:بمبلغ|بقيمة|المبلغ|بتاريخ|تاريخ|الرصيد|حساب|لحساب|[0-9][0-9.,]*\\s*(?:JOD|JD))");

    private static final Pattern MOVEMENT = Pattern.compile(
            "قيد|شراء|سحب|كليك|cliq|CliQ|CLIQ|حوالة|تحويل|إيداع|ايداع|راتب|خصم|دفع|رسوم|عمولة|مدين|دائن|وارد|استلام|تسديد");
    /** "دفع X" at the start of a line (not "دفعة"): X is who was paid. */
    private static final Pattern BILL_PAY = Pattern.compile("(?m)^\\s*دفع(?!ة)\\s+([^\\n]+)");
    private static final Pattern AD = Pattern.compile(
            "احصل|استمتع|اربح|سارع|عرض|عروض|قرض|تمويل|لفترة محدودة|سيتم|مستحق|تذكير|http|www\\.|\\.com|اتصل");
    private static final Pattern BALANCE_NOTICE = Pattern.compile(
            "رصيد(?:ك| حسابك| حسابكم)?[^0-9]{0,40}?(?:هو|:|：)?\\s*(?:JOD|JD)?\\s*(-?[0-9][0-9,]*(?:\\.[0-9]+)?)");

    private SmsParser() {}

    /**
     * One spelling per account: the bank writes the same account as "300*9876" and "9876*300".
     * The parts are put in a fixed order so both become "9876*300".
     */
    public static String canonical(String mask) {
        if (mask == null || mask.isEmpty()) return "";
        String m = mask.toUpperCase(Locale.ROOT).replaceAll("\\*+", "*");
        String[] p = m.split("\\*", -1);
        if (p.length != 2 || p[0].isEmpty() || p[1].isEmpty()) return m;
        return p[0].compareTo(p[1]) <= 0 ? p[0] + "*" + p[1] : p[1] + "*" + p[0];
    }

    /** Two masks of the same customer, e.g. "9876*300" and "9876*301". */
    static boolean sameCustomer(String a, String b) {
        String[] x = a.split("\\*"), y = b.split("\\*");
        if (x.length != 2 || y.length != 2) return false;
        for (String i : x) for (String j : y) if (i.length() >= 4 && i.equals(j)) return true;
        return false;
    }

    /** Masked account number in the SMS (the one money moved from or to), or "". */
    public static String acctNo(String raw) {
        if (raw == null) return "";
        String t = normalize(raw);
        String from = first(FROM_ACCT, t), to = first(TO_ACCT, t);
        if (!from.isEmpty()) return canonical(from);
        if (!to.isEmpty()) return canonical(to);
        return canonical(first(ACCT, t));
    }

    /**
     * The direction the SMS states in words: TRUE for "واردة" (money in), FALSE for "صادرة" (money out),
     * null when it says neither (or both). A stated direction wins over the balance check.
     */
    public static Boolean statedDirection(String raw) {
        if (raw == null) return null;
        String t = normalize(raw);
        String lower = t.toLowerCase(Locale.ROOT);
        boolean in = t.contains("وارد") || lower.contains("incoming");
        boolean out = t.contains("صادر") || lower.contains("outgoing");
        if (in == out) return null;
        return in;
    }

    /**
     * A transaction the bank refused ("لا يمكن إتمام الحركة لعدم وجود رصيد كافي…"). Nothing moved, and the
     * number in it is not a balance to trust, so it is neither a transaction nor a balance notice.
     */
    public static boolean isDeclined(String raw) {
        if (raw == null) return false;
        String t = normalize(raw).replace('إ', 'ا').replace('ة', 'ه');
        String lower = t.toLowerCase(Locale.ROOT);
        return t.contains("لا يمكن اتمام") || t.contains("لا يمكن تنفيذ") || t.contains("تعذر اتمام") || t.contains("تعذر تنفيذ")
                || t.contains("رصيد كاف") || t.contains("كفايه الرصيد") || t.contains("الرصيد غير كاف")
                || t.contains("مرفوض") || t.contains("تم رفض")
                || lower.contains("insufficient") || lower.contains("declined");
    }

    /**
     * The bank says the money actually moved: "تم قيد حركة شراء…", "قيدت حوالة…", "قيد راتب…", or a deposit
     * ("إيداع فوري…"). Banks that word their real movements this way send notices without it.
     */
    public static boolean isPosted(String raw) {
        if (raw == null) return false;
        String t = normalize(raw).replace('إ', 'ا');
        return t.contains("قيد") || t.contains("ايداع");
    }

    /** A balance-only notice ("رصيد حسابك ... هو X"): returns {acctNo, balance} or null. */
    public static String[] balanceNotice(String raw) {
        if (raw == null || isDeclined(raw)) return null;
        String t = normalize(raw);
        if (!t.contains("رصيد") || MOVEMENT.matcher(t).find()) return null;
        if (AD.matcher(t.toLowerCase(Locale.ROOT)).find()) return null;
        String acct = canonical(first(ACCT, t));
        Matcher m = BALANCE_NOTICE.matcher(ACCT.matcher(t).replaceAll(" "));
        if (!m.find()) return null;
        Double v = num(m.group(1));
        return v == null ? null : new String[]{acct, String.valueOf(v)};
    }

    /** Returns null when the text is not a money movement (OTP, offer, reminder, no amount). */
    public static Result parse(String raw) {
        if (raw == null || isDeclined(raw)) return null;
        String t = normalize(raw);
        String lower = t.toLowerCase(Locale.ROOT);

        boolean hasBalance = t.contains("الرصيد");
        boolean otp = t.contains("رمز") || t.contains("كلمة السر") || t.contains("كلمة المرور")
                || lower.matches("(?s).*\\botp\\b.*")
                || lower.matches("(?s).*\\b(verification|activation|security)\\s+code\\b.*")
                || lower.matches("(?s).*\\bcode\\s*(is|:)\\s*\\d+.*");
        if ((otp && !hasBalance) || t.contains("مرفوضة") || t.contains("رفض")) return null;
        boolean ad = AD.matcher(lower).find() || t.contains("%");
        if (ad && !hasBalance) return null;
        if (!MOVEMENT.matcher(t).find()) return null;

        String from = canonical(first(FROM_ACCT, t));
        String to = canonical(first(TO_ACCT, t));
        String any = canonical(first(ACCT, t));
        if (from.isEmpty() && to.isEmpty() && any.isEmpty() && !hasBalance) return null;

        // The balance sits after the amount; take it out first so it is never read as the amount.
        Matcher bm = BALANCE.matcher(t);
        Double balance = bm.find() ? num(bm.group(1)) : null;
        String noBalance = BALANCE.matcher(t).replaceAll(" ");
        Double amount = findAmount(noBalance);
        if (amount == null || amount <= 0) return null;

        Result r = new Result();
        r.amount = amount;
        r.balance = balance;
        r.time = findTime(t);

        // One-line copy for names (they wrap onto the next line), without the account phrases.
        String f = TO_ACCT.matcher(FROM_ACCT.matcher(t).replaceAll(" ")).replaceAll(" ").replaceAll("\\s+", " ");
        boolean cliq = t.contains("كليك") || lower.contains("cliq");

        if (!from.isEmpty() && !to.isEmpty() && sameCustomer(from, to)) {
            r.internal = true;
            r.income = false;
            r.kind = "تحويل بين حساباتك";
            r.acctNo = from;
            r.toAcct = to;
            r.balAcct = to;
            r.merchant = "من " + from + " إلى " + to;
        } else if (t.contains("راتب")) {
            r.income = true;
            r.kind = "راتب";
            r.merchant = "راتب";
            r.acctNo = pick(to, from, any);
        } else if (!from.isEmpty()) {
            r.income = false;
            r.acctNo = from;
            if (cliq) {
                r.kind = "كليك";
                r.merchant = cutName(group(TO_NAME, f));
            } else if (t.contains("شراء")) {
                r.kind = "شراء";
                String m = group(MERCHANT_LABEL, f);
                if (m.isEmpty()) m = group(FROM_NAME, f);
                r.merchant = cleanMerchant(cutName(m));
            } else if (t.contains("سحب")) {
                r.kind = "سحب نقدي";
                r.merchant = "صراف آلي";
            } else {
                r.kind = to.isEmpty() ? "حوالة صادرة" : "حوالة لحساب " + to;
                r.merchant = cutName(group(TO_NAME, f));
            }
        } else if (!to.isEmpty()) {
            r.income = true;
            r.acctNo = to;
            r.kind = cliq ? "كليك وارد" : t.contains("إيداع") || t.contains("ايداع") ? "إيداع" : "حوالة واردة";
            r.merchant = cutName(group(FROM_NAME, f));
        } else {
            // No from/to wording: fall back to the words in the message.
            r.acctNo = any;
            if (cliq) {
                boolean incoming = t.contains("وارد") || t.contains("استلام")
                        || (!TO_NAME.matcher(f).find() && FROM_NAME.matcher(f).find());
                r.income = incoming;
                r.kind = incoming ? "كليك وارد" : "كليك";
                r.merchant = cutName(incoming ? group(FROM_NAME, f) : group(TO_NAME, f));
            } else if (t.contains("شراء")) {
                r.kind = "شراء";
                String m = group(MERCHANT_LABEL, f);
                if (m.isEmpty()) m = group(PURCHASE, f);
                r.merchant = cleanMerchant(cutName(m));
            } else if (t.contains("سحب")) {
                r.kind = "سحب نقدي";
                r.merchant = "صراف آلي";
            } else if (t.contains("رسوم") || t.contains("عمولة")) {
                r.kind = "رسوم بنكية";
                r.merchant = "البنك";
            } else if (t.contains("إيداع") || t.contains("ايداع") || t.contains("دائن") || t.contains("وارد")) {
                r.income = true;
                r.kind = "إيداع";
                r.merchant = cutName(group(FROM_NAME, f));
            } else if (t.contains("خصم") || t.contains("مدين")) {
                r.kind = "خصم";
                r.merchant = cutName(group(MERCHANT_LABEL, f));
            } else if (BILL_PAY.matcher(t).find()) {
                // "دفع eFAWATEERcom": a bill paid from the account; the payee follows the word.
                r.kind = "دفع فاتورة";
                Matcher pm = BILL_PAY.matcher(t);
                String payee = pm.find() ? pm.group(1).trim() : "";
                r.merchant = payee.isEmpty() ? "دفع فاتورة" : cleanMerchant(cutName(payee));
            } else if (t.contains("تحويل") || t.contains("حوالة")) {
                r.income = t.contains("إليك") || t.contains("لحسابك");
                r.kind = r.income ? "حوالة واردة" : "حوالة صادرة";
                r.merchant = cutName(r.income ? group(FROM_NAME, f) : group(TO_NAME, f));
            } else {
                r.kind = "حركة";
            }
        }
        // "حوالة كليك صادرة / واردة": the stated direction decides, whatever the account wording suggests.
        Boolean stated = statedDirection(t);
        if (stated != null && !r.internal && !"راتب".equals(r.kind)
                && (cliq || t.contains("حوالة") || t.contains("تحويل"))) {
            boolean in = stated;
            if (in != r.income) {
                r.merchant = cutName(in ? group(FROM_NAME, f) : group(TO_NAME, f));
                if (in && !to.isEmpty()) r.acctNo = to;
                if (!in && !from.isEmpty()) r.acctNo = from;
            }
            r.income = in;
            if (cliq) r.kind = in ? "كليك وارد" : "كليك";
            else r.kind = in ? "حوالة واردة" : to.isEmpty() ? "حوالة صادرة" : "حوالة لحساب " + to;
        }
        if (r.balAcct.isEmpty()) r.balAcct = r.acctNo;
        if (r.merchant == null) r.merchant = "";
        r.merchant = r.merchant.trim();
        return r;
    }

    private static final Pattern PURCHASE = Pattern.compile("(?:حركة\\s+)?شراء\\s*(?:من|لدى)?\\s*[:：]?\\s*([^\\n]+)");

    private static String pick(String... options) {
        for (String o : options) if (o != null && !o.isEmpty()) return o;
        return "";
    }

    static String normalize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '٠' && c <= '٩') c = (char) ('0' + (c - '٠'));
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + (c - '۰'));
            else if (c == '٫') c = '.';
            else if (c == '٬') c = ',';
            else if (c == '‎' || c == '‏' || (c >= '‪' && c <= '‮')
                    || (c >= '⁦' && c <= '⁩')) {
                continue;
            } else if (c == ' ') c = ' ';
            else if (c == '\r') c = '\n';
            b.append(c);
        }
        return b.toString();
    }

    private static Double findAmount(String t) {
        Matcher m = AMOUNT_LABEL.matcher(t);
        if (m.find()) return num(m.group(1));
        m = JOD_AFTER.matcher(t);
        if (m.find()) return num(m.group(1));
        m = JOD_BEFORE.matcher(t);
        if (m.find()) return num(m.group(1));
        return null;
    }

    private static Long findTime(String t) {
        Matcher m = DATETIME.matcher(t);
        if (!m.find()) return null;
        try {
            int d = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            if (d < 1 || d > 31 || mo < 1 || mo > 12) return null;
            Calendar c = Calendar.getInstance();
            c.clear();
            int h = m.group(4) != null ? Integer.parseInt(m.group(4)) : 12;
            int mi = m.group(5) != null ? Integer.parseInt(m.group(5)) : 0;
            int s = m.group(6) != null ? Integer.parseInt(m.group(6)) : 0;
            c.set(y, mo - 1, d, h, mi, s);
            return c.getTimeInMillis();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Double num(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String first(Pattern p, String t) {
        Matcher m = p.matcher(t);
        return m.find() ? m.group(1) : "";
    }

    private static String group(Pattern p, String t) {
        Matcher m = p.matcher(t);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Keeps only the name: stops at "بمبلغ", "بتاريخ", "الرصيد"… and drops trailing dots. */
    private static String cutName(String m) {
        if (m == null) return "";
        Matcher e = NAME_END.matcher(m);
        String s = e.find() ? m.substring(0, e.start()) : m;
        return s.replaceAll("[.،,\\s]+$", "").trim();
    }

    private static String cleanMerchant(String m) {
        String s = m.trim();
        s = s.replaceAll("\\s+JOR?$", "").replaceAll("\\s+(AMMAN|IRBID|ZARQA|AQABA)$", "").trim();
        if (s.isEmpty()) s = m.trim();
        return s;
    }
}
