package com.lifebook.ledger.ui;

import android.app.Dialog;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.model.Category;
import com.lifebook.ledger.model.Recurring;
import com.lifebook.ledger.util.U;

import java.util.ArrayList;
import java.util.List;

/** 周期记账：每月固定自动生成一笔 */
public class RecurringDialog {

    public static void show(Activity act, DbHelper db, Recurring edit, Runnable onDone) {
        final Dialog dlg = new Dialog(act);
        final View v = act.getLayoutInflater().inflate(R.layout.dialog_recurring, null);
        dlg.setContentView(v);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.55f);
        }

        final TextView segExp = v.findViewById(R.id.rc_exp);
        final TextView segInc = v.findViewById(R.id.rc_inc);
        final EditText etAmount = v.findViewById(R.id.et_rc_amount);
        final EditText etDay = v.findViewById(R.id.et_rc_day);
        final EditText etNote = v.findViewById(R.id.et_rc_note);
        final LinearLayout catBox = v.findViewById(R.id.rc_cat_container);
        final Spinner spBook = v.findViewById(R.id.sp_rc_book);
        final TextView btnSave = v.findViewById(R.id.btn_rc_save);

        final String[] curType = {edit != null ? edit.type : DbHelper.TYPE_EXPENSE};
        final long[] selCat1 = {edit != null ? edit.cat1Id : 0};
        final String[] selCat1Name = {edit != null ? edit.cat1Name : ""};

        if (edit != null) {
            ((TextView) v.findViewById(R.id.tv_rc_title)).setText("编辑周期记账");
            etAmount.setText(U.money(edit.amountCents));
            etDay.setText(String.valueOf(edit.dayOfMonth));
            etNote.setText(edit.note == null ? "" : edit.note);
        }

        List<Book> books = db.books();
        List<String> names = new ArrayList<>();
        for (Book b : books) names.add(b.name);
        ArrayAdapter<String> ad = new ArrayAdapter<>(act, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBook.setAdapter(ad);
        if (edit != null) {
            int idx = -1;
            for (int i = 0; i < books.size(); i++) if (books.get(i).key.equals(edit.book)) idx = i;
            if (idx >= 0) spBook.setSelection(idx);
        }

        final Runnable[] rebuildCats = new Runnable[1];
        rebuildCats[0] = () -> {
            catBox.removeAllViews();
            List<Category> cats = db.topCategories(curType[0]);
            LinearLayout row = null;
            int count = 0;
            for (final Category c : cats) {
                if (count % 4 == 0) {
                    row = new LinearLayout(act);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = U.dp(act, 4);
                    catBox.addView(row, lp);
                }
                TextView chip = new TextView(act);
                chip.setText(c.name);
                chip.setTextSize(12);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(U.dp(act, 6), U.dp(act, 8), U.dp(act, 6), U.dp(act, 8));
                boolean sel = c.id == selCat1[0];
                chip.setBackgroundResource(sel ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
                chip.setTextColor(act.getResources().getColor(sel ? R.color.chip_sel_text : R.color.chip_text));
                chip.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                chip.setOnClickListener(x -> {
                    selCat1[0] = c.id;
                    selCat1Name[0] = c.name;
                    rebuildCats[0].run();
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                clp.rightMargin = count % 4 == 0 ? U.dp(act, 6) : 0;
                row.addView(chip, clp);
                count++;
            }
        };

        final Runnable styleType = () -> {
            styleChip(act, segExp, DbHelper.TYPE_EXPENSE.equals(curType[0]));
            styleChip(act, segInc, DbHelper.TYPE_INCOME.equals(curType[0]));
        };
        segExp.setOnClickListener(x -> {
            curType[0] = DbHelper.TYPE_EXPENSE;
            selCat1[0] = 0;
            selCat1Name[0] = "";
            styleType.run();
            rebuildCats[0].run();
        });
        segInc.setOnClickListener(x -> {
            curType[0] = DbHelper.TYPE_INCOME;
            selCat1[0] = 0;
            selCat1Name[0] = "";
            styleType.run();
            rebuildCats[0].run();
        });

        v.findViewById(R.id.btn_rc_cancel).setOnClickListener(x -> dlg.dismiss());
        btnSave.setOnClickListener(x -> {
            final long cents;
            try {
                cents = U.parseMoney(etAmount.getText().toString());
            } catch (Exception e) {
                U.toast(act, e.getMessage());
                return;
            }
            int day;
            try {
                day = Integer.parseInt(etDay.getText().toString().trim());
            } catch (Exception e) {
                U.toast(act, "请输入每个月几号");
                return;
            }
            if (day < 1 || day > 31) {
                U.toast(act, "日期应在 1-31 之间");
                return;
            }
            Book book = (Book) books.get(spBook.getSelectedItemPosition());
            String note = etNote.getText().toString().trim();
            if (edit != null) {
                db.updateRecurring(edit.id, curType[0], cents, selCat1[0], selCat1Name[0], 0, "", note,
                        book.key, day, edit.enabled);
                U.toast(act, "已保存");
            } else {
                db.addRecurring(curType[0], cents, selCat1[0], selCat1Name[0], 0, "", note, book.key, day);
                U.toast(act, "已添加周期记账");
            }
            dlg.dismiss();
            if (onDone != null) onDone.run();
        });

        styleType.run();
        rebuildCats[0].run();
        dlg.show();
    }

    private static void styleChip(Activity act, TextView chip, boolean selected) {
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
