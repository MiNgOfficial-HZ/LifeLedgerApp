package com.lifebook.ledger.page;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.util.Async;
import com.lifebook.ledger.util.TipsEngine;
import com.lifebook.ledger.util.U;
import com.lifebook.ledger.view.BarChartView;
import com.lifebook.ledger.view.PieChartView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalysisPage {

    private final MainActivity act;
    private final DbHelper db;
    private String month = U.currentMonth();
    private String type = DbHelper.TYPE_EXPENSE;
    private int level = 1;

    private TextView tvMonth;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvBalance;
    private TextView tvPieEmpty;
    private TextView chipExpOut;
    private TextView chipIncOut;
    private TextView chipLv1;
    private TextView chipLv2;
    private LinearLayout bookBox;
    private PieChartView pie;
    private BarChartView bar;
    private LinearLayout legendBox;
    private LinearLayout tipsBox;
    private List<PieChartView.Slice> currentSlices = new ArrayList<>();

    public AnalysisPage(MainActivity a) {
        act = a;
        db = a.db;
        tvMonth = a.findViewById(R.id.tv_month_an);
        tvIncome = a.findViewById(R.id.tv_an_income);
        tvExpense = a.findViewById(R.id.tv_an_expense);
        tvBalance = a.findViewById(R.id.tv_an_balance);
        tvPieEmpty = a.findViewById(R.id.tv_pie_empty);
        chipExpOut = a.findViewById(R.id.chip_exp_out);
        chipIncOut = a.findViewById(R.id.chip_inc_out);
        chipLv1 = a.findViewById(R.id.chip_lv1);
        chipLv2 = a.findViewById(R.id.chip_lv2);
        bookBox = a.findViewById(R.id.book_row_an);
        pie = a.findViewById(R.id.pie_chart);
        bar = a.findViewById(R.id.bar_chart);
        legendBox = a.findViewById(R.id.legend_container);
        tipsBox = a.findViewById(R.id.tips_container);

        a.findViewById(R.id.btn_prev_an).setOnClickListener(v -> {
            month = U.shiftMonth(month, -1);
            show();
        });
        a.findViewById(R.id.btn_next_an).setOnClickListener(v -> {
            month = U.shiftMonth(month, 1);
            show();
        });
        chipExpOut.setOnClickListener(v -> {
            type = DbHelper.TYPE_EXPENSE;
            show();
        });
        chipIncOut.setOnClickListener(v -> {
            type = DbHelper.TYPE_INCOME;
            show();
        });
        chipLv1.setOnClickListener(v -> {
            level = 1;
            show();
        });
        chipLv2.setOnClickListener(v -> {
            level = 2;
            show();
        });
        pie.setOnSliceTap(slice -> showSliceDetail(slice));
    }

    public void show() {
        act.ensureCurrentBook();
        final String book = act.currentBook();
        act.renderBookSelector(bookBox, this::show);
        tvMonth.setText(U.monthLabel(month));
        long[] t = db.monthTotals(month, book);
        tvIncome.setText(U.money(t[0]));
        tvExpense.setText(U.money(t[1]));
        long bal = t[0] - t[1];
        tvBalance.setText((bal < 0 ? "-" : "") + U.money(Math.abs(bal)));
        tvBalance.setTextColor(act.getResources().getColor(bal < 0 ? R.color.expense : R.color.income));

        styleChip(chipExpOut, DbHelper.TYPE_EXPENSE.equals(type));
        styleChip(chipIncOut, DbHelper.TYPE_INCOME.equals(type));
        styleChip(chipLv1, level == 1);
        styleChip(chipLv2, level == 2);

        Async.run(() -> {
            Map<String, Long> byCat = db.sumByCat(month, type, level, book);
            long[] days = new long[31];
            db.expenseByDay(month, days, book);
            List<TipsEngine.Tip> tips = TipsEngine.generate(db, month, book);
            return new Object[]{byCat, days, tips};
        }, res -> {
            if (!book.equals(act.currentBook())) return;
            Map<String, Long> byCat = (Map<String, Long>) res[0];
            long[] days = (long[]) res[1];
            List<TipsEngine.Tip> tips = (List<TipsEngine.Tip>) res[2];
            renderCharts(byCat, t, days, tips);
        });
    }

    private void renderCharts(Map<String, Long> byCat, long[] t, long[] days, List<TipsEngine.Tip> tips) {
        long total = DbHelper.TYPE_EXPENSE.equals(type) ? t[1] : t[0];
        List<PieChartView.Slice> slices = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Long> e : byCat.entrySet()) {
            PieChartView.Slice s = new PieChartView.Slice();
            s.label = e.getKey();
            s.value = e.getValue();
            s.color = U.CHART_COLORS[i % U.CHART_COLORS.length];
            slices.add(s);
            i++;
        }
        currentSlices = slices;
        pie.setData(slices,
                (DbHelper.TYPE_EXPENSE.equals(type) ? "本月支出" : "本月收入"),
                "¥" + U.money(total));
        tvPieEmpty.setVisibility(slices.isEmpty() ? View.VISIBLE : View.GONE);

        legendBox.removeAllViews();
        for (PieChartView.Slice s : slices) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, U.dp(act, 5), 0, U.dp(act, 5));

            View dot = new View(act);
            dot.setBackgroundResource(R.drawable.bg_dot);
            dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(s.color));
            row.addView(dot, new LinearLayout.LayoutParams(U.dp(act, 10), U.dp(act, 10)));

            TextView name = new TextView(act);
            name.setText(s.label);
            name.setTextSize(13);
            name.setTextColor(act.getResources().getColor(R.color.text_main));
            name.setPadding(U.dp(act, 8), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView money = new TextView(act);
            money.setText(U.money(s.value));
            money.setTextSize(13);
            money.setTextColor(act.getResources().getColor(R.color.text_main));
            row.addView(money);

            TextView pct = new TextView(act);
            double p = total > 0 ? s.value * 100.0 / total : 0;
            pct.setText(String.format(Locale.US, "%.1f%%", p));
            pct.setTextSize(12);
            pct.setTextColor(act.getResources().getColor(R.color.text_sub));
            pct.setGravity(Gravity.END);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(U.dp(act, 56), LinearLayout.LayoutParams.WRAP_CONTENT);
            pp.leftMargin = U.dp(act, 8);
            row.addView(pct, pp);
            legendBox.addView(row);
        }

        bar.setData(days);

        tipsBox.removeAllViews();
        for (TipsEngine.Tip tip : tips) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, U.dp(act, 6), 0, U.dp(act, 6));

            TextView em = new TextView(act);
            em.setText(tip.emoji);
            em.setTextSize(15);
            row.addView(em);

            TextView tx = new TextView(act);
            tx.setText(tip.text);
            tx.setTextSize(13);
            tx.setTextColor(act.getResources().getColor(R.color.text_main));
            tx.setLineSpacing(U.dp(act, 3), 1f);
            tx.setPadding(U.dp(act, 8), 0, 0, 0);
            row.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tipsBox.addView(row);
        }
    }

    private void showSliceDetail(final PieChartView.Slice slice) {
        final String book = act.currentBook();
        final int lv = level;
        Async.run(() -> {
            List<Record> all = db.records(month, type, book);
            List<Record> matched = new ArrayList<>();
            for (Record r : all) {
                String key = lv == 1
                        ? (r.cat1Name == null ? "" : r.cat1Name)
                        : (r.cat2Name != null && !r.cat2Name.isEmpty() ? r.cat2Name : r.cat1Name);
                if (slice.label.equals(key) || (key.isEmpty() && "未分类".equals(slice.label))) {
                    matched.add(r);
                }
            }
            long sum = 0;
            for (Record r : matched) sum += r.amountCents;
            return new Object[]{matched, sum};
        }, res -> {
            List<Record> matched = (List<Record>) res[0];
            long sum = (Long) res[1];
            StringBuilder sb = new StringBuilder();
            sb.append("「").append(slice.label).append("」共 ").append(matched.size())
                    .append(" 笔，合计 ¥").append(U.money(sum)).append("：\n\n");
            for (Record r : matched) {
                sb.append(U.dateLabel(r.date)).append("  ")
                        .append(DbHelper.TYPE_INCOME.equals(r.type) ? "+" : "-")
                        .append(U.money(r.amountCents));
                if (r.note != null && !r.note.isEmpty()) sb.append("  ").append(r.note);
                sb.append("\n");
            }
            new AlertDialog.Builder(act)
                    .setTitle("分类明细")
                    .setMessage(sb.toString())
                    .setPositiveButton("知道了", null)
                    .show();
        });
    }

    private void styleChip(TextView chip, boolean selected) {
        if (selected) {
            chip.setBackgroundResource(R.drawable.bg_seg_item);
            chip.setTextColor(act.getResources().getColor(R.color.primary));
            chip.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            chip.setBackgroundColor(Color.TRANSPARENT);
            chip.setTextColor(act.getResources().getColor(R.color.chip_text));
            chip.setTypeface(Typeface.DEFAULT);
        }
    }
}
