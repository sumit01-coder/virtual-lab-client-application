package com.virtuallab.client.ui;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.AccessPayload;
import com.virtuallab.client.security.SecurityPolicy;
import com.virtuallab.client.util.NetworkHealthManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SimulationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simulation);

        BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(findViewById(R.id.instructionSheet));
        behavior.setPeekHeight(150);

        int practicalId = getIntent().getIntExtra("practical_id", 0);
        String simulatorUrl = getIntent().getStringExtra("simulator_url");

        WebView webView = findViewById(R.id.webSimulation);
        SecurityPolicy.applyOnlineWebViewPolicy(webView);

        NetworkHealthManager.checkAsync(this, result -> {
            if (!result.isGoodForServerData()) {
                Intent i = new Intent(this, NetworkStatusActivity.class);
                i.putExtra(NetworkStatusActivity.EXTRA_NEXT_SCREEN, NetworkStatusActivity.NEXT_MAIN);
                startActivity(i);
                finish();
                return;
            }
            validateAccessThenLoadSimulation(webView, practicalId, simulatorUrl);
        });

        findViewById(R.id.btnFinishSim).setOnClickListener(v -> {
            if (practicalId <= 0) {
                openResult(practicalId, simulatorUrl);
                return;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("practical_id", practicalId);
            ApiClient.get().markComplete(body).enqueue(new Callback<ApiEnvelope<Object>>() {
                @Override
                public void onResponse(Call<ApiEnvelope<Object>> call, Response<ApiEnvelope<Object>> response) {
                    openResult(practicalId, simulatorUrl);
                }

                @Override
                public void onFailure(Call<ApiEnvelope<Object>> call, Throwable t) {
                    Toast.makeText(SimulationActivity.this, "Completion sync failed, saved locally", Toast.LENGTH_SHORT).show();
                    openResult(practicalId, simulatorUrl);
                }
            });
        });
    }

    private void openResult(int practicalId, String simulatorUrl) {
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra("practical_id", practicalId);
        i.putExtra("simulator_url", simulatorUrl);
        startActivity(i);
    }

    private void validateAccessThenLoadSimulation(WebView webView, int practicalId, String simulatorUrl) {
        if (practicalId <= 0) {
            loadSimulationOrFallback(webView, simulatorUrl);
            return;
        }
        Map<String, Object> checkBody = new HashMap<>();
        checkBody.put("practical_id", practicalId);
        checkBody.put("action", "check");
        ApiClient.get().accessPractical(checkBody).enqueue(new Callback<ApiEnvelope<AccessPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<AccessPayload>> call, Response<ApiEnvelope<AccessPayload>> response) {
                ApiEnvelope<AccessPayload> env = response.body();
                if (!(response.isSuccessful() && env != null && env.status && env.data != null)) {
                    String msg = (env != null && env.message != null && !env.message.trim().isEmpty())
                            ? env.message : "Unable to access simulation";
                    Toast.makeText(SimulationActivity.this, msg, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                if (env.data.would_charge) {
                    TokenAccessDialog.show(
                            SimulationActivity.this,
                            env.data.tokens,
                            () -> commitAccessAndLoad(webView, practicalId, simulatorUrl),
                            SimulationActivity.this::finish
                    );
                } else {
                    commitAccessAndLoad(webView, practicalId, simulatorUrl);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<AccessPayload>> call, Throwable t) {
                Toast.makeText(SimulationActivity.this, "Access check failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void commitAccessAndLoad(WebView webView, int practicalId, String simulatorUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("practical_id", practicalId);
        body.put("action", "commit");
        ApiClient.get().accessPractical(body).enqueue(new Callback<ApiEnvelope<AccessPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<AccessPayload>> call, Response<ApiEnvelope<AccessPayload>> response) {
                ApiEnvelope<AccessPayload> env = response.body();
                if (response.isSuccessful() && env != null && env.status) {
                    loadSimulationOrFallback(webView, simulatorUrl);
                    return;
                }
                String msg = (env != null && env.message != null && !env.message.trim().isEmpty())
                        ? env.message : "Unable to access simulation";
                Toast.makeText(SimulationActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<AccessPayload>> call, Throwable t) {
                Toast.makeText(SimulationActivity.this, "Access check failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void loadSimulationOrFallback(WebView webView, String simulatorUrl) {
        if (simulatorUrl != null && !simulatorUrl.isEmpty()) {
            if (!SecurityPolicy.isTrustedHttpsUrl(simulatorUrl)) {
                Toast.makeText(this, "Simulation blocked: trusted HTTPS URL required.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            webView.loadUrl(simulatorUrl);
        } else {
            String html = "<html><body style='font-family:sans-serif;background:#eef2ff;padding:24px;'>"
                    + "<h2>Simulation Not Available</h2>"
                    + "<p>This practical does not have a simulator URL yet.</p></body></html>";
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        }
    }
}
