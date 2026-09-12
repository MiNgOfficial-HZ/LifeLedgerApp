package com.lifebook.ledger.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Trip;
import com.lifebook.ledger.util.U;

import java.time.LocalDate;

/** 新建 / 编辑一次出游 */
public class AddTripDialog {

    public static void show(final MainActivity act, final DbHelper db, final Trip exist, final Runnable onDone) {
        View v = act.getLayoutInflater().inflate(R.layout.dialog_add_trip, null);
        final String[] selDate = {exist != null ? exist.startDate : U.today()};
        final TextView tvTitle = v.findViewById(R.id.tv_at_title);
        final EditText etName = v.findViewById(R.id.et_trip_name);
        final TextView tvDate = v.findViewById(R.id.tv_trip_start);
        final TextView btnSave = v.findViewById(R.id.btn_at_save);

        tvTitle.setText(exist == null ? "新建出游" : "编辑出游");
        etName.setText(exist != null ? exist.name : "");
        tvDate.setText(U.dateLabel(selDate[0]));
        btnSave.setText(exist == null ? "创建" : "保存");

        final AlertDialog dlg = new AlertDialog.Builder(act).setView(v).create();
        v.findViewById(R.id.btn_at_cancel).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.row_trip_start).setOnClickListener(x -> {
            LocalDate cur = LocalDate.parse(selDate[0]);
            new DatePickerDialog(act, (dp, y, m, d) -> {
                selDate[0] = LocalDate.of(y, m + 1, d).toString();
                tvDate.setText(U.dateLabel(selDate[0]));
            }, cur.getYear(), cur.getMonthValue() - 1, cur.getDayOfMonth()).show();
        });
        btnSave.setOnClickListener(x -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                U.toast(act, "请先输入出游名称");
                return;
            }
            if (exist == null) {
                db.addTrip(name, selDate[0]);
                U.toast(act, "出游已创建");
            } else {
                db.updateTrip(exist.id, name, selDate[0]);
                U.toast(act, "已保存");
            }
            dlg.dismiss();
            if (onDone != null) onDone.run();
        });
        dlg.show();
    }
}
