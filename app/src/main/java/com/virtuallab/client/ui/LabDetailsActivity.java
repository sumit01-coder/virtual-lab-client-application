package com.virtuallab.client.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
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
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.AccessPayload;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.LabDetailsPayload;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.offline.OfflineSimulationManager;
import com.virtuallab.client.offline.OfflineSyncManager;
import com.virtuallab.client.offline.SimulationMeta;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LabDetailsActivity extends AppCompatActivity {

    private int practicalId;
    private String simulatorUrl = "";
    private LabDetailsPayload payload;

    private TextView txtDetailTitle;
    private TextView txtDepartment;
    private TextView chipDifficulty;
    private TextView txtDuration;
    private TextView txtOverview;
    private TextView txtOverviewFull;
    private TextView txtObjectivesLabel;
    private TextView txtMaterialsLabel;
    private TextView txtProcedureLabel;
    private TextView txtProcedure;
    private TextView txtFileSize;
    private TextView txtVersion;
    private TextView txtLastUpdated;
    private TextView txtUpdateBadge;
    private View cardLearningContent;
    private View btnDownloadOffline;
    private View btnOpenOffline;
    private View btnOfflineNotAvailable;
    private LinearLayout objectivesContainer;
    private LinearLayout materialsContainer;

    private OfflineSimulationManager offlineManager;
    private OfflineSyncManager syncManager;
    private boolean accessGranted = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lab_details);

        if (!isInternetAvailable()) {
            startActivity(new Intent(this, OfflineLabsActivity.class));
            finish();
            return;
        }

        practicalId = getIntent().getIntExtra("id", 0);

        try {
            offlineManager = new OfflineSimulationManager(this);
            syncManager = new OfflineSyncManager(this);
        } catch (Exception e) {
            Toast.makeText(this, "Secure offline module init failed", Toast.LENGTH_LONG).show();
        }

        bindViews();
        setupActions();

        if (practicalId > 0) {
            if (!SessionStore.isLoggedIn()) {
                Toast.makeText(this, "Login required to access practical", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
            validateAccessThenLoad(practicalId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOfflineState();
    }

    private void bindViews() {
        txtDetailTitle = findViewById(R.id.txtDetailTitle);
        txtDepartment = findViewById(R.id.txtDepartment);
        chipDifficulty = findViewById(R.id.chipDifficulty);
        txtDuration = findViewById(R.id.txtDuration);
        txtOverview = findViewById(R.id.txtOverview);
        txtOverviewFull = findViewById(R.id.txtOverviewFull);
        txtObjectivesLabel = findViewById(R.id.txtObjectivesLabel);
        txtMaterialsLabel = findViewById(R.id.txtMaterialsLabel);
        txtProcedureLabel = findViewById(R.id.txtProcedureLabel);
        txtProcedure = findViewById(R.id.txtProcedure);
        txtFileSize = findViewById(R.id.txtFileSize);
        txtVersion = findViewById(R.id.txtVersion);
        txtLastUpdated = findViewById(R.id.txtLastUpdated);
        txtUpdateBadge = findViewById(R.id.txtUpdateAvailable);
        cardLearningContent = findViewById(R.id.cardLearningContent);
        btnDownloadOffline = findViewById(R.id.btnDownloadOffline);
        btnOpenOffline = findViewById(R.id.btnOpenOffline);
        btnOfflineNotAvailable = findViewById(R.id.btnOfflineNotAvailable);
        objectivesContainer = findViewById(R.id.objectivesContainer);
        materialsContainer = findViewById(R.id.materialsContainer);
    }

    private void setupActions() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnOpenOnline).setOnClickListener(v -> openSimulationOnline());
        findViewById(R.id.btnPreview).setOnClickListener(v -> openSimulationOnline());

        btnDownloadOffline.setOnClickListener(v -> {
            if (payload == null) return;

            String zipUrl = safe(payload.simulation_zip_url);
            if (!OfflineSimulationManager.isSupportedHttpsUrl(zipUrl)) {
                Toast.makeText(this,
                        "Offline download not available for this lab yet. Please try again later or contact admin.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            Intent i = new Intent(this, DownloadSimulationActivity.class);
            i.putExtra("lab_id", String.valueOf(payload.id));
            i.putExtra("title", safe(payload.title));
            i.putExtra("version", safe(payload.version));
            i.putExtra("checksum", safe(payload.checksum));
            i.putExtra("zip_url", zipUrl);
            i.putExtra("main_file", safe(payload.main_file));
            i.putExtra("thumbnail_url", safe(payload.thumbnail_url));
            i.putExtra("last_updated", safe(payload.last_updated));
            i.putExtra("subject", safe(payload.subject));
            i.putExtra("difficulty", safe(payload.difficulty));
            i.putExtra("duration", payload.duration_minutes > 0 ? payload.duration_minutes + " mins" : "");
            i.putExtra("overview", safe(payload.overview));
            i.putExtra("objectives", safe(payload.objectives));
            i.putExtra("materials", safe(payload.materials));
            i.putExtra("procedure", safe(payload.procedure));
            startActivity(i);
        });

        btnOpenOffline.setOnClickListener(v -> {
            if (offlineManager == null || payload == null) return;
            offlineManager.openOfflineSimulation(this, String.valueOf(payload.id));
        });

        findViewById(R.id.btnOfflineLabs).setOnClickListener(v ->
                startActivity(new Intent(this, OfflineLabsActivity.class)));
    }

    private void validateAccessThenLoad(int practicalId) {
        Map<String, Object> checkBody = new HashMap<>();
        checkBody.put("practical_id", practicalId);
        checkBody.put("action", "check");

        ApiClient.get().accessPractical(checkBody).enqueue(new Callback<ApiEnvelope<AccessPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<AccessPayload>> call, Response<ApiEnvelope<AccessPayload>> response) {
                ApiEnvelope<AccessPayload> env = response.body();
                if (!(response.isSuccessful() && env != null && env.status)) {
                    Toast.makeText(LabDetailsActivity.this, "Unable to access practical", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                commitPracticalAccess(practicalId);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<AccessPayload>> call, Throwable t) {
                Toast.makeText(LabDetailsActivity.this, "Access check failed", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void commitPracticalAccess(int practicalId) {
        Map<String, Object> body = new HashMap<>();
        body.put("practical_id", practicalId);
        body.put("action", "commit");

        ApiClient.get().accessPractical(body).enqueue(new Callback<ApiEnvelope<AccessPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<AccessPayload>> call, Response<ApiEnvelope<AccessPayload>> response) {
                ApiEnvelope<AccessPayload> env = response.body();
                if (!(response.isSuccessful() && env != null && env.status)) {
                    Toast.makeText(LabDetailsActivity.this, "Unable to access practical", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                accessGranted = true;
                loadDetails(practicalId);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<AccessPayload>> call, Throwable t) {
                Toast.makeText(LabDetailsActivity.this, "Access check failed", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void loadDetails(int id) {
        ApiClient.get().labDetails(id).enqueue(new Callback<ApiEnvelope<LabDetailsPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<LabDetailsPayload>> call, Response<ApiEnvelope<LabDetailsPayload>> response) {
                ApiEnvelope<LabDetailsPayload> env = response.body();
                if (env == null || !env.status || env.data == null) return;
                payload = env.data;
                simulatorUrl = safe(payload.simulator_url);

                txtDetailTitle.setText(safe(payload.title));
                txtDepartment.setText(safe(payload.subject));
                chipDifficulty.setText(safe(payload.difficulty));
                txtDuration.setText(payload.duration_minutes > 0 ? payload.duration_minutes + " mins" : "-");
                String overviewText = sanitizeText(payload.overview);
                txtOverview.setText(overviewText);
                txtOverviewFull.setText(overviewText);
                bindPointSection(objectivesContainer, txtObjectivesLabel, payload.objectives, "No objectives or observations available.");
                bindPointSection(materialsContainer, txtMaterialsLabel, payload.materials, "No materials listed.");
                bindTextSection(txtProcedure, txtProcedureLabel, "Procedure", payload.procedure);
                bindLearningContentVisibility();
                txtFileSize.setText(safe(payload.file_size));
                txtVersion.setText(safe(payload.version));
                txtLastUpdated.setText(safe(payload.last_updated));

                ImageView imgHero = findViewById(R.id.imgHero);
                if (!TextUtils.isEmpty(payload.thumbnail_url)) {
                    Glide.with(LabDetailsActivity.this).load(payload.thumbnail_url).into(imgHero);
                }

                refreshOfflineState();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<LabDetailsPayload>> call, Throwable t) {
                Toast.makeText(LabDetailsActivity.this, "Failed to load details", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshOfflineState() {
        if (payload == null || offlineManager == null) return;
        SimulationMeta existing = offlineManager.getStorageManager().getMeta(String.valueOf(payload.id));

        boolean downloaded = existing != null;
        boolean canDownload = OfflineSimulationManager.isSupportedHttpsUrl(safe(payload.simulation_zip_url));

        btnOpenOffline.setVisibility(downloaded ? View.VISIBLE : View.GONE);

        if (downloaded) {
            btnDownloadOffline.setVisibility(View.GONE);
            if (btnOfflineNotAvailable != null) btnOfflineNotAvailable.setVisibility(View.GONE);
        } else if (canDownload) {
            btnDownloadOffline.setVisibility(View.VISIBLE);
            if (btnOfflineNotAvailable != null) btnOfflineNotAvailable.setVisibility(View.GONE);
        } else {
            btnDownloadOffline.setVisibility(View.GONE);
            if (btnOfflineNotAvailable != null) btnOfflineNotAvailable.setVisibility(View.VISIBLE);
        }

        boolean updateAvailable = downloaded && existing.isUpdateAvailable(payload.version);
        txtUpdateBadge.setVisibility(updateAvailable ? View.VISIBLE : View.GONE);
        if (updateAvailable) {
            Toast.makeText(this, "Update available", Toast.LENGTH_SHORT).show();
            if (syncManager != null) {
                syncManager.syncSingleLabIfChanged(existing, payload, message ->
                        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show()));
            }
        }
    }

    private void openSimulationOnline() {
        if (!accessGranted) {
            Toast.makeText(this, "Validating practical access...", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, SimulationActivity.class);
        i.putExtra("practical_id", practicalId);
        i.putExtra("simulator_url", simulatorUrl);
        startActivity(i);
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities cap = cm.getNetworkCapabilities(net);
        return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void bindLearningContentVisibility() {
        boolean hasOverview = !sanitizeText(payload != null ? payload.overview : "").isEmpty();
        boolean hasObjectives = !splitPoints(payload != null ? payload.objectives : "").isEmpty();
        boolean hasMaterials = !splitPoints(payload != null ? payload.materials : "").isEmpty();
        boolean hasProcedure = !sanitizeText(payload != null ? payload.procedure : "").isEmpty();
        cardLearningContent.setVisibility(hasOverview || hasObjectives || hasMaterials || hasProcedure ? View.VISIBLE : View.GONE);
    }

    private void bindPointSection(LinearLayout container, TextView label, String raw, String fallback) {
        if (container == null || label == null) return;
        container.removeAllViews();
        java.util.List<String> points = splitPoints(raw);
        if (points.isEmpty()) {
            points.add(fallback);
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String point : points) {
            View row = inflater.inflate(R.layout.item_detail_point, container, false);
            TextView text = row.findViewById(R.id.txtPoint);
            text.setText(point);
            container.addView(row);
        }
        label.setVisibility(View.VISIBLE);
        container.setVisibility(View.VISIBLE);
    }

    private void bindTextSection(TextView content, TextView label, String title, String raw) {
        if (content == null || label == null) return;
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

    private java.util.List<String> splitPoints(String raw) {
        java.util.List<String> out = new java.util.ArrayList<>();
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
}
