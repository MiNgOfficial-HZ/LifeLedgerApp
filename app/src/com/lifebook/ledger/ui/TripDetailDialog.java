package com.lifebook.ledger.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Book;
import com.lifebook.ledger.model.Trip;
import com.lifebook.ledger.model.TripRecord;
import com.lifebook.ledger.util.U;

import java.util.List;

/** 单次出游详情：明细列表 + 记一笔 + 结束（可选合并主账本/小金库） */
public class TripDetailDialog {

    private final MainActivity act;
    private final DbHelper db;
    private final long tripId;
    private final Runnable onChanged;
    private final Dialog dlg;

    private Trip trip;
    private TextView tvName;
    private TextView tvStatus;
    private TextView tvRange;
    private TextView tvTotal;
    private TextView tvTotalLabel;
    private TextView btnAdd;
    private TextView btnFinish;
    private TextView btnAddMain;
    private TextView btnRmMain;
    private TextView btnDelete;
    private LinearLayout recordsBox;

    public static void show(MainActivity act, DbHelper db, long tripId, Runnable onChanged) {
        new TripDetailDialog(act, db, tripId, onChanged).open();
    }

    private TripDetailDialog(MainActivity act, DbHelper db, long tripId, Runnable onChanged) {
        this.act = act;
        this.db = db;
        this.tripId = tripId;
        this.onChanged = onChanged;

        dlg = new Dialog(act);
        View v = act.getLayoutInflater().inflate(R.layout.dialog_trip_detail, null);
        dlg.setContentView(v);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.55f);
        }

        tvName = v.findViewById(R.id.tv_td_name);
        tvStatus = v.findViewById(R.id.tv_td_status);
        tvRange = v.findViewById(R.id.tv_td_range);
        tvTotal = v.findViewById(R.id.tv_td_total);
        tvTotalLabel = v.findViewById(R.id.tv_td_total_label);
        btnAdd = v.findViewById(R.id.btn_td_add);
        btnFinish = v.findViewById(R.id.btn_td_finish);
        btnAddMain = v.findViewById(R.id.btn_td_addmain);
        btnRmMain = v.findViewById(R.id.btn_td_rmmain);
        btnDelete = v.findViewById(R.id.btn_td_delete);
        recordsBox = v.findViewById(R.id.trip_records_container);

        v.findViewById(R.id.btn_td_close).setOnClickListener(x -> dlg.dismiss());

        btnAdd.setOnClickListener(x -> {
            if (!trip.isOngoing()) return;
            AddTripRecordDialog.show(act, db, trip, null, () -> {
                refresh();
                onChanged.run();
            });
        });

        // 结束出游：三个明确按钮（合并主账本 / 合并小金库 / 点错了）
        btnFinish.setOnClickListener(x -> showMergeDialog(true));

        // 已结束但还没合并：随时可选合并到哪个账本
        btnAddMain.setOnClickListener(x -> showMergeDialog(false));

        btnRmMain.setOnClickListener(x -> {
            new AlertDialog.Builder(act)
                    .setTitle("移出账本")
                    .setMessage("将删除账本中「出游支出 · " + trip.name + "」那条记录；出游明细保留不变。")
                    .setPositiveButton("移出", (d, b) -> {
                        db.removeTripFromMain(tripId);
                        U.toast(act, "已移出账本");
                        refresh();
                        onChanged.run();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnDelete.setOnClickListener(x -> {
            String msg = "确定删除「" + trip.name + "」吗？全部出游明细将一并删除。\n"
                    + (trip.addedToMain ? "账本中已同步的那条支出会保留。" : "此操作不可恢复。");
            new AlertDialog.Builder(act)
                    .setTitle("删除出游")
                    .setMessage(msg)
                    .setPositiveButton("删除", (d, b) -> {
                        db.deleteTrip(tripId);
                        dlg.dismiss();
                        U.toast(act, "已删除");
                        onChanged.run();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        refresh();
    }

    /** finish=true 时是「结束出游」流程，否则是「补记入账本」流程；目标账本动态列出所有账本 */
    private void showMergeDialog(boolean finish) {
        long total = db.tripTotal(tripId);
        int n = db.tripCountRecords(tripId);
        String title = finish ? "结束这次出游" : "合并进账本";
        String msg = finish
                ? "结束之后不能再记新账。\n本次出游合计 ¥" + U.money(total) + "（" + n + " 笔）。\n要把这次出游合并到哪里？"
                : "本次出游合计 ¥" + U.money(total) + "（" + n + " 笔）。\n要把这次出游合并到哪里？";

        List<Book> books = db.books();
        final String[] items;
        if (finish) {
            items = new String[books.size() + 1];
            for (int i = 0; i < books.size(); i++) {
                items[i] = "结束并合并进「" + books.get(i).name + "」";
            }
            items[books.size()] = "仅结束，不合并";
        } else {
            items = new String[books.size()];
            for (int i = 0; i < books.size(); i++) {
                items[i] = "合并进「" + books.get(i).name + "」";
            }
        }
        new AlertDialog.Builder(act)
                .setTitle(title)
                .setMessage(msg)
                .setItems(items, (d, which) -> {
                    if (finish && which == books.size()) {
                        db.finishTrip(tripId, null);
                        U.toast(act, "已结束，未合并");
                    } else {
                        Book b = books.get(which);
                        db.finishTrip(tripId, b.key);
                        U.toast(act, (finish ? "已结束并合并进「" + b.name + "」" : "已合并进「" + b.name + "」"));
                    }
                    refresh();
                    onChanged.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void open() {
        dlg.show();
    }

    private void refresh() {
        trip = db.tripById(tripId);
        if (trip == null) {
            dlg.dismiss();
            return;
        }
        tvName.setText(trip.name);
        int n = db.tripCountRecords(tripId);
        long total = db.tripTotal(tripId);
        if (trip.isOngoing()) {
            tvStatus.setText("进行中");
            tvStatus.setBackgroundResource(R.drawable.bg_chip_sel);
            tvStatus.setTextColor(act.getResources().getColor(R.color.chip_sel_text));
            tvRange.setText(U.dateLabel(trip.startDate) + " 开始 · 至今");
            tvTotalLabel.setText("本次出游合计 · " + n + " 笔");
        } else {
            tvStatus.setText("已结束");
            tvStatus.setBackgroundResource(R.drawable.bg_chip);
            tvStatus.setTextColor(act.getResources().getColor(R.color.text_sub));
            String end = trip.endDate == null || trip.endDate.isEmpty() ? trip.startDate : trip.endDate;
            tvRange.setText(U.dateLabel(trip.startDate) + " ~ " + U.dateLabel(end));
            tvTotalLabel.setText("这次出游共支出 · " + n + " 笔" + (trip.addedToMain ? " · 已合并入" + db.bookLabel(trip.addedBook) : ""));
        }
        tvTotal.setText("¥" + U.money(total));

        btnAdd.setVisibility(trip.isOngoing() ? View.VISIBLE : View.GONE);
        btnFinish.setVisibility(trip.isOngoing() ? View.VISIBLE : View.GONE);
        btnAddMain.setVisibility(!trip.isOngoing() && !trip.addedToMain ? View.VISIBLE : View.GONE);
        btnRmMain.setVisibility(trip.addedToMain ? View.VISIBLE : View.GONE);

        recordsBox.removeAllViews();
        List<TripRecord> recs = db.tripRecords(tripId);
        if (recs.isEmpty()) {
            TextView hint = new TextView(act);
            hint.setText("还没有明细，点「＋ 记一笔」开始记录");
            hint.setTextSize(12);
            hint.setTextColor(act.getResources().getColor(R.color.text_sub));
            hint.setPadding(0, U.dp(act, 8), 0, U.dp(act, 4));
            recordsBox.addView(hint);
            return;
        }
        for (final TripRecord r : recs) {
            View row = act.getLayoutInflater().inflate(R.layout.row_trip_record, recordsBox, false);
            TextView emoji = row.findViewById(R.id.tv_trr_emoji);
            TextView title = row.findViewById(R.id.tv_trr_title);
            TextView sub = row.findViewById(R.id.tv_trr_sub);
            TextView amount = row.findViewById(R.id.tv_trr_amount);

            emoji.setText(U.emojiFor(r.categoryName(), false));
            title.setText(r.categoryName());
            String st = U.dateLabel(r.date);
            if (r.note != null && !r.note.isEmpty()) st += " · " + r.note;
            sub.setText(st);
            amount.setText("-" + U.money(r.amountCents));

            row.findViewById(R.id.btn_trr_edit).setOnClickListener(v ->
                    AddTripRecordDialog.show(act, db, trip, r, () -> {
                        refresh();
                        onChanged.run();
                    }));
            row.findViewById(R.id.btn_trr_delete).setOnClickListener(v ->
                    new AlertDialog.Builder(act)
                            .setTitle("删除这条记录")
                            .setMessage("确定删除这条出游明细吗？删除后无法恢复。")
                            .setPositiveButton("删除", (d, w) -> {
                                db.deleteTripRecord(r.id);
                                refresh();
                                onChanged.run();
                            })
                            .setNegativeButton("取消", null)
                            .show());
            recordsBox.addView(row);
        }
    }
}
