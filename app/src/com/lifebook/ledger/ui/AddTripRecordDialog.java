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
import com.lifebook.ledger.model.Trip;
import com.lifebook.ledger.model.TripRecord;
import com.lifebook.ledger.util.U;

import java.time.LocalDate;

/** 出游期间记一笔 / 编辑 / 删除 */
public class AddTripRecordDialog {

    private static final String[] CATS = {"交通", "餐饮", "住宿", "门票", "购物", "其他"};

    public static void show(final MainActivity act, final DbHelper db, final Trip trip, final TripRecord edit, final Runnable onDone) {
        final Dialog dlg = new Dialog(act);
        final View v = act.getLayoutInflater().inflate(R.layout.dialog_add_trip_record, null);
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
        final String[] selCat = {isEdit && edit.category != null && !edit.category.isEmpty() ? edit.category : "其他"};
        final String[] selDate = {isEdit ? edit.date : U.today()};
        final TextView tvTitle = v.findViewById(R.id.tv_atr_title);
        final EditText etAmount = v.findViewById(R.id.et_atr_amount);
        final EditText etNote = v.findViewById(R.id.et_atr_note);
        final TextView tvDate = v.findViewById(R.id.tv_atr_date);
        final TextView btnDel = v.findViewById(R.id.btn_atr_delete);
        final LinearLayout catBox = v.findViewById(R.id.atr_cat_container);

        tvTitle.setText((isEdit ? "编辑 · " : "出游记一笔 · ") + trip.name);
        etAmount.setText(isEdit ? U.money(edit.amountCents) : "");
        etNote.setText(isEdit && edit.note != null ? edit.note : "");
        tvDate.setText(U.dateLabel(selDate[0]));
        btnDel.setVisibility(isEdit ? View.VISIBLE : View.GONE);

        final Runnable[] rBuildCats = new Runnable[1];
        rBuildCats[0] = new Runnable() {
            @Override
            public void run() {
                catBox.removeAllViews();
                LinearLayout row = null;
                int count = 0;
                for (final String cat : CATS) {
                    if (count % 2 == 0) {
                        row = new LinearLayout(act);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.topMargin = U.dp(act, 4);
                        catBox.addView(row, lp);
                    }
                    final boolean sel = cat.equals(selCat[0]);
                    TextView chip = new TextView(act);
                    chip.setText(cat);
                    chip.setTextSize(13);
                    chip.setGravity(Gravity.CENTER);
                    chip.setMaxLines(1);
                    chip.setPadding(U.dp(act, 6), U.dp(act, 9), U.dp(act, 6), U.dp(act, 9));
                    chip.setBackgroundResource(sel ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
                    chip.setTextColor(act.getResources().getColor(sel ? R.color.chip_sel_text : R.color.chip_text));
                    chip.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    chip.setOnClickListener(x -> {
                        selCat[0] = cat;
                        rBuildCats[0].run();
                    });
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    clp.rightMargin = count % 2 == 0 ? U.dp(act, 6) : 0;
                    row.addView(chip, clp);
                    count++;
                }
            }
        };
        rBuildCats[0].run();

        v.findViewById(R.id.row_atr_date).setOnClickListener(x -> {
            LocalDate cur = LocalDate.parse(selDate[0]);
            new DatePickerDialog(act, (dp, y, m, d) -> {
                selDate[0] = LocalDate.of(y, m + 1, d).toString();
                tvDate.setText(U.dateLabel(selDate[0]));
            }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()).show();
        });

        v.findViewById(R.id.btn_atr_close).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_atr_cancel).setOnClickListener(x -> dlg.dismiss());

        v.findViewById(R.id.btn_atr_save).setOnClickListener(x -> {
            final long cents;
            try {
                cents = U.parseMoney(etAmount.getText().toString());
            } catch (Exception e) {
                U.toast(act, e.getMessage());
                return;
            }
            String note = etNote.getText().toString().trim();
            if (isEdit) {
                db.updateTripRecord(edit.id, cents, selCat[0], note, selDate[0]);
                U.toast(act, "已保存修改");
            } else {
                db.addTripRecord(trip.id, cents, selCat[0], note, selDate[0]);
                U.toast(act, "已记一笔");
            }
            dlg.dismiss();
            if (onDone != null) onDone.run();
        });

        btnDel.setOnClickListener(x -> {
            new AlertDialog.Builder(act)
                    .setTitle("删除这条记录")
                    .setMessage("确定删除这条出游明细吗？删除后无法恢复。")
                    .setPositiveButton("删除", (d, w2) -> {
                        db.deleteTripRecord(edit.id);
                        dlg.dismiss();
                        U.toast(act, "已删除");
                        if (onDone != null) onDone.run();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        dlg.show();
    }
}
