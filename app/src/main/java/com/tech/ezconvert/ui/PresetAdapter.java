package com.tech.ezconvert.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.tech.ezconvert.R;
import java.util.ArrayList;
import java.util.List;

public class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.ViewHolder> {
    private List<String> presets = new ArrayList<>();
    private int selectedPosition = 0;
    private OnPresetClickListener listener;

    public interface OnPresetClickListener {
        void onPresetClick(String presetName, int position);
    }

    public void setData(List<String> presets, int defaultPosition) {
        this.presets.clear();
        this.presets.addAll(presets);
        this.selectedPosition = defaultPosition;
        notifyDataSetChanged();
    }

    public void setListener(OnPresetClickListener listener) {
        this.listener = listener;
    }

    public String getSelectedPreset() {
        if (selectedPosition >= 0 && selectedPosition < presets.size()) {
            return presets.get(selectedPosition);
        }
        return null;
    }

    public void selectPosition(int position) {
        int old = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(old);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_preset_chip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String name = presets.get(position);
        holder.textView.setText(name);
        boolean isSelected = (position == selectedPosition);

        int colorSurface = MaterialColors.getColor(holder.itemView.getContext(),
                com.google.android.material.R.attr.colorSurface, 0);
        int colorPrimaryContainer = MaterialColors.getColor(holder.itemView.getContext(),
                com.google.android.material.R.attr.colorPrimaryContainer, 0);
        int colorOnPrimaryContainer = MaterialColors.getColor(holder.itemView.getContext(),
                com.google.android.material.R.attr.colorOnPrimaryContainer, 0);
        int colorOnSurface = MaterialColors.getColor(holder.itemView.getContext(),
                com.google.android.material.R.attr.colorOnSurface, 0);

        if (isSelected) {
            holder.cardView.setCardBackgroundColor(colorPrimaryContainer);
            holder.textView.setTextColor(colorOnPrimaryContainer);
        } else {
            holder.cardView.setCardBackgroundColor(colorSurface);
            holder.textView.setTextColor(colorOnSurface);
        }

        holder.itemView.setOnClickListener(v -> {
            int old = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(old);
            notifyItemChanged(position);
            if (listener != null) {
                listener.onPresetClick(name, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return presets.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView textView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            textView = itemView.findViewById(R.id.preset_name);
        }
    }
}