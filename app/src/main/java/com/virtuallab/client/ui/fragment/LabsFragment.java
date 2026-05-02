package com.virtuallab.client.ui.fragment;

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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.CatalogPayload;
import com.virtuallab.client.api.dto.DepartmentTreeItem;
import com.virtuallab.client.api.dto.LabListItem;
import com.virtuallab.client.data.DepartmentPrefs;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.model.LabItem;
import com.virtuallab.client.ui.adapter.ExploreLabAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LabsFragment extends Fragment {
    private static final String ARG_DEPT_ID = "dept_id";
    private static final String ARG_DEPT_NAME = "dept_name";

    private RecyclerView rv;
    private TextView txtTitle;
    private TextView txtSubtitle;
    private TextView txtHeroPrimary;
    private TextView txtHeroSecondary;
    private LinearLayout chipDeptFilter;
    private int filterDepartmentId = -1;
    private String filterDepartmentName = "";
    private List<LabItem> allItems = new ArrayList<>();
    private final Map<Integer, String> departments = new LinkedHashMap<>();
    private long prefsVersionSeen = -1L;

    public static LabsFragment newInstance(int departmentId, String departmentName) {
        LabsFragment f = new LabsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_DEPT_ID, departmentId);
        args.putString(ARG_DEPT_NAME, departmentName);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_labs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        txtTitle = view.findViewById(R.id.txtLabsTitle);
        txtSubtitle = view.findViewById(R.id.txtLabsSubtitle);
        txtHeroPrimary = view.findViewById(R.id.txtLabsHeroPrimary);
        txtHeroSecondary = view.findViewById(R.id.txtLabsHeroSecondary);
        chipDeptFilter = view.findViewById(R.id.chipDeptFilter);
        rv = view.findViewById(R.id.rvLabs);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        applyHeroInsets(view);

        Bundle args = getArguments();
        if (args != null) {
            filterDepartmentId = args.getInt(ARG_DEPT_ID, -1);
            filterDepartmentName = args.getString(ARG_DEPT_NAME, "");
        }
        if (filterDepartmentId > 0 && filterDepartmentName != null && !filterDepartmentName.trim().isEmpty()) {
            txtTitle.setText(filterDepartmentName + " Labs");
            txtSubtitle.setText("Focused practicals for " + filterDepartmentName + ".");
            txtHeroSecondary.setText(toDisplayCase(filterDepartmentName));
        } else {
            txtTitle.setText("All Labs");
            txtSubtitle.setText("Browse every available lab with richer visual cards.");
            txtHeroSecondary.setText("All departments");
        }
        txtHeroPrimary.setText("Visible labs: 0");

        loadDepartments();
        loadLabs();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) return;
        long currentVersion = DepartmentPrefs.getVersion(requireContext());
        if (currentVersion != prefsVersionSeen) {
            prefsVersionSeen = currentVersion;
            buildFilterChips();
            applyFilter();
        }
    }

    private void loadDepartments() {
        ApiClient.get().departmentsTree().enqueue(new Callback<ApiEnvelope<CatalogPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<CatalogPayload>> call, Response<ApiEnvelope<CatalogPayload>> response) {
                ApiEnvelope<CatalogPayload> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null || env.data.departments == null) return;

                departments.clear();
                for (DepartmentTreeItem dept : env.data.departments) {
                    departments.put(dept.id, dept.name != null ? dept.name : "General");
                }

                buildFilterChips();
                applyFilter();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<CatalogPayload>> call, Throwable t) {
                if (isAdded()) rv.setAdapter(new ExploreLabAdapter(new ArrayList<>()));
            }
        });
    }

    private void loadLabs() {
        ApiClient.get().explore("", "", "").enqueue(new Callback<ApiEnvelope<List<LabListItem>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<LabListItem>>> call, Response<ApiEnvelope<List<LabListItem>>> response) {
                ApiEnvelope<List<LabListItem>> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null) return;

                allItems.clear();
                final boolean loggedIn = SessionStore.isLoggedIn();
                for (LabListItem i : env.data) {
                    String accessLabel = i.locked ? "Login Required" : (loggedIn ? "" : "Free Preview");
                    allItems.add(new LabItem(
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
                applyFilter();
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<LabListItem>>> call, Throwable t) {
                if (!isAdded()) return;
                allItems.clear();
                applyFilter();
            }
        });
    }

    private void buildFilterChips() {
        if (!isAdded()) return;
        chipDeptFilter.removeAllViews();

        chipDeptFilter.addView(makeChip("All", -1, filterDepartmentId <= 0));
        for (Map.Entry<Integer, String> e : departments.entrySet()) {
            if (!DepartmentPrefs.allows(requireContext(), e.getValue())) continue;
            chipDeptFilter.addView(makeChip(e.getValue(), e.getKey(), filterDepartmentId == e.getKey()));
        }
    }

    private TextView makeChip(String label, int deptId, boolean selected) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextSize(13f);
        chip.setPadding(dp(16), dp(8), dp(16), dp(8));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);

        applyChipStyle(chip, selected);
        chip.setOnClickListener(v -> {
            filterDepartmentId = deptId;
            filterDepartmentName = deptId > 0 ? (departments.get(deptId) != null ? departments.get(deptId) : "") : "";
            buildFilterChips();
            applyFilter();
        });

        return chip;
    }

    private void applyChipStyle(TextView chip, boolean selected) {
        if (selected) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected);
            chip.setTextColor(requireContext().getColor(R.color.white));
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_soft);
            chip.setTextColor(requireContext().getColor(R.color.vl_primary));
        }
    }

    private void applyFilter() {
        if (!isAdded()) return;

        if (filterDepartmentId > 0) {
            String name = departments.get(filterDepartmentId);
            if (name != null && !DepartmentPrefs.allows(requireContext(), name)) {
                filterDepartmentId = -1;
                filterDepartmentName = "";
                name = null;
            }
            if (name == null || name.trim().isEmpty()) name = filterDepartmentName;
            if (name == null || name.trim().isEmpty()) name = "Department";
            String displayName = toDisplayCase(name);
            txtTitle.setText(displayName + " Labs");
            txtSubtitle.setText("Focused practicals for " + displayName + ".");
            txtHeroSecondary.setText(displayName);
        } else {
            txtTitle.setText("All Labs");
            txtSubtitle.setText("Browse every available lab with richer visual cards.");
            txtHeroSecondary.setText("All departments");
        }

        if (allItems.isEmpty()) {
            txtHeroPrimary.setText("Visible labs: 0");
            rv.setAdapter(new ExploreLabAdapter(new ArrayList<>()));
            return;
        }

        List<LabItem> filtered = new ArrayList<>();
        if (filterDepartmentId <= 0) {
            for (LabItem i : allItems) {
                if (DepartmentPrefs.allows(requireContext(), i.subject)) filtered.add(i);
            }
        } else {
            String deptName = departments.get(filterDepartmentId);
            if (deptName == null) deptName = filterDepartmentName;
            for (LabItem item : allItems) {
                if (deptName != null && deptName.equalsIgnoreCase(item.subject)
                        && DepartmentPrefs.allows(requireContext(), item.subject)) {
                    filtered.add(item);
                }
            }
        }

        txtHeroPrimary.setText("Visible labs: " + filtered.size());
        rv.setAdapter(new ExploreLabAdapter(filtered));
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private void applyHeroInsets(@NonNull View root) {
        View hero = root.findViewById(R.id.labsHero);
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
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
}
