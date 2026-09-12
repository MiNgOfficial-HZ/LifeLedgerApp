package com.lifebook.ledger.page;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Trip;
import com.lifebook.ledger.ui.AddTripDialog;
import com.lifebook.ledger.ui.AddTripRecordDialog;
import com.lifebook.ledger.ui.TripDetailDialog;
import com.lifebook.ledger.util.U;

import java.util.ArrayList;
import java.util.List;

/** 出游账本：每次出游独立记账 */
public class TravelPage {

    private final MainActivity act;
    private final DbHelper db;
    private final TripAdapter adapter = new TripAdapter();
    private final List<Trip> data = new ArrayList<>();
    private ListView list;
    private TextView empty;

    public TravelPage(MainActivity a) {
        act = a;
        db = a.db;
        list = a.findViewById(R.id.list_trips);
        empty = a.findViewById(R.id.tv_empty_trips);
        list.setAdapter(adapter);
        a.findViewById(R.id.btn_new_trip).setOnClickListener(v -> AddTripDialog.show(act, db, null, this::show));
    }

    public void show() {
        data.clear();
        data.addAll(db.trips());
        adapter.notifyDataSetChanged();
        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void onAdd(Trip t) {
        if (!t.isOngoing()) {
            U.toast(act, "已结束的出游不能再记新账");
            return;
        }
        AddTripRecordDialog.show(act, db, t, null, this::show);
    }

    void onDetail(Trip t) {
        TripDetailDialog.show(act, db, t.id, this::show);
    }

    void onDelete(Trip t) {
        String msg = "确定删除「" + t.name + "」吗？全部出游明细将一并删除。\n"
                + (t.addedToMain ? "账本中已同步的那条支出会保留。" : "此操作不可恢复。");
        new AlertDialog.Builder(act)
                .setTitle("删除出游")
                .setMessage(msg)
                .setPositiveButton("删除", (d, w) -> {
                    db.deleteTrip(t.id);
                    U.toast(act, "已删除");
                    show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class TripAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Trip getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return data.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            if (convertView == null) {
                v = act.getLayoutInflater().inflate(R.layout.row_trip, parent, false);
            } else {
                v = convertView;
            }
            final Trip t = getItem(position);
            boolean ongoing = t.isOngoing();
            TextView emoji = v.findViewById(R.id.tv_trip_emoji);
            TextView name = v.findViewById(R.id.tv_trip_name);
            TextView status = v.findViewById(R.id.tv_trip_status);
            TextView range = v.findViewById(R.id.tv_trip_range);
            TextView tag = v.findViewById(R.id.tv_trip_tag);
            TextView total = v.findViewById(R.id.tv_trip_total);
            TextView btnAdd = v.findViewById(R.id.btn_trip_add);

            emoji.setText("🧳");
            name.setText(t.name);
            if (ongoing) {
                status.setText("进行中");
                status.setBackgroundResource(R.drawable.bg_chip_sel);
                status.setTextColor(act.getResources().getColor(R.color.chip_sel_text));
            } else {
                status.setText("已结束");
                status.setBackgroundResource(R.drawable.bg_chip);
                status.setTextColor(act.getResources().getColor(R.color.text_sub));
            }
            if (ongoing) {
                range.setText(U.dateLabel(t.startDate) + " 开始 · 至今");
            } else {
                String end = t.endDate == null || t.endDate.isEmpty() ? t.startDate : t.endDate;
                range.setText(U.dateLabel(t.startDate) + " ~ " + U.dateLabel(end));
            }
            if (t.addedToMain) {
                tag.setText("✓ 已合并入" + db.bookLabel(t.addedBook) + "：出游支出 · " + t.name);
                tag.setVisibility(View.VISIBLE);
            } else {
                tag.setVisibility(View.GONE);
            }
            total.setText("¥" + U.money(db.tripTotal(t.id)));
            btnAdd.setVisibility(ongoing ? View.VISIBLE : View.GONE);

            v.findViewById(R.id.trip_head).setOnClickListener(v2 -> onDetail(t));
            v.findViewById(R.id.btn_trip_add).setOnClickListener(v2 -> onAdd(t));
            v.findViewById(R.id.btn_trip_detail).setOnClickListener(v2 -> onDetail(t));
            v.findViewById(R.id.btn_trip_delete).setOnClickListener(v2 -> onDelete(t));
            return v;
        }
    }
}
