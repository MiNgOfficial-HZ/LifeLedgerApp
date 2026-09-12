package com.lifebook.ledger.util;

import android.content.Context;
import android.widget.Toast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public final class U {
    private U() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /** 分 -> 元字符串，如 123456 -> "1234.56" */
    public static String money(long cents) {
        long abs = Math.abs(cents);
        long yuan = abs / 100;
        long fen = abs % 100;
        String s;
        if (fen == 0) {
            s = String.valueOf(yuan);
        } else {
            String f = String.valueOf(fen);
            if (f.length() == 1) f = "0" + f;
            s = yuan + "." + f;
        }
        if (cents < 0) s = "-" + s;
        return s;
    }

    /** 元字符串 -> 分，非法输入抛异常 */
    public static long parseMoney(String input) throws Exception {
        if (input == null) throw new Exception("请输入金额");
        String s = input.trim().replace(",", "").replace("，", "");
        if (s.isEmpty()) throw new Exception("请输入金额");
        BigDecimal bd = new BigDecimal(s);
        if (bd.signum() <= 0) throw new Exception("金额必须大于 0");
        if (bd.compareTo(new BigDecimal("99999999")) > 0) throw new Exception("金额太大了");
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.movePointRight(2).longValueExact();
    }

    public static String today() {
        return LocalDate.now().toString();
    }

    public static String currentMonth() {
        return YearMonth.now().toString();
    }

    public static String monthLabel(String ym) {
        return YearMonth.parse(ym).format(DateTimeFormatter.ofPattern("yyyy年MM月"));
    }

    public static String shiftMonth(String ym, int delta) {
        return YearMonth.parse(ym).plusMonths(delta).toString();
    }

    public static String dateLabel(String d) {
        LocalDate ld = LocalDate.parse(d);
        String wd = ld.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.CHINA);
        return ld.format(DateTimeFormatter.ofPattern("MM月dd日")) + " " + wd;
    }

    public static String emojiFor(String name, boolean income) {
        if (name == null || name.isEmpty()) return income ? "💰" : "🛒";
        String[][] map = {
            {"奶茶", "🧋"}, {"食堂", "🍚"}, {"三餐", "🍽️"}, {"外卖", "🥡"}, {"零食", "🍿"},
            {"水果", "🍎"}, {"聚餐", "🍻"}, {"吃喝", "🍜"}, {"餐", "🍜"},
            {"旅行", "✈️"}, {"门票", "🎫"}, {"住宿", "🏨"}, {"车票", "🎫"}, {"火车", "🚄"},
            {"公交", "🚌"}, {"地铁", "🚇"}, {"打车", "🚕"}, {"单车", "🚲"}, {"交通", "🚌"},
            {"购物", "🛍️"}, {"衣物", "👕"}, {"鞋", "👟"}, {"日用", "🧴"}, {"数码", "📱"},
            {"学习", "📖"}, {"书籍", "📚"}, {"文具", "✏️"}, {"网课", "💻"},
            {"娱乐", "🎮"}, {"电影", "🎬"}, {"游戏", "🎮"}, {"运动", "🏃"},
            {"医疗", "💊"}, {"药品", "💊"}, {"门诊", "🏥"},
            {"工资", "💰"}, {"实习", "💼"}, {"家教", "📚"}, {"兼职", "💼"},
            {"生活", "🏠"}, {"奖学", "🎓"}, {"竞赛", "🏆"}, {"奖金", "🏆"},
            {"红包", "🧧"}, {"压岁", "🧧"}, {"二手", "♻️"}, {"出售", "♻️"}, {"闲置", "♻️"},
            {"补助", "🎁"}, {"人情", "🎁"}, {"礼物", "🎁"}, {"请客", "🍽️"}, {"收入", "💵"}
        };
        for (String[] pair : map) {
            if (name.contains(pair[0])) return pair[1];
        }
        return income ? "💵" : "🏷️";
    }

    public static final int[] CHART_COLORS = {
        0xFF0A84FF, 0xFF5E5CE6, 0xFF64D2FF, 0xFFBF5AF2, 0xFFFF9F0A, 0xFFFF453A,
        0xFF30D158, 0xFF66D4CF, 0xFFFFD60A, 0xFFAC8E68, 0xFF98989D, 0xFF0A84FF
    };
}
