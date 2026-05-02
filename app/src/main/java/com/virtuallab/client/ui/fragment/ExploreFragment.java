package com.virtuallab.client.ui.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.LabListItem;
import com.virtuallab.client.data.DepartmentPrefs;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.model.LabItem;
import com.virtuallab.client.ui.adapter.ExploreLabAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ExploreFragment extends Fragment {

    private RecyclerView rv;
    private EditText edtSearch;
    private TextView chipAll, chipBeginner, chipIntermediate, chipAdvanced;
    private TextView txtViewAllExplore;
    private TextView txtHeroPrimary;
    private TextView txtHeroSecondary;
    private final List<LabItem> allItems = new ArrayList<>();
    private String selectedDifficulty = "All";
    private long prefsVersionSeen = -1L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rv = view.findViewById(R.id.rvExplore);
        edtSearch = view.findViewById(R.id.edtExploreSearch);
        chipAll = view.findViewById(R.id.chipAll);
        chipBeginner = view.findViewById(R.id.chipBeginner);
        chipIntermediate = view.findViewById(R.id.chipIntermediate);
        chipAdvanced = view.findViewById(R.id.chipAdvanced);
        txtViewAllExplore = view.findViewById(R.id.txtViewAllExplore);
        txtHeroPrimary = view.findViewById(R.id.txtExploreHeroPrimary);
        txtHeroSecondary = view.findViewById(R.id.txtExploreHeroSecondary);

        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        applyHeroInsets(view);
        txtHeroPrimary.setText("Visible labs: 0");
        txtHeroSecondary.setText("Difficulty: All");

        txtViewAllExplore.setOnClickListener(v -> {
            edtSearch.setText("");
            selectDifficulty("All");
        });

        chipAll.setOnClickListener(v -> selectDifficulty("All"));
        chipBeginner.setOnClickListener(v -> selectDifficulty("Beginner"));
        chipIntermediate.setOnClickListener(v -> selectDifficulty("Intermediate"));
        chipAdvanced.setOnClickListener(v -> selectDifficulty("Advanced"));

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        loadExplore();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) return;
        long currentVersion = DepartmentPrefs.getVersion(requireContext());
        if (currentVersion != prefsVersionSeen) {
            prefsVersionSeen = currentVersion;
            applyFilter();
        }
    }

    private void loadExplore() {
        ApiClient.get().explore("", "", "").enqueue(new Callback<ApiEnvelope<List<LabListItem>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<LabListItem>>> call, Response<ApiEnvelope<List<LabListItem>>> response) {
                ApiEnvelope<List<LabListItem>> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null) return;
                allItems.clear();
                allItems.addAll(map(env.data));
                applyFilter();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<LabListItem>>> call, Throwable t) {
                if (isAdded()) rv.setAdapter(new ExploreLabAdapter(new ArrayList<>()));
            }
        });
    }

    private void selectDifficulty(String difficulty) {
        selectedDifficulty = difficulty;
        txtHeroSecondary.setText("Difficulty: " + difficulty);
        setChipState(chipAll, "All".equals(difficulty));
        setChipState(chipBeginner, "Beginner".equals(difficulty));
        setChipState(chipIntermediate, "Intermediate".equals(difficulty));
        setChipState(chipAdvanced, "Advanced".equals(difficulty));
        applyFilter();
    }

    private void setChipState(TextView chip, boolean active) {
        if (!isAdded()) return;
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected);
            chip.setTextColor(requireContext().getColor(android.R.color.white));
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_soft);
            chip.setTextColor(requireContext().getColor(R.color.vl_text));
        }
    }

    private void applyFilter() {
        if (!isAdded()) return;
        String q = edtSearch.getText() != null ? edtSearch.getText().toString().trim().toLowerCase(Locale.US) : "";

        List<LabItem> filtered = new ArrayList<>();
        for (LabItem item : allItems) {
            if (!DepartmentPrefs.allows(requireContext(), item.subject)) continue;
            boolean difficultyOk = "All".equals(selectedDifficulty)
                    || (item.difficulty != null && item.difficulty.equalsIgnoreCase(selectedDifficulty));
            if (!difficultyOk) continue;

            if (!q.isEmpty()) {
                String hay = ((item.title != null ? item.title : "") + " " + (item.subject != null ? item.subject : "")).toLowerCase(Locale.US);
                if (!hay.contains(q)) continue;
            }
            filtered.add(item);
        }

        txtHeroPrimary.setText("Visible labs: " + filtered.size());
        rv.setAdapter(new ExploreLabAdapter(filtered));
    }

    private void applyHeroInsets(@NonNull View root) {
        View hero = root.findViewById(R.id.exploreHero);
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

    private List<LabItem> map(List<LabListItem> source) {
        List<LabItem> out = new ArrayList<>();
        if (source == null) return out;
        final boolean loggedIn = SessionStore.isLoggedIn();
        for (LabListItem i : source) {
            String accessLabel = i.locked ? "Login Required" : (loggedIn ? "" : "Free Preview");
            out.add(new LabItem(
                    i.id,
                    safe(i.title),
                    safe(i.subject),
                    i.duration_minutes > 0 ? (i.duration_minutes + " mins") : "",
                    safe(i.difficulty),
                    i.locked,
                    accessLabel,
                    buildImagePayload(i)
            ));
        }
        return out;
    }

    private String buildImagePayload(LabListItem item) {
        List<String> urls = new ArrayList<>();
        if (item != null) {
            if (item.images != null) {
                for (String u : item.images) {
                    String s = safe(u);
                    if (!s.isEmpty()) urls.add(s);
                }
            }
            String single = safe(item.image);
            if (!single.isEmpty() && !urls.contains(single)) urls.add(single);
        }
        return String.join("||", urls);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
