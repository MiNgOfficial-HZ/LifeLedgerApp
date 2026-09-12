package com.lifebook.ledger.page;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.ui.BookManageActivity;
import com.lifebook.ledger.ui.CategoryManageActivity;
import com.lifebook.ledger.ui.RecycleActivity;
import com.lifebook.ledger.ui.RecurringActivity;
import com.lifebook.ledger.util.BackupManager;
import com.lifebook.ledger.util.ExcelExporter;
import com.lifebook.ledger.util.LockUtil;
import com.lifebook.ledger.util.NotifyManager;
import com.lifebook.ledger.util.U;

import java.io.File;
import java.util.Locale;

public class SettingsPage {

    public static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MIME_ZIP = "application/zip";

    private final MainActivity act;

    public SettingsPage(MainActivity a) {
        act = a;
        a.findViewById(R.id.row_categories).setOnClickListener(v ->
                act.startActivity(new Intent(act, CategoryManageActivity.class)));
        a.findViewById(R.id.row_books).setOnClickListener(v ->
                act.startActivity(new Intent(act, BookManageActivity.class)));
        a.findViewById(R.id.row_recurring).setOnClickListener(v ->
                act.startActivity(new Intent(act, RecurringActivity.class)));
        a.findViewById(R.id.row_recycle).setOnClickListener(v ->
                act.startActivity(new Intent(act, RecycleActivity.class)));
        a.findViewById(R.id.row_export_excel).setOnClickListener(v -> pickMonthAndExport());
        a.findViewById(R.id.row_export_zip).setOnClickListener(v -> exportZip());
        a.findViewById(R.id.row_import).setOnClickListener(v -> act.pickBackup());
        a.findViewById(R.id.row_clear).setOnClickListener(v -> confirmClear());

        // ===== 指纹解锁 =====
        final Switch swFp = a.findViewById(R.id.sw_fingerprint);
        swFp.setChecked(LockUtil.enabled(a));
        swFp.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                if (!LockUtil.canUse(a)) {
                    U.toast(a, "此手机未设置锁屏指纹，无法开启");
                    swFp.setChecked(false);
                    return;
                }
                LockUtil.setEnabled(a, true);
                U.toast(a, "指纹解锁已开启");
            } else {
                LockUtil.setEnabled(a, false);
            }
        });

        // ===== 提醒 =====
        final TextView tvDaily = a.findViewById(R.id.tv_daily_time);
        final TextView tvWeekly = a.findViewById(R.id.tv_weekly_time);
        final TextView tvMonthly = a.findViewById(R.id.tv_monthly_time);
        refreshTimeTexts(tvDaily, tvWeekly, tvMonthly);

        Switch swDaily = a.findViewById(R.id.sw_daily);
        Switch swWeekly = a.findViewById(R.id.sw_weekly);
        Switch swMonthly = a.findViewById(R.id.sw_monthly);

        swDaily.setChecked(NotifyManager.enabled(a, "daily"));
        swWeekly.setChecked(NotifyManager.enabled(a, "weekly"));
        swMonthly.setChecked(NotifyManager.enabled(a, "monthly"));

        swDaily.setOnCheckedChangeListener((b, checked) -> {
            NotifyManager.setEnabled(act, "daily", checked);
            NotifyManager.scheduleAll(act);
            if (checked) NotifyManager.requestExactIfNeeded(act);
        });
        swWeekly.setOnCheckedChangeListener((b, checked) -> {
            NotifyManager.setEnabled(act, "weekly", checked);
            NotifyManager.scheduleAll(act);
            if (checked) NotifyManager.requestExactIfNeeded(act);
        });
        swMonthly.setOnCheckedChangeListener((b, checked) -> {
            NotifyManager.setEnabled(act, "monthly", checked);
            NotifyManager.scheduleAll(act);
            if (checked) NotifyManager.requestExactIfNeeded(act);
        });

        a.findViewById(R.id.row_daily).setOnClickListener(v -> pickTime("daily", tvDaily, tvWeekly, tvMonthly));
        a.findViewById(R.id.row_weekly).setOnClickListener(v -> pickTime("weekly", tvDaily, tvWeekly, tvMonthly));
        a.findViewById(R.id.row_monthly).setOnClickListener(v -> pickTime("monthly", tvDaily, tvWeekly, tvMonthly));

        // ===== 关于 =====
        TextView about = a.findViewById(R.id.tv_about_text);
        about.setText("· 本应用完全离线运行，不申请网络权限，所有数据只保存在本机。\n"
                + "· 账本：默认有主账本（日常收支）+ 小金库（零花钱/小目标），顶部可切换；"
                + "你还可以在「管理 → 账本管理」里自行新建更多账本。\n"
                + "· 新功能：账本间转账、按分类预算、搜索与金额/分类筛选、每月自动周期记账、深色模式，均可离线使用。\n"
                + "· 删除保护：删除的账目进「回收站」，可恢复；导入备份可选「覆盖」或「合并」。\n"
                + "· 换手机：旧手机「导出全部备份」→ 把 zip 发给新手机 → 新手机「导入备份」。\n"
                + "· 出游账本：每次出游单独记账，结束后可合并进任意一个账本 / 不合并。\n"
                + "· 分析数据：导出当月 Excel（含全部账本）后，可发送到电脑或 WPS 打开。\n"
                + "· 已适配小米 HyperOS（澎湃 OS），安装时如提示风险，选择「仍要安装」即可。");
        TextView ver = a.findViewById(R.id.tv_version);
        ver.setText("生活记账本 v2.0 · 本地数据，安心使用");
    }

    public void show() {
    }

    private void refreshTimeTexts(TextView tvDaily, TextView tvWeekly, TextView tvMonthly) {
        int d = NotifyManager.minutes(act, "daily");
        int w = NotifyManager.minutes(act, "weekly");
        int m = NotifyManager.minutes(act, "monthly");
        tvDaily.setText("每天 " + fmt(d));
        tvWeekly.setText("每周一 " + fmt(w) + " · 发送上周账单");
        tvMonthly.setText("每月 1 日 " + fmt(m) + " · 发送上月账单");
    }

    private void pickTime(final String key, final TextView tvDaily, final TextView tvWeekly, final TextView tvMonthly) {
        int cur = NotifyManager.minutes(act, key);
        new TimePickerDialog(act, (tp, h, min) -> {
            NotifyManager.setMinutes(act, key, h * 60 + min);
            refreshTimeTexts(tvDaily, tvWeekly, tvMonthly);
            NotifyManager.scheduleAll(act);
        }, cur / 60, cur % 60, true).show();
    }

    private static String fmt(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private void confirmClear() {
        new AlertDialog.Builder(act)
                .setTitle("清空全部数据")
                .setMessage("将删除本机全部记账记录、出游账本、转账、预算、周期记账与已删除的回收站内容（预设分类会恢复，自定义账本保留）。\n此操作不可恢复，建议先导出备份。")
                .setPositiveButton("清空", (d, w) -> {
                    act.db.clearAll();
                    U.toast(act, "已清空");
                    act.refreshData();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void exportZip() {
        try {
            File f = BackupManager.exportZip(act, act.db);
            act.showExportResult(f, MIME_ZIP);
        } catch (Exception e) {
            U.toast(act, "导出失败：" + e.getMessage());
        }
    }

    private void pickMonthAndExport() {
        final String[] month = {U.currentMonth()};
        View v = act.getLayoutInflater().inflate(R.layout.dialog_pick_month, null);
        final TextView tv = v.findViewById(R.id.tv_pm_month);
        tv.setText(U.monthLabel(month[0]));
        v.findViewById(R.id.btn_pm_prev).setOnClickListener(x -> {
            month[0] = U.shiftMonth(month[0], -1);
            tv.setText(U.monthLabel(month[0]));
        });
        v.findViewById(R.id.btn_pm_next).setOnClickListener(x -> {
            month[0] = U.shiftMonth(month[0], 1);
            tv.setText(U.monthLabel(month[0]));
        });
        final AlertDialog dlg = new AlertDialog.Builder(act).setView(v).create();
        v.findViewById(R.id.btn_pm_ok).setOnClickListener(x -> {
            dlg.dismiss();
            doExportExcel(month[0]);
        });
        v.findViewById(R.id.btn_pm_cancel).setOnClickListener(x -> dlg.dismiss());
        dlg.show();
    }

    private void doExportExcel(String ym) {
        try {
            File f = ExcelExporter.export(act, act.db, ym);
            act.showExportResult(f, MIME_XLSX);
        } catch (Exception e) {
            U.toast(act, "导出失败：" + e.getMessage());
        }
    }
}
