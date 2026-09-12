package com.lifebook.ledger.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Category;
import com.lifebook.ledger.util.U;

import java.util.List;

/** 收入和支出两级分类管理 */
public class CategoryManageActivity extends Activity {

    private DbHelper db;
    private String type = DbHelper.TYPE_EXPENSE;
    private LinearLayout container;
    private TextView segExp;
    private TextView segInc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_manage);
        db = new DbHelper(this);
        db.seedIfEmpty();
        container = findViewById(R.id.cat_container);
        segExp = findViewById(R.id.chip_cat_expense);
        segInc = findViewById(R.id.chip_cat_income);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_parent).setOnClickListener(v -> showNameInput("新建大分类", "", name -> {
            db.addCategory(type, 0, name);
            U.toast(this, "已添加");
            build();
        }));
        segExp.setOnClickListener(v -> {
            type = DbHelper.TYPE_EXPENSE;
            build();
        });
        segInc.setOnClickListener(v -> {
            type = DbHelper.TYPE_INCOME;
            build();
        });
        build();
    }

    private void build() {
        styleChip(segExp, DbHelper.TYPE_EXPENSE.equals(type));
        styleChip(segInc, DbHelper.TYPE_INCOME.equals(type));
        container.removeAllViews();
        List<Category> parents = db.topCategories(type);
        if (parents.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("还没有大分类\n点右上角「＋ 大分类」添加一个吧");
            hint.setTextSize(14);
            hint.setTextColor(getResources().getColor(R.color.text_sub));
            hint.setGravity(Gravity.CENTER);
            hint.setLineSpacing(U.dp(this, 4), 1f);
            hint.setPadding(0, U.dp(this, 60), 0, 0);
            container.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (final Category p : parents) {
            View row = getLayoutInflater().inflate(R.layout.row_cat_parent, container, false);
            TextView emoji = row.findViewById(R.id.tv_p_emoji);
            TextView name = row.findViewById(R.id.tv_p_name);
            TextView count = row.findViewById(R.id.tv_p_count);
            LinearLayout childrenBox = row.findViewById(R.id.p_children);

            emoji.setText(U.emojiFor(p.name, DbHelper.TYPE_INCOME.equals(type)));
            name.setText(p.name);
            List<Category> children = db.childCategories(type, p.id);
            count.setText(children.isEmpty() ? "" : children.size() + " 个小类");

            row.findViewById(R.id.btn_p_rename).setOnClickListener(v -> showNameInput("重命名大分类", p.name, newName -> {
                db.renameCategory(p.id, newName);
                U.toast(this, "已重命名");
                build();
            }));
            row.findViewById(R.id.btn_p_delete).setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("删除大分类")
                        .setMessage("确定删除「" + p.name + "」吗？\n其下的小分类会一起删除；已有记账记录不受影响（保留原分类名称）。")
                        .setPositiveButton("删除", (d, w) -> {
                            db.deleteCategory(p.id);
                            U.toast(this, "已删除");
                            build();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            childrenBox.removeAllViews();
            LinearLayout row2 = null;
            int count2 = 0;
            for (final Category c : children) {
                if (count2 % 2 == 0) {
                    row2 = new LinearLayout(this);
                    row2.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = U.dp(this, 4);
                    childrenBox.addView(row2, lp);
                }
                TextView chip = makeChip(c.name, () -> {
                    new AlertDialog.Builder(CategoryManageActivity.this)
                            .setTitle("「" + c.name + "」")
                            .setItems(new String[]{"重命名", "删除"}, (d, which) -> {
                                if (which == 0) {
                                    showNameInput("重命名小分类", c.name, newName -> {
                                        db.renameCategory(c.id, newName);
                                        U.toast(CategoryManageActivity.this, "已重命名");
                                        build();
                                    });
                                } else {
                                    new AlertDialog.Builder(CategoryManageActivity.this)
                                            .setTitle("删除小分类")
                                            .setMessage("确定删除「" + c.name + "」吗？已有记录不受影响。")
                                            .setPositiveButton("删除", (d2, w2) -> {
                                                db.deleteCategory(c.id);
                                                U.toast(CategoryManageActivity.this, "已删除");
                                                build();
                                            })
                                            .setNegativeButton("取消", null)
                                            .show();
                                }
                            })
                            .show();
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                clp.rightMargin = count2 % 2 == 0 ? U.dp(this, 6) : 0;
                row2.addView(chip, clp);
                count2++;
            }
            if (count2 % 2 == 0) {
                row2 = new LinearLayout(this);
                row2.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = U.dp(this, 4);
                childrenBox.addView(row2, lp);
            }
            TextView addChild = new TextView(this);
            addChild.setText("＋ 小分类");
            addChild.setTextSize(13);
            addChild.setGravity(Gravity.CENTER);
            addChild.setPadding(U.dp(this, 6), U.dp(this, 9), U.dp(this, 6), U.dp(this, 9));
            addChild.setBackgroundResource(R.drawable.bg_chip);
            addChild.setTextColor(getResources().getColor(R.color.primary));
            addChild.setOnClickListener(v -> showNameInput("添加小分类（属于「" + p.name + "」）", "", name2 -> {
                db.addCategory(type, p.id, name2);
                U.toast(this, "已添加");
                build();
            }));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            clp.rightMargin = count2 % 2 == 0 ? U.dp(this, 6) : 0;
            row2.addView(addChild, clp);

            container.addView(row);
        }
    }

    private TextView makeChip(String name, Runnable onClick) {
        TextView chip = new TextView(this);
        chip.setText(name);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);
        chip.setPadding(U.dp(this, 6), U.dp(this, 9), U.dp(this, 6), U.dp(this, 9));
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setTextColor(getResources().getColor(R.color.chip_text));
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        if (selected) {
            chip.setBackgroundResource(R.drawable.bg_seg_item);
            chip.setTextColor(getResources().getColor(R.color.primary));
            chip.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            chip.setBackgroundColor(Color.TRANSPARENT);
            chip.setTextColor(getResources().getColor(R.color.chip_text));
            chip.setTypeface(Typeface.DEFAULT);
        }
    }

    private interface NameCallback {
        void onName(String name);
    }

    private void showNameInput(String title, String prefill, NameCallback cb) {
        FrameLayout frame = new FrameLayout(this);
        final EditText input = new EditText(this);
        input.setHint("输入名称");
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
