package com.virtuallab.client.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.ProgressPayload;
import com.virtuallab.client.data.DepartmentPrefs;
import com.virtuallab.client.ui.CertificateActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProgressFragment extends Fragment {
    private long prefsVersionSeen = -1L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_progress, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyHeroInsets(view);
        loadProgress(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) return;
        long v = DepartmentPrefs.getVersion(requireContext());
        if (v != prefsVersionSeen) {
            prefsVersionSeen = v;
            View root = getView();
            if (root != null) loadProgress(root);
        }
    }

    private void loadProgress(@NonNull View view) {
        TextView streak = view.findViewById(R.id.txtStreak);
        TextView heroSecondary = view.findViewById(R.id.txtProgressHeroSecondary);
        TextView completedLabs = view.findViewById(R.id.txtCompletedLabs);
        TextView tokens = view.findViewById(R.id.txtTokens);
        TextView certificatesReady = view.findViewById(R.id.txtCertificatesReady);
        TextView noCompleted = view.findViewById(R.id.txtNoCompleted);
        TextView noCertificates = view.findViewById(R.id.txtNoCertificates);
        LinearLayout completedList = view.findViewById(R.id.completedList);
        LinearLayout certificateList = view.findViewById(R.id.certificateList);

        ApiClient.get().progress().enqueue(new Callback<ApiEnvelope<ProgressPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<ProgressPayload>> call, Response<ApiEnvelope<ProgressPayload>> response) {
                ApiEnvelope<ProgressPayload> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null || env.data.summary == null) return;

                streak.setText("Learning streak: " + env.data.summary.streak_days + " days");
                completedLabs.setText(String.valueOf(env.data.summary.completed_labs));
                tokens.setText(String.valueOf(env.data.summary.tokens));
                heroSecondary.setText("Certificates: 0");

                completedList.removeAllViews();
                if (env.data.completed == null || env.data.completed.isEmpty()) {
                    completedList.addView(noCompleted);
                } else {
                    int shown = 0;
                    for (int i = 0; i < env.data.completed.size() && shown < 8; i++) {
                        ProgressPayload.CompletedItem item = env.data.completed.get(i);
                        if (!DepartmentPrefs.allows(requireContext(), item.subject)) continue;
                        shown++;

                        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_progress_completed, completedList, false);
                        TextView index = row.findViewById(R.id.txtCompletedIndex);
                        TextView title = row.findViewById(R.id.txtCompletedTitle);
                        TextView meta = row.findViewById(R.id.txtCompletedMeta);

                        String titleText = item.title != null && !item.title.trim().isEmpty() ? item.title : ("Practical #" + item.practical_id);
                        String subject = item.subject != null && !item.subject.trim().isEmpty() ? item.subject : "General";
                        index.setText(String.format("#%02d", shown));
                        title.setText(titleText);
                        meta.setText(toDisplayCase(subject));
                        completedList.addView(row);
                    }
                    if (shown == 0) completedList.addView(noCompleted);
                }

                certificateList.removeAllViews();
                if (env.data.departments == null || env.data.departments.isEmpty()) {
                    certificatesReady.setText("0");
                    heroSecondary.setText("Certificates: 0");
                    certificateList.addView(noCertificates);
                    return;
                }

                int shownDepartments = 0;
                int readyCount = 0;
                for (ProgressPayload.DepartmentProgress dept : env.data.departments) {
                    if (!DepartmentPrefs.allows(requireContext(), dept.department_name)) continue;
                    shownDepartments++;

                    View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_progress_certificate, certificateList, false);
                    TextView title = card.findViewById(R.id.txtCertificateDept);
                    TextView meta = card.findViewById(R.id.txtCertificateProgress);
                    TextView status = card.findViewById(R.id.txtCertificateStatus);
                    MaterialButton action = card.findViewById(R.id.btnCertificateAction);
                    ProgressBar progressBar = card.findViewById(R.id.progressCertificate);

                    title.setText((dept.department_name == null || dept.department_name.trim().isEmpty()) ? "Department" : toDisplayCase(dept.department_name));
                    meta.setText("Progress: " + dept.completed_practicals + "/" + dept.total_practicals + " (" + dept.progress_percent + "%)");
                    progressBar.setProgress(Math.max(0, Math.min(100, dept.progress_percent)));

                    if (dept.certificate_ready && dept.certificate_url != null && !dept.certificate_url.trim().isEmpty()) {
                        readyCount++;
                        status.setText("Certificate unlocked");
                        status.setTextColor(requireContext().getColor(android.R.color.white));
                        status.setBackgroundResource(R.drawable.bg_button_primary);
                        action.setVisibility(View.VISIBLE);
                        action.setOnClickListener(v -> {
                            Intent i = new Intent(requireContext(), CertificateActivity.class);
                            i.putExtra("dept_id", dept.department_id);
                            i.putExtra("dept_name", dept.department_name);
                            startActivity(i);
                        });
                    } else {
                        status.setText("Keep completing labs");
                    }

                    certificateList.addView(card);
                }

                certificatesReady.setText(String.valueOf(readyCount));
                heroSecondary.setText("Certificates: " + readyCount);
                if (shownDepartments == 0) {
                    certificateList.addView(noCertificates);
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<ProgressPayload>> call, Throwable t) {
            }
        });
    }

    private void applyHeroInsets(@NonNull View root) {
        View hero = root.findViewById(R.id.progressHero);
        if (hero == null) return;

        final int baseTopPadding = hero.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(hero, (v, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(
                    v.getPaddingLeft(),
                    baseTopPadding + topInset,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(hero);
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
                out.append(part.substring(1).toLowerCase());
            }
        }
        return out.toString();
    }
}
