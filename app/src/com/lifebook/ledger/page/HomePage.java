package com.lifebook.ledger.page;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.ui.BudgetDialog;
import com.lifebook.ledger.ui.RecordAdapter;
import com.lifebook.ledger.ui.TransferDialog;
import com.lifebook.ledger.util.Async;
import com.lifebook.ledger.util.U;

import java.util.ArrayList;
import java.util.List;

public class HomePage {

    private final MainActivity act;
    private final DbHelper db;
    private String month = U.currentMonth();

    private TextView tvMonth;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvBalance;
    private TextView tvTotalBalanceLabel;
    private TextView tvTotalBalanceHome;
    private TextView tvRecentCount;
    private TextView tvEmpty;
    private TextView tvBudgetProgress;
    private TextView tvBudgetDetail;
    private LinearLayout bookBox;
    private LinearLayout budgetRow;
    private ListView list;
    private RecordAdapter adapter;
    private final List<Record> recent = new ArrayList<>();

    public HomePage(MainActivity a) {
        act = a;
        db = a.db;
        tvMonth = a.findViewById(R.id.tv_month_home);
        tvIncome = a.findViewById(R.id.tv_income_home);
        tvExpense = a.findViewById(R.id.tv_expense_home);
        tvBalance = a.findViewById(R.id.tv_balance_home);
        tvTotalBalanceLabel = a.findViewById(R.id.tv_total_balance_label);
        tvTotalBalanceHome = a.findViewById(R.id.tv_total_balance_home);
        tvRecentCount = a.findViewById(R.id.tv_recent_count);
        tvEmpty = a.findViewById(R.id.tv_empty_home);
        tvBudgetProgress = a.findViewById(R.id.tv_budget_progress);
        tvBudgetDetail = a.findViewById(R.id.tv_budget_detail);
        bookBox = a.findViewById(R.id.book_row_home);
        budgetRow = a.findViewById(R.id.budget_row);
        list = a.findViewById(R.id.list_recent);

        adapter = new RecordAdapter(act, new RecordAdapter.Listener() {
            @Override
            public void onClick(Record r) {
                act.openEditRecord(r);
            }

            @Override
            public void onEdit(Record r) {
                act.openEditRecord(r);
            }

            @Override
            public void onDelete(Record r) {
                act.confirmDeleteRecord(r);
            }

            @Override
            public void onLongClick(Record r) {
            }
        });
        list.setAdapter(adapter);

        a.findViewById(R.id.btn_prev_home).setOnClickListener(v -> {
            month = U.shiftMonth(month, -1);
            show();
        });
        a.findViewById(R.id.btn_next_home).setOnClickListener(v -> {
            month = U.shiftMonth(month, 1);
            show();
        });
        a.findViewById(R.id.fab_home).setOnClickListener(v -> act.openAddRecord(DbHelper.TYPE_EXPENSE));
        a.findViewById(R.id.btn_transfer_home).setOnClickListener(v -> TransferDialog.show(act, db, null, this::show));
        a.findViewById(R.id.btn_budget_edit).setOnClickListener(v -> BudgetDialog.show(act, db, act.currentBook(), this::show));
    }

    public void show() {
        act.ensureCurrentBook();
        final String book = act.currentBook();
        act.renderBookSelector(bookBox, this::show);
        tvMonth.setText(U.monthLabel(month));
        Async.run(() -> {
            long[] t = db.monthTotals(month, book);
            int total = db.recordCount(month, null, book);
            List<Record> all = db.records(month, null, book);
            long budget = db.budgetTotal(book, DbHelper.TYPE_EXPENSE);
            long totalBalance = db.bookBalance(book);
            String name = db.bookName(book);
            return new Object[]{t, total, all, budget, totalBalance, name};
        }, res -> {
            if (!book.equals(act.currentBook())) return;
            long[] t = (long[]) res[0];
            int total = (Integer) res[1];
            long budget = (Long) res[3];
            long totalBalance = (Long) res[4];
            String name = (String) res[5];
            @SuppressWarnings("unchecked")
            List<Record> all = (List<Record>) res[2];
            tvIncome.setText(U.money(t[0]));
            tvExpense.setText(U.money(t[1]));
            long bal = t[0] - t[1];
            tvBalance.setText((bal < 0 ? "-" : "") + U.money(Math.abs(bal)));
            tvBalance.setTextColor(act.getResources().getColor(bal < 0 ? R.color.expense : R.color.text_main));

            tvTotalBalanceLabel.setText(name + " · 累计结余");
            tvTotalBalanceHome.setText((totalBalance < 0 ? "-" : "") + U.money(Math.abs(totalBalance)));
            tvTotalBalanceHome.setTextColor(act.getResources().getColor(totalBalance < 0 ? R.color.expense : R.color.text_main));

            recent.clear();
            recent.addAll(all.size() > 10 ? all.subList(0, 10) : all);
            adapter.setData(recent);
            adapter.notifyDataSetChanged();
            tvRecentCount.setText("本月共 " + total + " 笔");
            tvEmpty.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
            list.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);

            renderBudget(budget, t[1]);
        });
    }

    private void renderBudget(long budget, long spent) {
        if (budget <= 0) {
            budgetRow.setVisibility(View.GONE);
            return;
        }
        budgetRow.setVisibility(View.VISIBLE);
        long remain = budget - spent;
        double pct = budget > 0 ? spent * 100.0 / budget : 0;
        tvBudgetProgress.setText("本月预算 ¥" + U.money(budget) + " · 已用 " + String.format("%.0f%%", pct));
        tvBudgetDetail.setText(remain >= 0
                ? "剩余 ¥" + U.money(remain)
                : "已超支 ¥" + U.money(-remain));
    }
}
