package com.lifebook.ledger.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.util.U;

import java.util.List;

/** 账本管理：展示全部账本，支持新建 / 重命名 / 删除自定义账本 */
public class BookManageActivity extends Activity {

    private DbHelper db;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_manage);
        db = new DbHelper(this);
        db.seedIfEmpty();
        container = findViewById(R.id.book_container);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_book).setOnClickListener(v -> showNameInput("新建账本", "", name -> {
            db.addBook(name);
            U.toast(this, "已创建");
            build();
        }));
        build();
    }

    private void build() {
        container.removeAllViews();
        List<Book> books = db.books();
        for (final Book b : books) {
            View row = getLayoutInflater().inflate(R.layout.row_book, container, false);
            TextView emoji = row.findViewById(R.id.tv_b_emoji);
            TextView name = row.findViewById(R.id.tv_b_name);
            TextView count = row.findViewById(R.id.tv_b_count);
            TextView balance = row.findViewById(R.id.tv_b_balance);
            TextView builtin = row.findViewById(R.id.tv_b_builtin);
            ImageView rename = row.findViewById(R.id.btn_b_rename);
            ImageView delete = row.findViewById(R.id.btn_b_delete);

            emoji.setText("📒");
            name.setText(b.name);
            int n = db.recordCount(null, null, b.key);
            count.setText(n > 0 ? "共 " + n + " 条记录" : "暂无记录");
            long bal = db.bookBalance(b.key);
            balance.setText("余额 ¥" + (bal < 0 ? "-" : "") + U.money(Math.abs(bal)));
            balance.setTextColor(getResources().getColor(bal < 0 ? R.color.expense : R.color.income));

            if (b.builtin) {
                builtin.setVisibility(View.VISIBLE);
                delete.setVisibility(View.GONE);
            } else {
                builtin.setVisibility(View.GONE);
                delete.setVisibility(View.VISIBLE);
            }

            rename.setOnClickListener(v ->
                    showNameInput("重命名账本", b.name, newName -> {
                        db.renameBook(b.key, newName);
                        U.toast(this, "已重命名");
                        build();
                    }));

            delete.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("删除账本")
                            .setMessage("确定删除「" + b.name + "」吗？其下 " + n + " 条记录将一并删除，且无法恢复。")
                            .setPositiveButton("删除", (d, w) -> {
                                db.deleteBook(b.key);
                                U.toast(this, "已删除");
                                build();
                            })
                            .setNegativeButton("取消", null)
                            .show());
        }
    }

    private interface NameCallback {
        void onName(String name);
    }

    private void showNameInput(String title, String prefill, NameCallback cb) {
        FrameLayout frame = new FrameLayout(this);
        final EditText input = new EditText(this);
        input.setHint("输入账本名称");
        input.setTextColor(getResources().getColor(R.color.text_main));
        input.setHintTextColor(getResources().getColor(R.color.text_sub));
        input.setText(prefill == null ? "" : prefill);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.bg_input);
        frame.setPadding(U.dp(this, 20), U.dp(this, 8), U.dp(this, 20), 0);
        frame.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(frame)
                .setPositiveButton("确定", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty()) {
                        U.toast(this, "名称不能为空");
                        return;
                    }
                    cb.onName(n);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
