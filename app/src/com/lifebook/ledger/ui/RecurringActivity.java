package com.lifebook.ledger.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Recurring;
import com.lifebook.ledger.util.U;

import java.util.List;

/** 周期记账管理 */
public class RecurringActivity extends Activity {

    private DbHelper db;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recurring);
        db = new DbHelper(this);
        db.seedIfEmpty();
        container = findViewById(R.id.recurring_container);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_recurring).setOnClickListener(v ->
                RecurringDialog.show(this, db, null, this::build));
        findViewById(R.id.btn_run_this_month).setOnClickListener(v -> {
            int n = db.runRecurring(U.currentMonth(), true);
            U.toast(this, n > 0 ? "已生成 " + n + " 笔" : "本月没有到期的周期记账");
            build();
        });
        build();
    }

    private void build() {
        container.removeAllViews();
        List<Recurring> list = db.recurrings();
        if (list.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("还没有周期记账\n点右上角「＋ 周期」添加一个吧");
            hint.setTextSize(14);
            hint.setTextColor(getResources().getColor(R.color.text_sub));
            hint.setGravity(android.view.Gravity.CENTER);
            hint.setLineSpacing(U.dp(this, 4), 1f);
            hint.setPadding(0, U.dp(this, 60), 0, 0);
            container.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (final Recurring r : list) {
            View row = getLayoutInflater().inflate(R.layout.row_recurring, container, false);
            TextView emoji = row.findViewById(R.id.tv_rr_emoji);
            TextView title = row.findViewById(R.id.tv_rr_title);
            TextView sub = row.findViewById(R.id.tv_rr_sub);
            TextView amount = row.findViewById(R.id.tv_rr_amount);
            emoji.setText(DbHelper.TYPE_INCOME.equals(r.type) ? "💰" : "🧾");
            title.setText(r.title() + (r.enabled ? "" : "（已停用）"));
            sub.setText("每月 " + r.dayOfMonth + " 日 · " + db.bookLabel(r.book)
                    + " · " + (DbHelper.TYPE_INCOME.equals(r.type) ? "收入" : "支出"));
            amount.setText((DbHelper.TYPE_INCOME.equals(r.type) ? "+" : "-") + U.money(r.amountCents));
            row.findViewById(R.id.btn_rr_edit).setOnClickListener(v ->
                    RecurringDialog.show(this, db, r, this::build));
            row.findViewById(R.id.btn_rr_delete).setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("删除周期记账")
                            .setMessage("确定删除「" + r.title() + "」吗？")
                            .setPositiveButton("删除", (d, w) -> {
                                db.deleteRecurring(r.id);
                                U.toast(this, "已删除");
                                build();
                            })
                            .setNegativeButton("取消", null)
                            .show());
            row.setOnClickListener(v -> {
                db.updateRecurring(r.id, r.type, r.amountCents, r.cat1Id, r.cat1Name, r.cat2Id, r.cat2Name,
                        r.note, r.book, r.dayOfMonth, !r.enabled);
                build();
            });
            container.addView(row);
        }
    }
}
