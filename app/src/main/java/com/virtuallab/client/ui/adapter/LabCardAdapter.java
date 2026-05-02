package com.virtuallab.client.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.virtuallab.client.R;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.model.LabItem;
import com.virtuallab.client.ui.LabDetailsActivity;
import com.virtuallab.client.ui.LoginActivity;
import com.virtuallab.client.util.ImageUrlSanitizer;

import java.util.List;
import java.util.ArrayList;

public class LabCardAdapter extends RecyclerView.Adapter<LabCardAdapter.VH> {
    private final List<LabItem> items;

    public LabCardAdapter(List<LabItem> items) {
        this.items = items;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lab_card, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        LabItem item = items.get(position);
        holder.title.setText(item.title);
        String meta = item.meta();
        if (item.locked) {
            meta = meta.isEmpty() ? "Login required" : (meta + " | Login required");
        }
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);

        holder.durationBadge.setText(item.duration);
        holder.durationBadge.setVisibility(item.duration != null && !item.duration.trim().isEmpty() ? View.VISIBLE : View.GONE);
        holder.bindImages(extractImages(item.imageUrl));

        if (item.accessLabel != null && !item.accessLabel.trim().isEmpty()) {
            holder.accessBadge.setVisibility(View.VISIBLE);
            holder.accessBadge.setText(item.accessLabel);
            if (item.locked) {
                holder.accessBadge.setBackgroundResource(R.drawable.bg_badge_login);
                holder.accessBadge.setTextColor(0xFF92400E);
            } else {
                holder.accessBadge.setBackgroundResource(R.drawable.bg_badge_free);
                holder.accessBadge.setTextColor(0xFF166534);
            }
        } else {
            holder.accessBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            Context ctx = v.getContext();
            if (!SessionStore.isLoggedIn()) {
                Toast.makeText(ctx, "Login required to access practicals", Toast.LENGTH_SHORT).show();
                ctx.startActivity(new Intent(ctx, LoginActivity.class));
                return;
            }
            if (item.locked) {
                Toast.makeText(ctx, "Login to access this practical", Toast.LENGTH_SHORT).show();
                ctx.startActivity(new Intent(ctx, LoginActivity.class));
                return;
            }
            Intent intent = new Intent(ctx, LabDetailsActivity.class);
            intent.putExtra("id", item.id);
            intent.putExtra("title", item.title);
            intent.putExtra("meta", item.meta());
            ctx.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        holder.stopImageRotation();
        super.onViewRecycled(holder);
    }

    private List<String> extractImages(String payload) {
        List<String> raw = new ArrayList<>();
        if (payload == null) return raw;
        String[] parts = payload.split("\\|\\|");
        for (String p : parts) {
            String s = p == null ? "" : p.trim();
            if (!s.isEmpty()) raw.add(s);
        }
        return ImageUrlSanitizer.keepDecodable(raw);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, meta, durationBadge, accessBadge;
        ImageView cover;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable rotator = new Runnable() {
            @Override
            public void run() {
                if (images.size() <= 1) return;
                index = (index + 1) % images.size();
                loadCurrent();
                handler.postDelayed(this, 2600);
            }
        };
        private List<String> images = new ArrayList<>();
        private int index = 0;

        VH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.imgLabCover);
            title = itemView.findViewById(R.id.txtLabTitle);
            meta = itemView.findViewById(R.id.txtLabMeta);
            durationBadge = itemView.findViewById(R.id.txtDurationBadge);
            accessBadge = itemView.findViewById(R.id.txtAccessBadge);
        }

        void bindImages(List<String> newImages) {
            stopImageRotation();
            images = newImages != null ? newImages : new ArrayList<>();
            index = 0;
            loadCurrent();
            if (images.size() > 1) {
                handler.postDelayed(rotator, 2600);
            }
        }

        void stopImageRotation() {
            handler.removeCallbacks(rotator);
        }

        private void loadCurrent() {
            if (images == null || images.isEmpty()) {
                cover.setImageResource(0);
                cover.setBackgroundResource(R.drawable.bg_gradient_soft);
                return;
            }
            Glide.with(itemView)
                    .load(images.get(index))
                    .centerCrop()
                    .placeholder(R.drawable.bg_gradient_soft)
                    .error(R.drawable.bg_gradient_soft)
                    .into(cover);
        }
    }
}

