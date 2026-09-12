package com.lifebook.ledger.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.model.Transfer;
import com.lifebook.ledger.util.U;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 账本间转账弹窗（也可编辑已有转账） */
public class TransferDialog {

    public static void show(Activity act, DbHelper db, Transfer edit, Runnable onDone) {
        final Dialog dlg = new Dialog(act);
        final android.view.View v = act.getLayoutInflater().inflate(R.layout.dialog_transfer, null);
        dlg.setContentView(v);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.55f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        final Spinner spFrom = v.findViewById(R.id.sp_from);
        final Spinner spTo = v.findViewById(R.id.sp_to);
        final EditText etAmount = v.findViewById(R.id.et_tr_amount);
        final TextView tvDate = v.findViewById(R.id.tv_tr_date);
        final EditText etNote = v.findViewById(R.id.et_tr_note);
        final TextView btnSave = v.findViewById(R.id.btn_tr_save);
        final LinearLayout rowDate = v.findViewById(R.id.row_date);

        final List<Book> books = db.books();
        List<String> names = new ArrayList<>();
        for (Book b : books) names.add(b.name);
        ArrayAdapter<String> ad = new ArrayAdapter<>(act, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFrom.setAdapter(ad);
        spTo.setAdapter(ad);

        final String[] selDate = {edit != null ? edit.date : U.today()};
        tvDate.setText(U.dateLabel(selDate[0]));

        if (edit != null) {
            ((TextView) v.findViewById(R.id.tv_tr_title)).setText("编辑转账");
            etAmount.setText(U.money(edit.amountCents));
            etNote.setText(edit.note == null ? "" : edit.note);
            int fi = indexOf(books, edit.fromBook);
            int ti = indexOf(books, edit.toBook);
            if (fi >= 0) spFrom.setSelection(fi);
            if (ti >= 0) spTo.setSelection(ti);
        }

        v.findViewById(R.id.btn_tr_cancel).setOnClickListener(x -> dlg.dismiss());
        btnSave.setOnClickListener(x -> {
            Book from = (Book) books.get(spFrom.getSelectedItemPosition());
            Book to = (Book) books.get(spTo.getSelectedItemPosition());
            if (from == null || to == null) return;
            if (from.key.equals(to.key)) {
                U.toast(act, "转出和转入账本不能相同");
                return;
            }
            final long cents;
            try {
                cents = U.parseMoney(etAmount.getText().toString());
            } catch (Exception e) {
                U.toast(act, e.getMessage());
                return;
            }
            String note = etNote.getText().toString().trim();
            if (edit != null) {
                db.purgeTransfer(edit.id);
                db.addTransfer(from.key, to.key, cents, selDate[0], note);
                U.toast(act, "已保存");
            } else {
                db.addTransfer(from.key, to.key, cents, selDate[0], note);
                U.toast(act, "已转账");
            }
            dlg.dismiss();
            if (onDone != null) onDone.run();
        });

        rowDate.setOnClickListener(x -> {
            LocalDate cur = LocalDate.parse(selDate[0]);
            new DatePickerDialog(act, (dp, y, m, day) -> {
                selDate[0] = LocalDate.of(y, m + 1, day).toString();
                tvDate.setText(U.dateLabel(selDate[0]));
            }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()).show();
        });

        dlg.show();
    }

    private static int indexOf(List<Book> books, String key) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).key.equals(key)) return i;
        }
        return -1;
    }
}
