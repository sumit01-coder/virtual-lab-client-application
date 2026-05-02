package com.virtuallab.client.ui;

import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.virtuallab.client.R;
import com.virtuallab.client.offline.OfflineSimulationManager;
import com.virtuallab.client.offline.SimulationMeta;

import java.util.ArrayList;
import java.util.List;

public class OfflineSimulationDetailsActivity extends AppCompatActivity {

    private OfflineSimulationManager manager;
    private SimulationMeta meta;
    private String labId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_simulation_details);

        labId = getIntent().getStringExtra("lab_id");
        if (labId == null || labId.trim().isEmpty()) {
            Toast.makeText(this, "Invalid offline lab", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            manager = new OfflineSimulationManager(this);
            meta = manager.getStorageManager().getMeta(labId);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to load offline details", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (meta == null) {
            Toast.makeText(this, "Offline simulation not found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        findViewById(R.id.btnBackOfflineDetails).setOnClickListener(v -> finish());
        findViewById(R.id.btnStartOfflineSimulation).setOnClickListener(v -> {
            startActivity(new android.content.Intent(this, OfflineWebViewActivity.class).putExtra("lab_id", labId));
        });
    }

    private void bindViews() {
        ((TextView) findViewById(R.id.txtOfflineDetailTitle)).setText(safe(meta.title));
        ((TextView) findViewById(R.id.txtOfflineDepartment)).setText(safe(meta.subject));
        ((TextView) findViewById(R.id.chipOfflineDifficulty)).setText(nonEmpty(meta.difficulty, "Offline"));
        ((TextView) findViewById(R.id.txtOfflineDuration)).setText(nonEmpty(meta.duration, "Ready"));
        ((TextView) findViewById(R.id.txtOfflineOverview)).setText(nonEmpty(sanitizeText(meta.overview), "Overview will be available once this simulation is downloaded from the latest lab details."));

        bindPointSection((LinearLayout) findViewById(R.id.offlineObjectivesContainer),
                (TextView) findViewById(R.id.txtOfflineObjectivesLabel),
                meta.objectives,
                "No objectives or observations saved for this offline package.");

        bindPointSection((LinearLayout) findViewById(R.id.offlineMaterialsContainer),
                (TextView) findViewById(R.id.txtOfflineMaterialsLabel),
                meta.materials,
                "No materials were saved for this offline package.");

        bindTextSection((TextView) findViewById(R.id.txtOfflineProcedure),
                (TextView) findViewById(R.id.txtOfflineProcedureLabel),
                "Procedure",
                meta.procedure);

        if (meta.thumbnailUrl != null && !meta.thumbnailUrl.trim().isEmpty()) {
            Glide.with(this).load(meta.thumbnailUrl).into((ImageView) findViewById(R.id.imgOfflineHero));
        }
    }

    private void bindPointSection(LinearLayout container, TextView label, String raw, String fallback) {
        container.removeAllViews();
        List<String> points = splitPoints(raw);
        if (points.isEmpty()) {
            points.add(fallback);
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String point : points) {
            View row = inflater.inflate(R.layout.item_detail_point, container, false);
            ((TextView) row.findViewById(R.id.txtPoint)).setText(point);
            container.addView(row);
        }
        label.setVisibility(View.VISIBLE);
        container.setVisibility(View.VISIBLE);
    }

    private void bindTextSection(TextView content, TextView label, String title, String raw) {
        String clean = sanitizeText(raw);
        if (clean.isEmpty()) {
            label.setVisibility(View.GONE);
            content.setVisibility(View.GONE);
            return;
        }
        label.setText(title);
        label.setVisibility(View.VISIBLE);
        content.setText(clean);
        content.setVisibility(View.VISIBLE);
    }

    private List<String> splitPoints(String raw) {
        List<String> out = new ArrayList<>();
        String clean = sanitizeText(raw);
        if (clean.isEmpty()) return out;

        String[] lines = clean.split("\\r?\\n");
        for (String line : lines) {
            String item = line.trim();
            if (item.isEmpty()) continue;
            item = item.replaceFirst("^[-*\\d.)\\s]+", "").trim();
            if (!item.isEmpty()) out.add(item);
        }

        if (out.isEmpty()) {
            String[] parts = clean.split("\\s*;\\s*");
            for (String part : parts) {
                String item = part.trim();
                if (!item.isEmpty()) out.add(item);
            }
        }
        return out;
    }

    private String sanitizeText(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        Spanned spanned = Html.fromHtml(trimmed, Html.FROM_HTML_MODE_LEGACY);
        return spanned.toString().replace('\u00A0', ' ').trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nonEmpty(String value, String fallback) {
        String clean = safe(value);
        return clean.isEmpty() ? fallback : clean;
    }
}
