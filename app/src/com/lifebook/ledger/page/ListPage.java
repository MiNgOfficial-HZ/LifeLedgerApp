package com.lifebook.ledger.page;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Category;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.model.Transfer;
import com.lifebook.ledger.ui.RecordAdapter;
import com.lifebook.ledger.ui.TransferAdapter;
import com.lifebook.ledger.ui.TransferDialog;
import com.lifebook.ledger.util.Async;
import com.lifebook.ledger.util.U;

import java.util.ArrayList;
import java.util.List;

public class ListPage {

    private final MainActivity act;
    private final DbHelper db;
    private String month = U.currentMonth();
    private String filter = "all";
    private String searchQuery = "";
    private long amountMin = 0;
    private long amountMax = 0;
    private long cat1Filter = 0;
    private String cat1FilterName = "";

    private TextView tvMonth;
    private TextView tvSummary;
    private TextView tvEmpty;
    private TextView chipAll;
    private TextView chipExpense;
    private TextView chipIncome;
    private TextView chipTransfer;
    private LinearLayout bookBox;
    private ListView list;
    private EditText etSearch;
    private TextView btnFilter;
    private LinearLayout rowBulk;
    private RecordAdapter recordAdapter;
    private TransferAdapter transferAdapter;
    private final List<Record> recordData = new ArrayList<>();
    private final List<Transfer> transferData = new ArrayList<>();

