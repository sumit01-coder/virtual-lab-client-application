package com.virtuallab.client.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.CatalogPayload;
import com.virtuallab.client.api.dto.DepartmentTreeItem;
import com.virtuallab.client.api.dto.ProfilePayload;
import com.virtuallab.client.data.DepartmentPrefs;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.ui.LoginActivity;
import com.virtuallab.client.ui.OfflineLabsActivity;
import com.virtuallab.client.ui.SettingsActivity;

import java.util.HashSet;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {
    private final Set<String> selectedSubjects = new HashSet<>();
    private LinearLayout chipsContainer;
    private TextView prefHint;
    private TextView txtProfileInitial;
    private TextView txtProfileName;
    private TextView txtProfileStats;
    private TextView txtProfileHeroPrimary;
    private TextView txtProfileHeroSecondary;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        applyHeroInsets(view);
        view.findViewById(R.id.btnSettingsTop).setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        view.findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
        view.findViewById(R.id.btnDownloadedSimulations).setOnClickListener(v -> startActivity(new Intent(requireContext(), OfflineLabsActivity.class)));
        view.findViewById(R.id.txtLogout).setOnClickListener(v -> {
            SessionStore.clear();
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        chipsContainer = view.findViewById(R.id.chipsPreferredDepartments);
        prefHint = view.findViewById(R.id.txtPrefHint);
        txtProfileInitial = view.findViewById(R.id.txtProfileInitial);
        txtProfileName = view.findViewById(R.id.txtProfileName);
        txtProfileStats = view.findViewById(R.id.txtProfileStats);
        txtProfileHeroPrimary = view.findViewById(R.id.txtProfileHeroPrimary);
        txtProfileHeroSecondary = view.findViewById(R.id.txtProfileHeroSecondary);
        selectedSubjects.clear();
        selectedSubjects.addAll(DepartmentPrefs.getSelectedSubjects(requireContext()));
        updateHint();
        applyProfileIdentity("Student", "Student profile");
        txtProfileHeroPrimary.setText("Role: Student");
        txtProfileHeroSecondary.setText("Tokens: 0");

        ApiClient.get().profile().enqueue(new Callback<ApiEnvelope<ProfilePayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<ProfilePayload>> call, Response<ApiEnvelope<ProfilePayload>> response) {
                ApiEnvelope<ProfilePayload> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null) return;

                ProfilePayload p = env.data;
                String displayName = firstNonEmpty(p.full_name, p.username, "Student");
                String role = firstNonEmpty(p.role, "student");
                applyProfileIdentity(
                        toDisplayCase(displayName),
                        toDisplayCase(role) + " | " + p.tokens + " tokens"
                );
                txtProfileHeroPrimary.setText("Role: " + toDisplayCase(role));
                txtProfileHeroSecondary.setText("Tokens: " + p.tokens);
            }

            @Override
            public void onFailure(Call<ApiEnvelope<ProfilePayload>> call, Throwable t) {
                // keep fallback text
            }
        });

        loadDepartments();
    }

    private void loadDepartments() {
        ApiClient.get().departmentsTree().enqueue(new Callback<ApiEnvelope<CatalogPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<CatalogPayload>> call, Response<ApiEnvelope<CatalogPayload>> response) {
                ApiEnvelope<CatalogPayload> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null || env.data.departments == null) return;
                chipsContainer.removeAllViews();
                for (DepartmentTreeItem dept : env.data.departments) {
                    addDeptRow(dept.name != null ? dept.name.trim() : "");
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<CatalogPayload>> call, Throwable t) {}
        });
    }

    private void addDeptRow(String subject) {
        if (!isAdded() || subject.isEmpty()) return;
        TextView chip = new TextView(requireContext());
        chip.setText(toDisplayCase(subject));
        chip.setTextSize(13f);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(8);
        chip.setLayoutParams(lp);

        applyStyle(chip, selectedSubjects.contains(subject));
        chip.setOnClickListener(v -> {
            if (selectedSubjects.contains(subject)) {
                selectedSubjects.remove(subject);
            } else {
                selectedSubjects.add(subject);
            }
            DepartmentPrefs.setSelectedSubjects(requireContext(), selectedSubjects);
            applyStyle(chip, selectedSubjects.contains(subject));
            updateHint();
        });
        chipsContainer.addView(chip);
    }

    private void applyStyle(TextView chip, boolean selected) {
        if (selected) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected);
            chip.setTextColor(requireContext().getColor(android.R.color.white));
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_soft);
            chip.setTextColor(requireContext().getColor(R.color.vl_primary));
        }
    }

    private void updateHint() {
        if (!isAdded()) return;
        prefHint.setText(selectedSubjects.isEmpty()
                ? "No selection means all departments stay visible."
                : ("Selected departments: " + selectedSubjects.size()));
    }

    private void applyProfileIdentity(String name, String stats) {
        if (!isAdded()) return;
        txtProfileName.setText(name);
        txtProfileStats.setText(stats);
        String initial = (name != null && !name.trim().isEmpty())
                ? String.valueOf(Character.toUpperCase(name.trim().charAt(0)))
                : "S";
        txtProfileInitial.setText(initial);
    }

    private void applyHeroInsets(@NonNull View root) {
        View hero = root.findViewById(R.id.profileHero);
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

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
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

    private int dp(int v) {
        return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
    }
}
