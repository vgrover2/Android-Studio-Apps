package com.example.lab3;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
public class CustomRecyclerViewAdapter extends RecyclerView.Adapter<CustomRecyclerViewAdapter.ViewHolder> {

    private List<itemData> list = Collections.emptyList();
    private Context context;
    CustomRecyclerViewAdapter(List<itemData> list, Context context) {
        this.list = list;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.single_unit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomRecyclerViewAdapter.ViewHolder holder, int position) {
        holder.name_view.setText(list.get(position).itemName);
        holder.val_view.setText(list.get(position).itemValue);

    }

    @Override
    public int getItemCount() {
        return list.size();
    }
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView name_view;
        private final TextView val_view;

        ViewHolder(View itemView) {
            super(itemView);
            name_view = itemView.findViewById(R.id.items_name);
            val_view = itemView.findViewById(R.id.items_value);
        }
    }
}
