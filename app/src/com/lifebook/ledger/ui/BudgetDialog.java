package com.lifebook.ledger.ui;

import android.app.Dialog;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Budget;
import com.lifebook.ledger.model.Category;
import com.lifebook.ledger.util.U;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** 某账本的支出分类预算编辑 */
public class BudgetDialog {

    public static void show(Activity act, DbHelper db, String book, Runnable onDone) {
        final Dialog dlg = new Dialog(act);
        final View v = act.getLayoutInflater().inflate(R.layout.dialog_budget, null);
        dlg.setContentView(v);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.55f);
        }

        LinearLayout box = v.findViewById(R.id.budget_container);
        HashMap<Long, EditText> inputs = new HashMap<>();
        HashMap<Long, String> names = new HashMap<>();
        HashMap<Long, Long> existing = new HashMap<>();
        for (Budget b : db.budgets(book, DbHelper.TYPE_EXPENSE)) {
            existing.put(b.cat1Id, b.amountCents);
        }
        List<Category> cats = db.topCategories(DbHelper.TYPE_EXPENSE);
        for (Category c : cats) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(act);
            name.setText(c.name);
            name.setTextSize(14);
            name.setTextColor(act.getResources().getColor(R.color.text_main));
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            EditText et = new EditText(act);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setTextColor(act.getResources().getColor(R.color.text_main));
            et.setHintTextColor(act.getResources().getColor(R.color.icon_gray));
            et.setTextSize(14);
            et.setSingleLine(true);
            et.setBackgroundResource(R.drawable.bg_input);
            et.setPadding(U.dp(act, 10), U.dp(act, 6), U.dp(act, 10), U.dp(act, 6));
            Long ex = existing.get(c.id);
            if (ex != null && ex > 0) et.setText(U.money(ex));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(act, 110), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = U.dp(act, 8);
            row.addView(et, lp);
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inputs.put(c.id, et);
            names.put(c.id, c.name);
        }
        if (cats.isEmpty()) {
            TextView hint = new TextView(act);
            hint.setText("还没有支出分类，先到「分类管理」添加");
            hint.setTextSize(13);
            hint.setTextColor(act.getResources().getColor(R.color.text_sub));
            hint.setPadding(0, U.dp(act, 8), 0, U.dp(act, 8));
            box.addView(hint);
        }

        v.findViewById(R.id.btn_budget_cancel).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_budget_save).setOnClickListener(x -> {
            for (java.util.Map.Entry<Long, EditText> e : inputs.entrySet()) {
                String s = e.getValue().getText().toString().trim();
                long cents = 0;
                if (!s.isEmpty()) {
                    try {
                        cents = U.parseMoney(s);
                    } catch (Exception ex) {
                        continue;
                    }
                }
                db.setBudget(book, DbHelper.TYPE_EXPENSE, e.getKey(), names.get(e.getKey()), cents);
            }
            U.toast(act, "预算已保存");
            dlg.dismiss();
            if (onDone != null) onDone.run();
        });

        dlg.show();
    }
}
