package com.lifebook.ledger.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.lifebook.ledger.MainActivity;
import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Transfer;
import com.lifebook.ledger.util.U;

import java.util.ArrayList;
import java.util.List;

/** 转账列表 */
public class TransferAdapter extends BaseAdapter {

    public interface Listener {
        void onClick(Transfer t);

        void onEdit(Transfer t);

        void onDelete(Transfer t);
    }

    private final MainActivity act;
    private final DbHelper db;
    private final Listener listener;
    private final List<Transfer> data = new ArrayList<>();

    public TransferAdapter(MainActivity act, DbHelper db, Listener listener) {
        this.act = act;
        this.db = db;
        this.listener = listener;
    }

    public void setData(List<Transfer> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Transfer getItem(int position) {
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
            v = act.getLayoutInflater().inflate(R.layout.row_record, parent, false);
        } else {
            v = convertView;
        }
        final Transfer t = getItem(position);
        TextView emoji = v.findViewById(R.id.tv_emoji);
        TextView title = v.findViewById(R.id.tv_title);
        TextView sub = v.findViewById(R.id.tv_sub);
        TextView amount = v.findViewById(R.id.tv_amount);
        emoji.setText("🔁");
        title.setText(db.bookLabel(t.fromBook) + " → " + db.bookLabel(t.toBook));
        String st = U.dateLabel(t.date);
        if (t.note != null && !t.note.isEmpty()) st += " · " + t.note;
        sub.setText(st);
        amount.setText(U.money(t.amountCents));
        amount.setTextColor(act.getResources().getColor(R.color.primary));
        v.findViewById(R.id.btn_edit).setOnClickListener(x -> {
            if (listener != null) listener.onEdit(t);
        });
        v.findViewById(R.id.btn_delete).setOnClickListener(x -> {
            if (listener != null) listener.onDelete(t);
        });
        v.findViewById(R.id.btn_edit).setVisibility(View.VISIBLE);
        v.findViewById(R.id.btn_delete).setVisibility(View.VISIBLE);
        return v;
    }
}
