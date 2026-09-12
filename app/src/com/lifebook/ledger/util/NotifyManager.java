package com.lifebook.ledger.util;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.receiver.NotifyReceiver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本地通知：每日记账提醒 / 每周周报 / 每月月报（完全离线） */
public class NotifyManager {

    public static final String CHANNEL_ID = "ledger_reminder";
    private static final String PREFS = "notify";

    public static final int REQ_DAILY = 1001;
    public static final int REQ_WEEKLY = 1002;
    public static final int REQ_MONTHLY = 1003;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c, String key) {
        return prefs(c).getBoolean(key + "_enabled", false);
    }

    public static void setEnabled(Context c, String key, boolean v) {
        prefs(c).edit().putBoolean(key + "_enabled", v).apply();
    }

    public static int minutes(Context c, String key) {
        int def;
        if ("daily".equals(key)) def = 21 * 60;
        else if ("weekly".equals(key)) def = 20 * 60;
        else def = 9 * 60;
        return prefs(c).getInt(key + "_minutes", def);
    }

    public static void setMinutes(Context c, String key, int minutes) {
        prefs(c).edit().putInt(key + "_minutes", minutes).apply();
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "记账提醒", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("每日记账提醒与每周/每月账本");
                nm.createNotificationChannel(ch);
            }
        }
    }

    public static void scheduleAll(Context c) {
        scheduleOne(c, "daily", REQ_DAILY, nextDaily(minutes(c, "daily")));
        scheduleOne(c, "weekly", REQ_WEEKLY, nextWeekly(minutes(c, "weekly")));
        scheduleOne(c, "monthly", REQ_MONTHLY, nextMonthly(minutes(c, "monthly")));
    }

    private static void scheduleOne(Context c, String key, int req, long triggerAt) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (!enabled(c, key)) {
            am.cancel(pending(c, req));
            return;
        }
        PendingIntent pi = pending(c, req);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        }
    }

    public static PendingIntent pending(Context c, int req) {
        Intent it = new Intent(c, NotifyReceiver.class);
        it.putExtra("code", req);
        return PendingIntent.getBroadcast(c, req, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static long nextDaily(int minutes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime t = now.toLocalDate().atTime(minutes / 60, minutes % 60);
        if (!t.isAfter(now)) t = t.plusDays(1);
        return toMillis(t);
    }

    private static long nextWeekly(int minutes) {
        LocalDateTime now = LocalDateTime.now();
        int dow = now.getDayOfWeek().getValue();
        LocalDate next = now.toLocalDate().plusDays((8 - dow) % 7);
        LocalDateTime t = next.atTime(minutes / 60, minutes % 60);
        if (!t.isAfter(now)) t = t.plusDays(7);
        return toMillis(t);
    }

    private static long nextMonthly(int minutes) {
        LocalDateTime now = LocalDateTime.now();
        YearMonth ym = YearMonth.from(now.toLocalDate());
        LocalDateTime t = ym.atDay(1).atTime(minutes / 60, minutes % 60);
        if (!t.isAfter(now)) t = ym.plusMonths(1).atDay(1).atTime(minutes / 60, minutes % 60);
        return toMillis(t);
    }

    private static long toMillis(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 请在系统设置中允许精确闹钟（Android 12+） */
    public static void requestExactIfNeeded(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                try {
                    Intent it = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    it.setData(Uri.parse("package:" + c.getPackageName()));
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(it);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ===== 周报 / 月报文案 =====
    public static String weeklyReportText(Context c) {
        LocalDate today = LocalDate.now();
        int dow = today.getDayOfWeek().getValue();
        LocalDate weekEnd = today.minusDays(dow);
        LocalDate weekStart = weekEnd.minusDays(6);
        String period = "上周（" + U.dateLabel(weekStart.toString()) + " - " + U.dateLabel(weekEnd.toString()) + "）";
        return reportFor(c, weekStart.toString(), weekEnd.toString(), period);
    }

    public static String monthlyReportText(Context c) {
        YearMonth ym = YearMonth.now().minusMonths(1);
        String period = ym.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
        return reportFor(c, ym.atDay(1).toString(), ym.atEndOfMonth().toString(), period);
    }

    private static String reportFor(Context c, String start, String end, String period) {
        DbHelper db = new DbHelper(c);
        List<Record> recs = db.recordsBetween(start, end, null, null);
        long inc = 0;
        long exp = 0;
        int n = 0;
        Map<String, Long> byCat = new LinkedHashMap<>();
        for (Record r : recs) {
            n++;
            if (r.isIncome()) {
                inc += r.amountCents;
            } else {
                exp += r.amountCents;
                String k = (r.cat1Name == null || r.cat1Name.isEmpty()) ? "未分类" : r.cat1Name;
                Long old = byCat.get(k);
                byCat.put(k, (old == null ? 0 : old) + r.amountCents);
            }
        }
        long bal = inc - exp;
        StringBuilder sb = new StringBuilder();
        sb.append(period).append('\n');
        sb.append("收入 ¥").append(U.money(inc)).append("  ·  支出 ¥").append(U.money(exp)).append('\n');
        sb.append("结余 ").append(bal < 0 ? "-" : "").append("¥").append(U.money(Math.abs(bal)));
        String topKey = null;
        long topVal = 0;
        for (Map.Entry<String, Long> e : byCat.entrySet()) {
            if (e.getValue() > topVal) {
                topVal = e.getValue();
                topKey = e.getKey();
            }
        }
        if (topKey != null) {
            sb.append('\n').append("最大开支：").append(topKey).append(" ¥").append(U.money(topVal));
        }
        sb.append('\n').append("共 ").append(n).append(" 笔记录 · 点击查看明细");
        return sb.toString();
    }
}
