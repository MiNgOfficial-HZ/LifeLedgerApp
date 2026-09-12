package com.lifebook.ledger.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.model.Transfer;
import com.lifebook.ledger.util.U;

import java.util.List;

/** 回收站：恢复或彻底删除软删除的记录/转账 */
public class RecycleActivity extends Activity {

    private DbHelper db;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycle);
        db = new DbHelper(this);
        db.seedIfEmpty();
        container = findViewById(R.id.trash_container);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_trash_clear).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("清空回收站")
                    .setMessage("将永久删除回收站内全部内容，且无法恢复。确定吗？")
                    .setPositiveButton("清空", (d, w) -> {
                        db.emptyTrash();
                        U.toast(this, "已清空");
                        build();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        build();
    }

    private void build() {
        container.removeAllViews();
        final List<Record> records = db.trashRecords();
        final List<Transfer> transfers = db.trashTransfers();
        int total = records.size() + transfers.size();
        if (total == 0) {
            TextView hint = new TextView(this);
            hint.setText("回收站是空的");
            hint.setTextSize(14);
            hint.setTextColor(getResources().getColor(R.color.text_sub));
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, U.dp(this, 60), 0, 0);
            container.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (final Record r : records) {
            container.addView(makeRecordRow(r));
        }
        for (final Transfer t : transfers) {
            container.addView(makeTransferRow(t));
        }
    }

    private View makeRecordRow(final Record r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        row.setElevation(U.dp(this, 1));

        TextView title = new TextView(this);
        title.setText(U.dateLabel(r.date) + " · " + r.title());
        title.setTextSize(14);
        title.setTextColor(getResources().getColor(R.color.text_main));
        title.setPadding(U.dp(this, 0), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView am = new TextView(this);
        am.setText((DbHelper.TYPE_INCOME.equals(r.type) ? "+" : "-") + U.money(r.amountCents));
        am.setTextSize(14);
        am.setTextColor(getResources().getColor(DbHelper.TYPE_INCOME.equals(r.type) ? R.color.income : R.color.expense));
        row.addView(am);

        TextView restore = new TextView(this);
        restore.setText("恢复");
        restore.setTextSize(13);
        restore.setTextColor(getResources().getColor(R.color.primary));
        restore.setPadding(U.dp(this, 10), U.dp(this, 8), U.dp(this, 4), U.dp(this, 8));
        restore.setOnClickListener(v -> {
            db.restoreRecord(r.id);
            U.toast(this, "已恢复");
            build();
        });
        row.addView(restore);

        TextView purge = new TextView(this);
        purge.setText("删除");
        purge.setTextSize(13);
        purge.setTextColor(getResources().getColor(R.color.expense));
        purge.setPadding(U.dp(this, 4), U.dp(this, 8), U.dp(this, 0), U.dp(this, 8));
        purge.setOnClickListener(v -> {
            db.purgeRecord(r.id);
            U.toast(this, "已删除");
            build();
        });
        row.addView(purge);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, U.dp(this, 8));
        row.setLayoutParams(lp);
        return row;
    }

    private View makeTransferRow(final Transfer t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(U.dp(this, 14), U.dp(this, 12), U.dp(this, 14), U.dp(this, 12));
        row.setElevation(U.dp(this, 1));

        TextView title = new TextView(this);
        title.setText(U.dateLabel(t.date) + " · " + db.bookLabel(t.fromBook) + " → " + db.bookLabel(t.toBook));
        title.setTextSize(14);
        title.setTextColor(getResources().getColor(R.color.text_main));
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView am = new TextView(this);
        am.setText(U.money(t.amountCents));
        am.setTextSize(14);
        am.setTextColor(getResources().getColor(R.color.text_main));
        row.addView(am);

        TextView restore = new TextView(this);
        restore.setText("恢复");
        restore.setTextSize(13);
        restore.setTextColor(getResources().getColor(R.color.primary));
        restore.setPadding(U.dp(this, 10), U.dp(this, 8), U.dp(this, 4), U.dp(this, 8));
        restore.setOnClickListener(v -> {
            db.restoreTransfer(t.id);
            U.toast(this, "已恢复");
            build();
        });
        row.addView(restore);

        TextView purge = new TextView(this);
        purge.setText("删除");
        purge.setTextSize(13);
        purge.setTextColor(getResources().getColor(R.color.expense));
        purge.setPadding(U.dp(this, 4), U.dp(this, 8), U.dp(this, 0), U.dp(this, 8));
        purge.setOnClickListener(v -> {
            db.purgeTransfer(t.id);
            U.toast(this, "已删除");
            build();
        });
        row.addView(purge);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, U.dp(this, 8));
        row.setLayoutParams(lp);
        return row;
    }
}
