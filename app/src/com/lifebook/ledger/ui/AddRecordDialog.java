package com.lifebook.ledger.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Category;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.util.U;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 记账 / 编辑记录的底部弹窗 */
public class AddRecordDialog {

    public static void show(final MainActivity act, final DbHelper db, final Record edit, final String defaultType, final String book, final Runnable onDone) {
        final Dialog dlg = new Dialog(act);
        final View v = act.getLayoutInflater().inflate(R.layout.dialog_add_record, null);
        dlg.setContentView(v);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.55f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        final boolean isEdit = edit != null;
        final String[] curType = {isEdit ? edit.type : (defaultType != null ? defaultType : DbHelper.TYPE_EXPENSE)};
        final long[] selCat1 = {isEdit ? edit.cat1Id : 0L};
        final long[] selCat2 = {isEdit ? edit.cat2Id : 0L};
        final String[] pseudoCat1Name = {isEdit && edit.cat1Name != null ? edit.cat1Name : ""};
        final String[] pseudoCat2Name = {isEdit && edit.cat2Name != null ? edit.cat2Name : ""};
        final String[] selDate = {isEdit ? edit.date : U.today()};

        final TextView tvTitle = v.findViewById(R.id.tv_dlg_title);
        final TextView segExp = v.findViewById(R.id.chip_dlg_expense);
        final TextView segInc = v.findViewById(R.id.chip_dlg_income);
        final EditText etAmount = v.findViewById(R.id.et_amount);
        final LinearLayout cat1Box = v.findViewById(R.id.cat1_container);
        final TextView labelCat2 = v.findViewById(R.id.label_cat2);
        final LinearLayout cat2Box = v.findViewById(R.id.cat2_container);
        final TextView tvDate = v.findViewById(R.id.tv_date);
        final EditText etNote = v.findViewById(R.id.et_note);
        final TextView btnDel = v.findViewById(R.id.btn_dlg_delete);
        final TextView btnCancel = v.findViewById(R.id.btn_dlg_cancel);
        final TextView btnSave = v.findViewById(R.id.btn_dlg_save);

        tvTitle.setText(isEdit ? "编辑记录" : "记一笔");
        etAmount.setText(isEdit ? U.money(edit.amountCents) : "");
        etNote.setText(isEdit && edit.note != null ? edit.note : "");
        tvDate.setText(U.dateLabel(selDate[0]));
        btnDel.setVisibility(isEdit ? View.VISIBLE : View.GONE);

        if (isEdit) {
            if (selCat1[0] > 0 && db.categoryById(selCat1[0]) == null) selCat1[0] = -1;
            if (selCat2[0] > 0 && db.categoryById(selCat2[0]) == null) selCat2[0] = -1;
        } else {
            selCat1[0] = -2;
        }

        final Runnable[] rRebuild = new Runnable[1];
        final Runnable[] rCat1 = new Runnable[1];
        final Runnable[] rCat2 = new Runnable[1];

        rCat1[0] = new Runnable() {
            @Override
            public void run() {
                cat1Box.removeAllViews();
                List<Category> cats = new ArrayList<>(db.topCategories(curType[0]));
                if (selCat1[0] == -1) {
                    Category p = new Category();
                    p.id = -1;
                    p.name = pseudoCat1Name[0];
                    cats.add(p);
                }
                if (cats.isEmpty() && selCat1[0] == -2) {
                    TextView hint = new TextView(act);
                    hint.setText("还没有分类，可先到「管理 → 分类管理」添加，或直接点保存");
                    hint.setTextSize(12);
                    hint.setTextColor(act.getResources().getColor(R.color.text_sub));
                    cat1Box.addView(hint);
                    return;
                }
                LinearLayout row = null;
                int count = 0;
                for (final Category c : cats) {
                    if (count % 2 == 0) {
                        row = new LinearLayout(act);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.topMargin = U.dp(act, 4);
                        cat1Box.addView(row, lp);
                    }
                    TextView chip = makeChip(act, c.name, c.id == selCat1[0], () -> {
                        selCat1[0] = c.id;
                        if (c.id == -1) pseudoCat1Name[0] = c.name;
                        if (c.id > 0) selCat2[0] = 0;
                        rCat1[0].run();
                        rCat2[0].run();
                    });
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    clp.rightMargin = count % 2 == 0 ? U.dp(act, 6) : 0;
                    row.addView(chip, clp);
                    count++;
                }
            }
        };

        rCat2[0] = new Runnable() {
            @Override
            public void run() {
                cat2Box.removeAllViews();
                List<Category> children = new ArrayList<>();
                if (selCat1[0] > 0) {
                    children = db.childCategories(curType[0], selCat1[0]);
                }
                if (selCat2[0] == -1) {
                    Category p = new Category();
                    p.id = -1;
                    p.name = pseudoCat2Name[0];
                    children.add(p);
                }
                if (selCat1[0] == -2 || selCat1[0] == 0) {
                    labelCat2.setVisibility(View.GONE);
                    cat2Box.setVisibility(View.GONE);
                    return;
                }
                labelCat2.setVisibility(View.VISIBLE);
                cat2Box.setVisibility(View.VISIBLE);

                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView none = makeChip(act, "不细分", selCat2[0] == 0, () -> {
                    selCat2[0] = 0;
                    rCat2[0].run();
                });
                row.addView(none, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                cat2Box.addView(row);

                int count = 0;
                LinearLayout row2 = null;
                for (final Category c : children) {
                    if (count % 2 == 0) {
                        row2 = new LinearLayout(act);
                        row2.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.topMargin = U.dp(act, 4);
                        cat2Box.addView(row2, lp);
                    }
                    TextView chip = makeChip(act, c.name, c.id == selCat2[0], () -> {
                        selCat2[0] = c.id;
                        if (c.id == -1) pseudoCat2Name[0] = c.name;
                        rCat2[0].run();
                    });
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    clp.rightMargin = count % 2 == 0 ? U.dp(act, 6) : 0;
                    row2.addView(chip, clp);
                    count++;
                }
            }
        };

        rRebuild[0] = new Runnable() {
            @Override
            public void run() {
                styleChip(act, segExp, DbHelper.TYPE_EXPENSE.equals(curType[0]));
                styleChip(act, segInc, DbHelper.TYPE_INCOME.equals(curType[0]));
                rCat1[0].run();
                rCat2[0].run();
            }
        };

        segExp.setOnClickListener(x -> {
            curType[0] = DbHelper.TYPE_EXPENSE;
            selCat1[0] = -2;
            selCat2[0] = 0;
            rRebuild[0].run();
        });
        segInc.setOnClickListener(x -> {
            curType[0] = DbHelper.TYPE_INCOME;
            selCat1[0] = -2;
            selCat2[0] = 0;
            rRebuild[0].run();
        });

        v.findViewById(R.id.row_date).setOnClickListener(x -> {
            LocalDate cur = LocalDate.parse(selDate[0]);
            new DatePickerDialog(act, (dp, year, m, day) -> {
                selDate[0] = LocalDate.of(year, m + 1, day).toString();
                tvDate.setText(U.dateLabel(selDate[0]));
            }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()).show();
        });

        v.findViewById(R.id.btn_close).setOnClickListener(x -> dlg.dismiss());
        btnCancel.setOnClickListener(x -> dlg.dismiss());

        btnSave.setOnClickListener(x -> {
            final long cents;
            try {
                cents = U.parseMoney(etAmount.getText().toString());
            } catch (Exception e) {
                U.toast(act, e.getMessage());
                return;
            }
            long c1id = 0;
            String c1name = "";
            if (selCat1[0] > 0) {
                Category c = db.categoryById(selCat1[0]);
                if (c != null) {
                    c1id = c.id;
                    c1name = c.name;
                }
            } else if (selCat1[0] == -1) {
                c1name = pseudoCat1Name[0];
            }
            long c2id = 0;
            String c2name = "";
            if (selCat2[0] > 0) {
                Category c = db.categoryById(selCat2[0]);
                if (c != null) {
                    c2id = c.id;
                    c2name = c.name;
                }
            } else if (selCat2[0] == -1) {
                c2name = pseudoCat2Name[0];
            }
            String note = etNote.getText().toString().trim();
            if (isEdit) {
                db.updateRecord(edit.id, curType[0], cents, c1id, c1name, c2id, c2name, selDate[0], note);
                U.toast(act, "已保存修改");
            } else {
                db.addRecord(curType[0], cents, c1id, c1name, c2id, c2name, selDate[0], note, book);
                U.toast(act, "已记一笔");
            }
            dlg.dismiss();
            if (onDone != null) onDone.run();
        });

        btnDel.setOnClickListener(x -> {
            new AlertDialog.Builder(act)
                    .setTitle("删除记录")
                    .setMessage("确定删除这笔记录吗？删除后无法恢复。")
                    .setPositiveButton("删除", (d, w2) -> {
                        db.deleteRecord(edit.id);
                        dlg.dismiss();
                        U.toast(act, "已删除");
                        if (onDone != null) onDone.run();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        rRebuild[0].run();
        dlg.show();
    }

    private static TextView makeChip(final MainActivity act, String name, boolean selected, Runnable onClick) {
        TextView chip = new TextView(act);
        chip.setText(name);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);
        chip.setPadding(U.dp(act, 6), U.dp(act, 9), U.dp(act, 6), U.dp(act, 9));
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
        chip.setTextColor(act.getResources().getColor(selected ? R.color.chip_sel_text : R.color.chip_text));
        chip.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setOnClickListener(x -> onClick.run());
        return chip;
    }

    public static void styleChip(MainActivity act, TextView chip, boolean selected) {
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
