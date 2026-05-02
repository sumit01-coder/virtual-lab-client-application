package com.virtuallab.client.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.virtuallab.client.R;

import java.util.List;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {

    public static class Item {
        public final String title;
        public final String description;
        public Item(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private final List<Item> items;
    public OnboardingAdapter(List<Item> items) { this.items = items; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Item item = items.get(position);
        holder.title.setText(item.title);
        holder.description.setText(item.description);
        holder.image.setScaleX(1f + (0.05f * position));
        holder.image.setScaleY(1f + (0.05f * position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        TextView description;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imgIllustration);
            title = itemView.findViewById(R.id.txtTitle);
            description = itemView.findViewById(R.id.txtDescription);
        }
    }
}

