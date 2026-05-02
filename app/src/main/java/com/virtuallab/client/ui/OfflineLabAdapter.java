package com.virtuallab.client.ui;

import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.virtuallab.client.R;
import com.virtuallab.client.offline.OfflineSimulationManager;
import com.virtuallab.client.offline.SimulationMeta;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Date;

public class OfflineLabAdapter extends RecyclerView.Adapter<OfflineLabAdapter.VH> {

    public interface ActionListener {
        void onOpenOffline(@NonNull SimulationMeta meta);
        void onUpdate(@NonNull SimulationMeta meta);
        void onDelete(@NonNull SimulationMeta meta);
    }

    private List<SimulationMeta> items;
    private final ActionListener listener;

    public OfflineLabAdapter(List<SimulationMeta> items, ActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void update(List<SimulationMeta> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_offline_lab, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SimulationMeta meta = items.get(position);
        h.txtTitle.setText(meta.title);
        h.txtVersion.setText("Version " + meta.version);
        h.txtSize.setText(OfflineSimulationManager.formatSize(meta.fileSize));
        String downloadedAt = meta.downloadedAt > 0
                ? new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date(meta.downloadedAt))
                : "";
        h.txtDate.setText(downloadedAt);
        h.txtDate.setVisibility(downloadedAt.isEmpty() ? View.GONE : View.VISIBLE);
        h.itemView.setOnClickListener(v -> listener.onOpenOffline(meta));
        h.btnOpenOfflineNow.setOnClickListener(v -> listener.onOpenOffline(meta));
        h.btnDeleteOfflineNow.setOnClickListener(v -> listener.onDelete(meta));

        if (meta.thumbnailUrl != null && !meta.thumbnailUrl.trim().isEmpty()) {
            Glide.with(h.itemView.getContext()).load(meta.thumbnailUrl).into(h.imgThumb);
        } else {
            h.imgThumb.setImageResource(R.drawable.bg_lab_hero);
        }

        h.btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), h.btnMenu);
            MenuInflater mi = popup.getMenuInflater();
            mi.inflate(R.menu.offline_lab_actions, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> handleMenuItem(item, meta));
            popup.show();
        });
    }

    private boolean handleMenuItem(MenuItem item, SimulationMeta meta) {
        if (item.getItemId() == R.id.action_open_offline) {
            listener.onOpenOffline(meta);
            return true;
        } else if (item.getItemId() == R.id.action_update) {
            listener.onUpdate(meta);
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            listener.onDelete(meta);
            return true;
        }
        return false;
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb;
        ImageView btnMenu;
        TextView btnOpenOfflineNow;
        TextView btnDeleteOfflineNow;
        TextView txtTitle;
        TextView txtVersion;
        TextView txtSize;
        TextView txtDate;

        VH(@NonNull View itemView) {
            super(itemView);
            imgThumb = itemView.findViewById(R.id.imgOfflineThumb);
            btnMenu = itemView.findViewById(R.id.btnOfflineMenu);
            btnOpenOfflineNow = itemView.findViewById(R.id.btnOpenOfflineNow);
            btnDeleteOfflineNow = itemView.findViewById(R.id.btnDeleteOfflineNow);
            txtTitle = itemView.findViewById(R.id.txtOfflineTitle);
            txtVersion = itemView.findViewById(R.id.txtOfflineVersion);
            txtSize = itemView.findViewById(R.id.txtOfflineSize);
            txtDate = itemView.findViewById(R.id.txtOfflineDate);
        }
    }
}