    public ListPage(MainActivity a) {
        act = a;
        db = a.db;
        tvMonth = a.findViewById(R.id.tv_month_list);
        tvSummary = a.findViewById(R.id.tv_list_summary);
        tvEmpty = a.findViewById(R.id.tv_empty_list);
        chipAll = a.findViewById(R.id.chip_all);
        chipExpense = a.findViewById(R.id.chip_expense);
        chipIncome = a.findViewById(R.id.chip_income);
        chipTransfer = a.findViewById(R.id.chip_transfer);
        bookBox = a.findViewById(R.id.book_row_list);
        list = a.findViewById(R.id.list_records);
        etSearch = a.findViewById(R.id.et_search);
        btnFilter = a.findViewById(R.id.btn_filter);
        rowBulk = a.findViewById(R.id.row_bulk_actions);

        recordAdapter = new RecordAdapter(act, new RecordAdapter.Listener() {
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
                enterSelection();
            }
        });
        transferAdapter = new TransferAdapter(act, db, new TransferAdapter.Listener() {
            @Override
            public void onClick(Transfer t) {
                TransferDialog.show(act, db, t, ListPage.this::show);
            }

            @Override
            public void onEdit(Transfer t) {
                TransferDialog.show(act, db, t, ListPage.this::show);
            }

            @Override
            public void onDelete(Transfer t) {
                new AlertDialog.Builder(act)
                        .setTitle("删除转账")
                        .setMessage("确定删除这笔转账吗？（可到 管理→回收站 恢复）")
                        .setPositiveButton("删除", (d, w) -> {
                            db.deleteTransfer(t.id);
                            U.toast(act, "已删除");
                            show();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        a.findViewById(R.id.btn_prev_list).setOnClickListener(v -> {
            month = U.shiftMonth(month, -1);
            show();
        });
        a.findViewById(R.id.btn_next_list).setOnClickListener(v -> {
            month = U.shiftMonth(month, 1);
            show();
        });
        chipAll.setOnClickListener(v -> setFilter("all"));
        chipExpense.setOnClickListener(v -> setFilter("expense"));
        chipIncome.setOnClickListener(v -> setFilter("income"));
        chipTransfer.setOnClickListener(v -> setFilter("transfer"));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                searchQuery = s == null ? "" : s.toString().trim();
                show();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        btnFilter.setOnClickListener(v -> openFilter());

        a.findViewById(R.id.btn_bulk_cancel).setOnClickListener(v -> exitSelection());
        a.findViewById(R.id.btn_bulk_delete).setOnClickListener(v -> bulkDelete());
        a.findViewById(R.id.btn_bulk_recat).setOnClickListener(v -> bulkRecat());
    }

    private void setFilter(String f) {
        filter = f;
        exitSelection();
        show();
    }

    public void show() {
        act.ensureCurrentBook();
        String book = act.currentBook();
        act.renderBookSelector(bookBox, this::show);
        tvMonth.setText(U.monthLabel(month));
        styleChip(chipAll, "all".equals(filter));
        styleChip(chipExpense, "expense".equals(filter));
        styleChip(chipIncome, "income".equals(filter));
        styleChip(chipTransfer, "transfer".equals(filter));

        if ("transfer".equals(filter)) {
            Async.run(() -> db.transfers(month, book), list -> {
                if (!isCurrent(month, book)) return;
                showTransfers(list);
            });
        } else {
            String type = "all".equals(filter) ? null : filter;
            final String q = searchQuery;
            final long mn = amountMin, mx = amountMax, cid = cat1Filter;
            Async.run(() -> {
                List<Record> rec;
                if (hasFilter(q, mn, mx, cid)) {
                    rec = db.searchRecords(month, type, book, q, mn, mx, cid);
                } else {
                    rec = db.records(month, type, book);
                }
                long[] tot = db.monthTotals(month, book);
                int n = db.recordCount(month, type, book);
                return new Object[]{rec, tot, n};
            }, res -> {
                if (!isCurrent(month, book)) return;
                showRecords((List<Record>) res[0], (long[]) res[1], (Integer) res[2]);
            });
        }
    }

    private boolean isCurrent(String m, String b) {
        return m.equals(month) && b.equals(act.currentBook());
    }

    private boolean hasFilter(String q, long mn, long mx, long cid) {
        return (q != null && !q.isEmpty()) || mn > 0 || mx > 0 || cid > 0;
    }

    private void showRecords(List<Record> rec, long[] tot, int n) {
        if (rec == null) rec = new ArrayList<>();
        recordData.clear();
        recordData.addAll(rec);
        list.setAdapter(recordAdapter);
        recordAdapter.setData(recordData);
        recordAdapter.setSelectionMode(false);
        rowBulk.setVisibility(View.GONE);
        String extra = hasFilter(searchQuery, amountMin, amountMax, cat1Filter) ? "（筛选后）" : "";
        tvSummary.setText("共 " + n + " 笔" + extra + " · 支出 ¥" + U.money(tot[1]) + " · 收入 ¥" + U.money(tot[0]));
        boolean empty = rec.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showTransfers(List<Transfer> rec) {
        transferData.clear();
        if (rec != null) transferData.addAll(rec);
        list.setAdapter(transferAdapter);
        transferAdapter.setData(transferData);
        rowBulk.setVisibility(View.GONE);
        long total = 0;
        for (Transfer t : transferData) total += t.amountCents;
        tvSummary.setText("本月共 " + transferData.size() + " 笔转账 · 合计 ¥" + U.money(total));
        boolean empty = transferData.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openFilter() {
        FrameLayout frame = new FrameLayout(act);
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        EditText etMin = new EditText(act);
        etMin.setHint("最小金额（元，留空不限）");
        etMin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etMin.setTextColor(act.getResources().getColor(R.color.text_main));
        etMin.setHintTextColor(act.getResources().getColor(R.color.icon_gray));
        etMin.setSingleLine(true);
        EditText etMax = new EditText(act);
        etMax.setHint("最大金额（元，留空不限）");
        etMax.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etMax.setTextColor(act.getResources().getColor(R.color.text_main));
        etMax.setHintTextColor(act.getResources().getColor(R.color.icon_gray));
        etMax.setSingleLine(true);
        if (amountMin > 0) etMin.setText(U.money(amountMin));
        if (amountMax > 0) etMax.setText(U.money(amountMax));
        TextView cat = new TextView(act);
        cat.setPadding(U.dp(act, 2), U.dp(act, 10), U.dp(act, 2), U.dp(act, 10));
        cat.setTextSize(14);
        cat.setTextColor(act.getResources().getColor(R.color.primary));
        cat.setText(cat1FilterName.isEmpty() ? "分类：不限" : "分类：" + cat1FilterName);
        cat.setOnClickListener(v2 -> pickCategory(cat));
        col.addView(etMin, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(etMax, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(cat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.setPadding(U.dp(act, 20), U.dp(act, 8), U.dp(act, 20), 0);
        frame.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(act)
                .setTitle("筛选")
                .setView(frame)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        amountMin = etMin.getText().toString().trim().isEmpty() ? 0 : U.parseMoney(etMin.getText().toString());
                    } catch (Exception e) {
                        amountMin = 0;
                    }
                    try {
                        amountMax = etMax.getText().toString().trim().isEmpty() ? 0 : U.parseMoney(etMax.getText().toString());
                    } catch (Exception e) {
                        amountMax = 0;
                    }
                    show();
                })
                .setNeutralButton("清除", (d, w) -> {
                    amountMin = 0;
                    amountMax = 0;
                    cat1Filter = 0;
                    cat1FilterName = "";
                    show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickCategory(TextView cat) {
        final List<String> names = new ArrayList<>();
        final List<Object> vals = new ArrayList<>();
        names.add("不限");
        vals.add(0L);
        for (String type : new String[]{DbHelper.TYPE_EXPENSE, DbHelper.TYPE_INCOME}) {
            for (Category c : db.topCategories(type)) {
                names.add("[" + ("expense".equals(type) ? "支出" : "收入") + "] " + c.name);
                vals.add(c.id);
            }
        }
        new AlertDialog.Builder(act)
                .setTitle("选择分类")
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    long id = (Long) vals.get(w);
                    cat1Filter = id;
                    cat1FilterName = w == 0 ? "" : names.get(w);
                    cat.setText(cat1FilterName.isEmpty() ? "分类：不限" : "分类：" + cat1FilterName);
                })
                .show();
    }

    private void enterSelection() {
        if (!"transfer".equals(filter)) {
            recordAdapter.setSelectionMode(true);
            rowBulk.setVisibility(View.VISIBLE);
        }
    }

    private void exitSelection() {
        recordAdapter.setSelectionMode(false);
        rowBulk.setVisibility(View.GONE);
    }

    private void bulkDelete() {
        List<Long> ids = recordAdapter.selectedIds();
        if (ids.isEmpty()) {
            U.toast(act, "还没有选择记录");
            return;
        }
        new AlertDialog.Builder(act)
                .setTitle("批量删除")
                .setMessage("确定删除所选 " + ids.size() + " 笔吗？（可到 管理→回收站 恢复）")
                .setPositiveButton("删除", (d, w) -> {
                    for (long id : ids) db.deleteRecord(id);
                    U.toast(act, "已删除 " + ids.size() + " 笔");
                    exitSelection();
                    show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void bulkRecat() {
        List<Long> ids = recordAdapter.selectedIds();
        if (ids.isEmpty()) {
            U.toast(act, "还没有选择记录");
            return;
        }
        final List<Category> cats = new ArrayList<>();
        cats.addAll(db.topCategories(DbHelper.TYPE_EXPENSE));
        cats.addAll(db.topCategories(DbHelper.TYPE_INCOME));
        final String[] names = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) names[i] = cats.get(i).name;
        new AlertDialog.Builder(act)
                .setTitle("批量改分类")
                .setItems(names, (d, w) -> {
                    Category c = cats.get(w);
                    for (long id : ids) {
                        Record r = findRecord(id);
                        if (r != null) {
                            db.updateRecord(id, r.type, r.amountCents, c.id, c.name, 0, "", r.date, r.note);
                        }
                    }
                    U.toast(act, "已改分类为「" + c.name + "」");
                    exitSelection();
                    show();
                })
                .show();
    }

    private Record findRecord(long id) {
        for (Record r : recordData) {
            if (r.id == id) return r;
        }
        return null;
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
