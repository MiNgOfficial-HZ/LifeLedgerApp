package com.lifebook.ledger.util;

import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 基于本机数据的规则化「智能建议」引擎（完全离线） */
public class TipsEngine {

    public static class Tip {
        public final String emoji;
        public final String text;

        public Tip(String emoji, String text) {
            this.emoji = emoji;
            this.text = text;
        }
    }

    public static List<Tip> generate(DbHelper db, String month, String book) {
        List<Tip> out = new ArrayList<>();
        long[] t = db.monthTotals(month, book);
        long income = t[0];
        long expense = t[1];
        int cnt = db.recordCount(month, null, book);
        if (cnt == 0) {
            out.add(new Tip("👋", "这个月还没有任何记录。从首页的「＋」开始记第一笔吧，坚持记录才能看清钱的流向。"));
            return out;
        }
        if (income == 0) {
            out.add(new Tip("💡", "本月还没有收入记录。如果收到父母给的生活费，也别忘了记一笔收入哦。"));
        }
        if (income > 0 && expense > 0) {
            long bal = income - expense;
            double rate = bal * 100.0 / income;
            if (bal < 0) {
                out.add(new Tip("⚠️", "本月支出比收入多了 ¥" + U.money(-bal) + "，已经超支。建议优先压缩外卖、奶茶、娱乐等非必要开销。"));
            } else if (rate < 10) {
                out.add(new Tip("🧷", "本月结余率只有 " + pct(rate) + "%，离攒钱目标还有距离。试试先把收入的一成单独存起来。"));
            } else if (rate >= 30) {
                out.add(new Tip("🎉", "本月结余率 " + pct(rate) + "%，储蓄习惯很棒，继续保持！"));
            } else {
                out.add(new Tip("✅", "本月收支平稳，结余 ¥" + U.money(bal) + "。"));
            }
        }
        Map<String, Long> byCat = db.sumByCat(month, DbHelper.TYPE_EXPENSE, 1, book);
        if (!byCat.isEmpty() && expense > 0) {
            Map.Entry<String, Long> top = null;
            for (Map.Entry<String, Long> e : byCat.entrySet()) {
                if (top == null || e.getValue() > top.getValue()) top = e;
            }
            double share = top.getValue() * 100.0 / expense;
            if (share >= 45) {
                out.add(new Tip("🍜", "「" + top.getKey() + "」占了本月支出的 " + pct(share) + "%，占比偏高。可以翻翻明细，看看里面有没有能压缩的部分。"));
            } else if (share >= 30) {
                out.add(new Tip("📊", "本月最大支出是「" + top.getKey() + "」(" + pct(share) + "%)，留意一下这类花销的增长节奏。"));
            }
        }
        int teaCount = 0;
        long teaCents = 0;
        for (Record r : db.records(month, DbHelper.TYPE_EXPENSE, book)) {
            String names = (r.cat1Name == null ? "" : r.cat1Name) + " " + (r.cat2Name == null ? "" : r.cat2Name);
            if (names.contains("奶茶")) {
                teaCount++;
                teaCents += r.amountCents;
            }
        }
        if (teaCount > 0 && teaCents >= 3000) {
            out.add(new Tip("🧋", "本月喝了 " + teaCount + " 次奶茶，共 ¥" + U.money(teaCents) + "。每周少喝一杯，一个月大概能省下 50 元，还能少长点肉。"));
        }
        long[] days = new long[31];
        db.expenseByDay(month, days, book);
        long max = 0;
        int maxDay = -1;
        for (int i = 0; i < 31; i++) {
            if (days[i] > max) {
                max = days[i];
                maxDay = i + 1;
            }
        }
        if (max > 0 && expense > 0 && max >= expense / 30.0 * 3) {
            out.add(new Tip("⚡", U.monthLabel(month) + maxDay + "日单日支出 ¥" + U.money(max) + "，明显高于日均水平。大额消费前先想想：是「需要」还是「想要」？"));
        }
        String prev = U.shiftMonth(month, -1);
        long[] pt = db.monthTotals(prev, book);
        if (pt[1] > 0 && expense > pt[1] * 1.2) {
            out.add(new Tip("📈", "本月支出比上月多了 " + pct((expense - pt[1]) * 100.0 / pt[1]) + "%。翻翻明细，看看是哪一类悄悄变多了。"));
        }
        Map<String, Long> inCat = db.sumByCat(month, DbHelper.TYPE_INCOME, 1, book);
        if (!inCat.isEmpty() && income > 0) {
            Map.Entry<String, Long> top = null;
            for (Map.Entry<String, Long> e : inCat.entrySet()) {
                if (top == null || e.getValue() > top.getValue()) top = e;
            }
            double share = top.getValue() * 100.0 / income;
            if (share >= 80) {
                out.add(new Tip("🎓", "收入主要来自「" + top.getKey() + "」(" + pct(share) + "%)，来源比较单一。可以考虑发展一个副业，让收入更稳定。"));
            }
        }
        if (out.isEmpty()) {
            out.add(new Tip("🌱", "数据还不多，多记几笔后这里会给出更有针对性的分析。"));
        }
        return out;
    }

    private static String pct(double v) {
        return String.format(Locale.US, "%.0f%%", v);
    }
}
