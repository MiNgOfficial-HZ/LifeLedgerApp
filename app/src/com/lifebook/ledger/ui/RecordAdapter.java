package com.lifebook.ledger.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.lifebook.ledger.R;
import com.lifebook.ledger.db.DbHelper;
import com.lifebook.ledger.model.Record;
import com.lifebook.ledger.util.U;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordAdapter extends BaseAdapter {

    public interface Listener {
        void onClick(Record r);

        void onEdit(Record r);

        void onDelete(Record r);

        void onLongClick(Record r);
    }

    private final Context ctx;
    private final List<Record> data = new ArrayList<>();
    private final Listener listener;
    private final Set<Long> selected = new HashSet<>();
    private boolean selectionMode = false;

    public RecordAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setData(List<Record> list) {
        data.clear();
        if (list != null) data.addAll(list);
        selected.clear();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean mode) {
        selectionMode = mode;
        if (!mode) selected.clear();
        notifyDataSetChanged();
    }

    public void toggleSelected(long id) {
        if (selected.contains(id)) selected.remove(id);
        else selected.add(id);
        notifyDataSetChanged();
    }

    public boolean isSelected(long id) {
        return selected.contains(id);
    }

    public int selectedCount() {
        return selected.size();
    }

    public List<Long> selectedIds() {
        return new ArrayList<>(selected);
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Record getItem(int position) {
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
            v = LayoutInflater.from(ctx).inflate(R.layout.row_record, parent, false);
        } else {
            v = convertView;
        }
        Record r = getItem(position);
        boolean sel = selectionMode && isSelected(r.id);
        boolean income = DbHelper.TYPE_INCOME.equals(r.type);
        TextView emoji = v.findViewById(R.id.tv_emoji);
        TextView title = v.findViewById(R.id.tv_title);
        TextView sub = v.findViewById(R.id.tv_sub);
        TextView amount = v.findViewById(R.id.tv_amount);
        View inner = v.findViewById(R.id.row_inner);
        inner.setBackgroundResource(sel ? R.drawable.bg_chip_sel : R.drawable.bg_card);
        if (selectionMode) {
            emoji.setText(sel ? "✔" : "⬜");
        } else {
            emoji.setText(U.emojiFor(r.cat1Name, income));
        }

        title.setText(r.title());
        String subText = U.dateLabel(r.date);
        if (r.note != null && !r.note.isEmpty()) subText += " · " + r.note;
        sub.setText(subText);
        amount.setText((income ? "+" : "-") + U.money(r.amountCents));
        amount.setTextColor(ctx.getResources().getColor(income ? R.color.income : R.color.expense));
        v.findViewById(R.id.btn_edit).setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        v.findViewById(R.id.btn_delete).setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        v.findViewById(R.id.btn_edit).setOnClickListener(v2 -> {
            if (!selectionMode) listener.onEdit(r);
        });
        v.findViewById(R.id.btn_delete).setOnClickListener(v2 -> {
            if (!selectionMode) listener.onDelete(r);
        });
        v.setOnClickListener(v2 -> {
            if (selectionMode) toggleSelected(r.id);
            else listener.onClick(r);
        });
        v.setOnLongClickListener(v2 -> {
            if (!selectionMode) {
                selectionMode = true;
                selected.add(r.id);
                notifyDataSetChanged();
                if (listener != null) listener.onLongClick(r);
            }
            return true;
        });
        return v;
    }
}
