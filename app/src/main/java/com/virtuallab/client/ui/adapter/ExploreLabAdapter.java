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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExploreLabAdapter extends RecyclerView.Adapter<ExploreLabAdapter.VH> {
    private final List<LabItem> items;

    public ExploreLabAdapter(List<LabItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_explore_lab_card, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        LabItem item = items.get(position);
        holder.title.setText(item.title);
        holder.subject.setText(toDisplayCase(item.subject));
        holder.duration.setText(item.duration);
        holder.bindImages(extractImages(item.imageUrl));
        holder.subject.setVisibility(item.subject != null && !item.subject.trim().isEmpty() ? View.VISIBLE : View.GONE);
        holder.duration.setVisibility(item.duration != null && !item.duration.trim().isEmpty() ? View.VISIBLE : View.GONE);

        String difficulty = item.difficulty != null ? item.difficulty.trim() : "";
        holder.difficulty.setText(difficulty);
        holder.difficulty.setVisibility(difficulty.isEmpty() ? View.GONE : View.VISIBLE);
        String d = difficulty.toLowerCase(Locale.US);
        if (d.contains("advanced")) {
            holder.difficulty.setBackgroundResource(R.drawable.bg_badge_advanced);
            holder.difficulty.setTextColor(0xFF991B1B);
        } else if (d.contains("intermediate")) {
            holder.difficulty.setBackgroundResource(R.drawable.bg_badge_intermediate);
            holder.difficulty.setTextColor(0xFF92400E);
        } else if (!d.isEmpty()) {
            holder.difficulty.setBackgroundResource(R.drawable.bg_badge_free);
            holder.difficulty.setTextColor(0xFF166534);
        }

        if (item.locked) {
            holder.access.setVisibility(View.VISIBLE);
            holder.access.setText("Login required");
            holder.btnStart.setText("Login to Start");
        } else {
            holder.access.setVisibility(item.accessLabel != null && !item.accessLabel.trim().isEmpty() ? View.VISIBLE : View.GONE);
            holder.access.setText(item.accessLabel != null && !item.accessLabel.trim().isEmpty() ? item.accessLabel : "Simulation ready");
            holder.btnStart.setText("Start Lab");
        }

        View.OnClickListener open = v -> {
            Context ctx = v.getContext();
            if (!SessionStore.isLoggedIn()) {
                Toast.makeText(ctx, "Login required to access practicals", Toast.LENGTH_SHORT).show();
                ctx.startActivity(new Intent(ctx, LoginActivity.class));
                return;
            }
            if (item.locked) {
                Toast.makeText(ctx, "Login required for this lab", Toast.LENGTH_SHORT).show();
                ctx.startActivity(new Intent(ctx, LoginActivity.class));
                return;
            }
            Intent i = new Intent(ctx, LabDetailsActivity.class);
            i.putExtra("id", item.id);
            i.putExtra("title", item.title);
            i.putExtra("meta", item.meta());
            ctx.startActivity(i);
        };
        holder.itemView.setOnClickListener(open);
        holder.btnStart.setOnClickListener(open);
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

    private String toDisplayCase(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return "";

        String[] parts = trimmed.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.US));
            }
        }
        return out.toString();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, subject, difficulty, duration, access, btnStart;
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
            subject = itemView.findViewById(R.id.txtLabSubject);
            difficulty = itemView.findViewById(R.id.txtDifficultyBadge);
            duration = itemView.findViewById(R.id.txtDurationBadge);
            access = itemView.findViewById(R.id.txtAccess);
            btnStart = itemView.findViewById(R.id.btnStartLab);
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
                cover.setScaleType(ImageView.ScaleType.CENTER);
                cover.setColorFilter(0x88FFFFFF);
                cover.setBackgroundResource(R.drawable.bg_image_placeholder);
                cover.setImageResource(R.drawable.ic_labs_24);
                return;
            }
            cover.clearColorFilter();
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackgroundResource(0);
            Glide.with(itemView)
                    .load(images.get(index))
                    .centerCrop()
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .into(cover);
        }
    }
}
