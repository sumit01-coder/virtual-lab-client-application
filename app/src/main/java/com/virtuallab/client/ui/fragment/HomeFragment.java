package com.virtuallab.client.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.CatalogPayload;
import com.virtuallab.client.api.dto.DepartmentTreeItem;
import com.virtuallab.client.api.dto.HomePayload;
import com.virtuallab.client.api.dto.LabListItem;
import com.virtuallab.client.api.dto.ProfilePayload;
import com.virtuallab.client.data.DepartmentPrefs;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.model.LabItem;
import com.virtuallab.client.ui.LabDetailsActivity;
import com.virtuallab.client.ui.LabTutorActivity;
import com.virtuallab.client.ui.MainActivity;
import com.virtuallab.client.ui.OfflineLabsActivity;
import com.virtuallab.client.ui.adapter.LabCardAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {
    private static final long EXPLORE_IMAGE_CACHE_TTL_MS = 5L * 60L * 1000L;

    private TextView txtGreeting;
    private TextView txtStudentName;
    private LinearLayout chipContainer;
    private LinearLayout departmentCardContainer;
    private RecyclerView featured;

    private RecyclerView rvContinueLearning;
    private TextView txtContinueCount;
    private TextView txtContinueEmpty;
    private TextView btnContinueProgress;
    private View cardContinue;

    private View txtViewAllDepartments;
    private TextView txtViewAllFeatured;
    private TextView txtViewAllContinue;

    private TextView txtUsersFeatured;
    private TextView txtTopStatLabel;
    private TextView txtUsersTrendFeatured;
    private TextView txtStreak;
    private TextView txtStatsBadge;
    private TextView txtSystemServer;
    private TextView txtSystemApi;
    private TextView txtSystemCatalog;
    private EditText edtSearch;
    private View btnVoiceSearch;
    private View btnAiTutor;
    private View actionExplore;
    private View actionDownloads;
    private View actionProgress;
    private View actionProfile;

    private LabItem continueItem;
    private ContinueLearningAdapter continueLearningAdapter;
    private long prefsVersionSeen = -1L;
    private final Map<Integer, String> exploreImageCache = new HashMap<>();
    private long exploreImageCacheUpdatedAt = 0L;

    private final ActivityResultLauncher<Intent> voiceSearchLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches == null || matches.isEmpty()) return;
                String query = matches.get(0);
                edtSearch.setText(query);
                edtSearch.setSelection(edtSearch.length());
                openExploreSearch(query);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtGreeting = view.findViewById(R.id.txtGreeting);
        txtStudentName = view.findViewById(R.id.txtStudentName);
        chipContainer = view.findViewById(R.id.chipContainer);
        departmentCardContainer = view.findViewById(R.id.departmentCardContainer);
        featured = view.findViewById(R.id.rvFeatured);

        rvContinueLearning = view.findViewById(R.id.rvContinueLearning);
        txtContinueCount = view.findViewById(R.id.txtContinueCount);
        txtContinueEmpty = view.findViewById(R.id.txtContinueEmpty);
        btnContinueProgress = view.findViewById(R.id.btnContinueProgress);
        cardContinue = view.findViewById(R.id.cardContinue);

        txtViewAllDepartments = view.findViewById(R.id.txtViewAllDepartments);
        txtViewAllFeatured = view.findViewById(R.id.txtViewAllFeatured);
        txtViewAllContinue = view.findViewById(R.id.txtViewAllContinue);

        txtUsersFeatured = view.findViewById(R.id.txtUsersFeatured);
        txtTopStatLabel = view.findViewById(R.id.txtTopStatLabel);
        txtUsersTrendFeatured = view.findViewById(R.id.txtUsersTrendFeatured);
        txtStreak = view.findViewById(R.id.txtStreak);
        txtStatsBadge = view.findViewById(R.id.txtStatsBadge);
        txtSystemServer = view.findViewById(R.id.txtSystemServer);
        txtSystemApi = view.findViewById(R.id.txtSystemApi);
        txtSystemCatalog = view.findViewById(R.id.txtSystemCatalog);
        edtSearch = view.findViewById(R.id.edtSearch);
        btnVoiceSearch = view.findViewById(R.id.btnVoiceSearch);
        btnAiTutor = view.findViewById(R.id.btnAiTutor);
        actionExplore = view.findViewById(R.id.actionExplore);
        actionDownloads = view.findViewById(R.id.actionDownloads);
        actionProgress = view.findViewById(R.id.actionProgress);
        actionProfile = view.findViewById(R.id.actionProfile);

        featured.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvContinueLearning.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        SnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rvContinueLearning);
        continueLearningAdapter = new ContinueLearningAdapter(new ArrayList<>(), 0, this::openContinueItem);
        rvContinueLearning.setAdapter(continueLearningAdapter);

        txtViewAllDepartments.setOnClickListener(v -> openProfileTab());
        txtViewAllFeatured.setOnClickListener(v -> openExploreTab());
        txtViewAllContinue.setOnClickListener(v -> openProgressTab());
        cardContinue.setOnClickListener(v -> openContinueItem());
        btnContinueProgress.setOnClickListener(v -> openProgressTab());
        actionExplore.setOnClickListener(v -> openExploreTab());
        actionDownloads.setOnClickListener(v -> openOfflineDownloads());
        actionProgress.setOnClickListener(v -> openProgressTab());
        actionProfile.setOnClickListener(v -> openProfileTab());
        btnVoiceSearch.setOnClickListener(v -> startVoiceSearch());
        btnAiTutor.setOnClickListener(v -> openAiTutor());
        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                openExploreSearch(edtSearch.getText() != null ? edtSearch.getText().toString() : "");
                return true;
            }
            return false;
        });

        loadHome();
        loadProfile();
        loadDepartmentsChips();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) return;
        long currentVersion = DepartmentPrefs.getVersion(requireContext());
        if (currentVersion != prefsVersionSeen) {
            prefsVersionSeen = currentVersion;
            loadHome();
            loadDepartmentsChips();
        }
    }

    private void loadHome() {
        ApiClient.get().home().enqueue(new Callback<ApiEnvelope<HomePayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<HomePayload>> call, Response<ApiEnvelope<HomePayload>> response) {
                ApiEnvelope<HomePayload> env = response.body();
                if (!isAdded()) return;
                if (env == null || !env.status || env.data == null) {
                    featured.setAdapter(new LabCardAdapter(new ArrayList<>()));
                    bindContinue(new ArrayList<>());
                    applyHomeFallback();
                    String message = responseMessage(response, "Home data not available. Please refresh or login again.");
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    return;
                }

                updateGreeting(env.data.greeting);

                List<LabItem> featuredItems = mapLabList(env.data.featured);
                List<LabItem> continueItems = mapLabList(env.data.continue_learning);

                HomePayload.Stats stats = env.data.stats;
                updateDashboardStats(stats, featuredItems.size());

                enrichImagesFromExplore(featuredItems, continueItems, (featuredEnriched, continueEnriched) -> {
                    if (!isAdded()) return;
                    List<LabItem> featuredDisplay = featuredEnriched;
                    if ((featuredDisplay == null || featuredDisplay.isEmpty()) && featuredItems != null && !featuredItems.isEmpty()) {
                        featuredDisplay = featuredItems;
                    }
                    featured.setAdapter(new LabCardAdapter(featuredDisplay));
                    int pctFromStats = (stats != null && stats.practicals > 0)
                            ? Math.max(0, Math.min(100, (stats.completed_practicals * 100) / stats.practicals))
                            : 0;
                    bindContinue(continueEnriched, pctFromStats);
                });
            }

            @Override
            public void onFailure(Call<ApiEnvelope<HomePayload>> call, Throwable t) {
                if (isAdded()) {
                    featured.setAdapter(new LabCardAdapter(new ArrayList<>()));
                    bindContinue(new ArrayList<>());
                    applyHomeFallback();
                    Toast.makeText(requireContext(), "Home data failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private String responseMessage(Response<?> response, String fallback) {
        if (response == null) return fallback;
        Object body = response.body();
        if (body instanceof ApiEnvelope) {
            String message = ((ApiEnvelope<?>) body).message;
            if (message != null && !message.trim().isEmpty()) {
                return message.trim();
            }
        }
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                String message = extractJsonMessage(raw);
                if (!message.isEmpty()) {
                    return message;
                }
                if (raw != null && !raw.trim().isEmpty()) {
                    return "Home data failed (HTTP " + response.code() + ")";
                }
            }
        } catch (Exception ignored) {
        }
        if (!response.isSuccessful()) {
            return "Home data failed (HTTP " + response.code() + ")";
        }
        return fallback;
    }

    private String extractJsonMessage(String raw) {
        if (raw == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"message\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(raw);
        if (!matcher.find()) return "";
        return matcher.group(1)
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .trim();
    }

    private void updateGreeting(String greeting) {
        if (greeting == null || greeting.trim().isEmpty()) return;
        String cleaned = greeting.replace("Hello", "Hi").trim();
        txtGreeting.setText(cleaned.endsWith(",") ? cleaned : (cleaned + ","));
    }

    private void updateDashboardStats(HomePayload.Stats stats, int featuredFallbackCount) {
        int labsCount = stats != null ? stats.labs : featuredFallbackCount;
        int practicalCount = stats != null ? stats.practicals : 0;
        int completedCount = stats != null ? stats.completed_practicals : 0;
        int departmentsCount = stats != null ? stats.departments : 0;
        int streakDays = stats != null ? stats.streak_days : 0;
        int selectedDepartments = 0;
        if (isAdded()) {
            selectedDepartments = DepartmentPrefs.getSelectedSubjects(requireContext()).size();
        }

        txtTopStatLabel.setText("Labs");
        txtUsersFeatured.setText(String.valueOf(labsCount));
        txtUsersTrendFeatured.setText(completedCount > 0
                ? (completedCount + " practicals completed")
                : "Start your first practical");
        txtStatsBadge.setText(streakDays > 0 ? (streakDays + " day streak") : "For you");
        if (streakDays > 0) {
            txtStreak.setText(streakDays + " day learning streak");
        } else {
            txtStreak.setText("Keep exploring to build your streak");
        }
        txtSystemServer.setText("Selected: " + (selectedDepartments > 0 ? selectedDepartments : departmentsCount));
        txtSystemApi.setText("Available: " + labsCount);
        txtSystemCatalog.setText("Completed: " + completedCount + "/" + practicalCount);
    }

    private void applyHomeFallback() {
        txtTopStatLabel.setText("Labs");
        txtUsersFeatured.setText("--");
        txtUsersTrendFeatured.setText("Unable to load your dashboard");
        txtStatsBadge.setText("For you");
        txtStreak.setText("Learning progress");
        txtSystemServer.setText("Selected: --");
        txtSystemApi.setText("Available: --");
        txtSystemCatalog.setText("Completed: --");
    }

    private void loadProfile() {
        ApiClient.get().profile().enqueue(new Callback<ApiEnvelope<ProfilePayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<ProfilePayload>> call, Response<ApiEnvelope<ProfilePayload>> response) {
                ApiEnvelope<ProfilePayload> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null) return;
                String name = safe(env.data.full_name);
                if (name.isEmpty()) name = safe(env.data.username);
                if (!name.isEmpty()) {
                    txtStudentName.setText(toDisplayName(name));
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<ProfilePayload>> call, Throwable t) {
                // Keep current fallback.
            }
        });
    }

    private void loadDepartmentsChips() {
        ApiClient.get().departmentsTree().enqueue(new Callback<ApiEnvelope<CatalogPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<CatalogPayload>> call, Response<ApiEnvelope<CatalogPayload>> response) {
                ApiEnvelope<CatalogPayload> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null || env.data.departments == null || env.data.departments.isEmpty()) return;

                chipContainer.removeAllViews();
                departmentCardContainer.removeAllViews();
                int visibleIndex = 0;
                for (int i = 0; i < env.data.departments.size(); i++) {
                    DepartmentTreeItem d = env.data.departments.get(i);
                    if (!DepartmentPrefs.allows(requireContext(), d.name)) continue;

                    TextView chip = new TextView(requireContext());
                    chip.setText(toDisplayName(d.name != null ? d.name : ""));
                    chip.setTextSize(14f);
                    chip.setTypeface(Typeface.DEFAULT_BOLD);
                    boolean active = visibleIndex == 0;
                    chip.setTextColor(active ? requireContext().getColor(android.R.color.white) : requireContext().getColor(R.color.vl_primary));
                    chip.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip_soft);
                    chip.setPadding(dp(16), dp(9), dp(16), dp(9));

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    if (i > 0) lp.setMarginStart(dp(8));
                    chip.setLayoutParams(lp);
                    chip.setOnClickListener(v -> openDepartmentLabs(d));
                    chipContainer.addView(chip);

                    departmentCardContainer.addView(createDepartmentCard(d, i));
                    visibleIndex++;
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<CatalogPayload>> call, Throwable t) {
                // Keep current UI; no static fallback data.
            }
        });
    }

    private int dp(int v) {
        return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
    }

    private View createDepartmentCard(DepartmentTreeItem d, int index) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card_premium);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setElevation(dp(6));
        card.setMinimumHeight(dp(210));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(248), LinearLayout.LayoutParams.WRAP_CONTENT);
        if (index > 0) lp.setMarginStart(dp(10));
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);

        TextView icon = new TextView(requireContext());
        icon.setText(makeDepartmentMonogram(d != null ? d.name : ""));
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundResource(R.drawable.bg_nav_active);
        icon.setTextColor(requireContext().getColor(R.color.vl_primary));
        icon.setTextSize(12f);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        icon.setLayoutParams(iconLp);

        TextView title = new TextView(requireContext());
        title.setText(d.name != null ? d.name : "");
        title.setText(toDisplayName(d.name != null ? d.name : ""));
        title.setTextColor(requireContext().getColor(R.color.vl_text));
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(14), 0, 0);

        TextView meta = new TextView(requireContext());
        int labsCount = d.labs != null ? d.labs.size() : 0;
        meta.setText(labsCount + " Labs");
        meta.setTextColor(requireContext().getColor(R.color.vl_primary));
        meta.setTextSize(13f);
        meta.setTypeface(Typeface.DEFAULT_BOLD);
        meta.setPadding(0, dp(4), 0, 0);

        TextView desc = new TextView(requireContext());
        String description = d.description != null ? d.description.trim() : "";
        desc.setText(description);
        desc.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
        desc.setTextColor(requireContext().getColor(R.color.vl_text_secondary));
        desc.setTextSize(12f);
        desc.setLineSpacing(0f, 1.15f);
        desc.setMaxLines(4);
        desc.setPadding(0, dp(8), 0, 0);

        View spacer = new View(requireContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout footer = new LinearLayout(requireContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(14), 0, 0);

        TextView cta = new TextView(requireContext());
        cta.setText("Explore Labs");
        cta.setTextColor(requireContext().getColor(R.color.vl_primary));
        cta.setTextSize(12f);
        cta.setTypeface(Typeface.DEFAULT_BOLD);

        TextView arrow = new TextView(requireContext());
        arrow.setText(">");
        arrow.setGravity(Gravity.CENTER);
        arrow.setBackgroundResource(R.drawable.bg_nav_active);
        arrow.setTextColor(requireContext().getColor(R.color.vl_primary));
        arrow.setTextSize(12f);
        arrow.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        arrowLp.leftMargin = dp(10);
        arrow.setLayoutParams(arrowLp);

        footer.addView(cta);
        footer.addView(arrow);

        card.addView(icon);
        card.addView(title);
        card.addView(meta);
        card.addView(desc);
        card.addView(spacer);
        card.addView(footer);

        card.setOnClickListener(v -> openDepartmentLabs(d));
        return card;
    }

    private String makeDepartmentMonogram(String name) {
        if (name == null || name.trim().isEmpty()) return "LB";
        String[] parts = name.trim().split("\\s+");
        StringBuilder monogram = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            monogram.append(Character.toUpperCase(part.charAt(0)));
            if (monogram.length() == 2) break;
        }
        if (monogram.length() == 0) return "LB";
        if (monogram.length() == 1) monogram.append('B');
        return monogram.toString();
    }

    private void openDepartmentLabs(DepartmentTreeItem d) {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) return;
        String name = d != null && d.name != null ? d.name : "";
        int id = d != null ? d.id : -1;
        ((MainActivity) requireActivity()).openLabsForDepartment(id, name);
    }

    private void openExploreTab() {
        View nav = requireActivity().findViewById(R.id.bottomNav);
        if (nav instanceof BottomNavigationView) {
            ((BottomNavigationView) nav).setSelectedItemId(R.id.nav_explore);
        }
    }

    private void openExploreSearch(String query) {
        if (!isAdded()) return;
        String q = query == null ? "" : query.trim();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).openExploreWithQuery(q);
        }
    }

    private void openAiTutor() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireContext(), LabTutorActivity.class);
        String question = edtSearch.getText() != null ? edtSearch.getText().toString().trim() : "";
        if (!question.isEmpty()) {
            intent.putExtra(LabTutorActivity.EXTRA_INITIAL_QUESTION, question);
        }
        startActivity(intent);
    }

    private void startVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Search labs or ask the AI tutor");
        try {
            voiceSearchLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Voice search is not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void openLabsTab() {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) return;
        ((MainActivity) requireActivity()).openLabsForDepartment(-1, "");
    }

    private void openProgressTab() {
        View nav = requireActivity().findViewById(R.id.bottomNav);
        if (nav instanceof BottomNavigationView) {
            ((BottomNavigationView) nav).setSelectedItemId(R.id.nav_progress);
        }
    }

    private void openProfileTab() {
        View nav = requireActivity().findViewById(R.id.bottomNav);
        if (nav instanceof BottomNavigationView) {
            ((BottomNavigationView) nav).setSelectedItemId(R.id.nav_profile);
        }
    }

    private void openOfflineDownloads() {
        if (!isAdded()) return;
        startActivity(new Intent(requireContext(), OfflineLabsActivity.class));
    }

    private void bindContinue(List<LabItem> continueItems) {
        bindContinue(continueItems, 0);
    }

    private void bindContinue(List<LabItem> continueItems, int pctHint) {
        if (!isAdded()) return;

        if (continueItems == null || continueItems.isEmpty()) {
            continueItem = null;
            txtContinueCount.setText("0 pending practicals");
            rvContinueLearning.setVisibility(View.GONE);
            txtContinueEmpty.setVisibility(View.VISIBLE);
            continueLearningAdapter.update(new ArrayList<>(), 0);
            return;
        }

        continueItem = continueItems.get(0);
        int count = continueItems.size();
        int pct = Math.max(1, Math.min(100, pctHint > 0 ? pctHint : 1));
        txtContinueCount.setText(count == 1
                ? "1 pending practical in your track"
                : count + " pending practicals in your track");
        rvContinueLearning.setVisibility(View.VISIBLE);
        txtContinueEmpty.setVisibility(View.GONE);
        continueLearningAdapter.update(continueItems, pct);
    }

    private void openContinueItem() {
        if (!isAdded()) return;
        if (continueItem == null) {
            openLabsTab();
            return;
        }
        if (continueItem.locked) {
            Toast.makeText(requireContext(), "Login to access this practical", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(requireContext(), LabDetailsActivity.class);
        intent.putExtra("id", continueItem.id);
        intent.putExtra("title", continueItem.title);
        intent.putExtra("meta", continueItem.meta());
        startActivity(intent);
    }

    private void openContinueItem(LabItem item) {
        continueItem = item;
        openContinueItem();
    }

    private List<LabItem> mapLabList(List<LabListItem> source) {
        List<LabItem> out = new ArrayList<>();
        if (source == null) return out;
        final boolean loggedIn = SessionStore.isLoggedIn();
        for (LabListItem i : source) {
            String title = safe(i.title);
            String subject = safe(i.subject);
            String difficulty = safe(i.difficulty);
            String duration = i.duration_minutes > 0 ? (i.duration_minutes + " mins") : "";
            String accessLabel = i.locked ? "Login Required" : (loggedIn ? "" : "Free Preview");
            out.add(new LabItem(
                    i.id,
                    title,
                    subject,
                    duration,
                    difficulty,
                    i.locked,
                    accessLabel,
                    buildImagePayload(i)
            ));
        }
        return out;
    }

    private void enrichImagesFromExplore(List<LabItem> featuredItems, List<LabItem> continueItems, EnrichCallback callback) {
        if (!needsImageEnrichment(featuredItems, continueItems)) {
            callback.onDone(featuredItems, continueItems);
            return;
        }

        if (!exploreImageCache.isEmpty() && (System.currentTimeMillis() - exploreImageCacheUpdatedAt) < EXPLORE_IMAGE_CACHE_TTL_MS) {
            callback.onDone(withImages(featuredItems, exploreImageCache), withImages(continueItems, exploreImageCache));
            return;
        }

        ApiClient.get().explore("", "", "").enqueue(new Callback<ApiEnvelope<List<LabListItem>>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<List<LabListItem>>> call, Response<ApiEnvelope<List<LabListItem>>> response) {
                ApiEnvelope<List<LabListItem>> env = response.body();
                if (!isAdded() || env == null || !env.status || env.data == null) {
                    callback.onDone(featuredItems, continueItems);
                    return;
                }

                Map<Integer, String> imageById = new HashMap<>();
                for (LabListItem item : env.data) {
                    imageById.put(item.id, buildImagePayload(item));
                }
                exploreImageCache.clear();
                exploreImageCache.putAll(imageById);
                exploreImageCacheUpdatedAt = System.currentTimeMillis();
                callback.onDone(withImages(featuredItems, imageById), withImages(continueItems, imageById));
            }

            @Override
            public void onFailure(Call<ApiEnvelope<List<LabListItem>>> call, Throwable t) {
                callback.onDone(featuredItems, continueItems);
            }
        });
    }

    private boolean needsImageEnrichment(List<LabItem> featuredItems, List<LabItem> continueItems) {
        for (LabItem item : featuredItems) {
            if (item.imageUrl == null || item.imageUrl.trim().isEmpty()) return true;
        }
        for (LabItem item : continueItems) {
            if (item.imageUrl == null || item.imageUrl.trim().isEmpty()) return true;
        }
        return false;
    }

    private List<LabItem> withImages(List<LabItem> source, Map<Integer, String> imageById) {
        List<LabItem> out = new ArrayList<>();
        List<LabItem> fallback = new ArrayList<>();
        for (LabItem item : source) {
            String imagePayload = item.imageUrl;
            if ((imagePayload == null || imagePayload.trim().isEmpty()) && imageById.containsKey(item.id)) {
                imagePayload = imageById.get(item.id);
            }
            LabItem enriched = new LabItem(
                    item.id,
                    item.title,
                    item.subject,
                    item.duration,
                    item.difficulty,
                    item.locked,
                    item.accessLabel,
                    imagePayload
            );
            fallback.add(enriched);
            if (!isAdded() || DepartmentPrefs.allows(requireContext(), item.subject)) {
                out.add(enriched);
            }
        }
        if (out.isEmpty() && !fallback.isEmpty()) {
            return fallback;
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

    private String toDisplayName(String raw) {
        String[] parts = raw.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            if (p.length() == 1) {
                out.append(Character.toUpperCase(p.charAt(0)));
            } else {
                out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
            }
        }
        return out.toString();
    }

    private interface EnrichCallback {
        void onDone(List<LabItem> featuredEnriched, List<LabItem> continueEnriched);
    }

    private class ContinueLearningAdapter extends RecyclerView.Adapter<ContinueLearningAdapter.VH> {
        private final List<LabItem> items = new ArrayList<>();
        private final ContinueClickListener listener;
        private int progressPct;

        ContinueLearningAdapter(List<LabItem> initialItems, int progressPct, ContinueClickListener listener) {
            this.listener = listener;
            update(initialItems, progressPct);
        }

        void update(List<LabItem> nextItems, int nextProgressPct) {
            items.clear();
            if (nextItems != null) {
                items.addAll(nextItems);
            }
            progressPct = Math.max(0, Math.min(100, nextProgressPct));
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundResource(R.drawable.bg_card_surface);
            root.setPadding(dp(14), dp(14), dp(14), dp(14));
            RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                    Math.max(dp(268), parent.getResources().getDisplayMetrics().widthPixels - dp(76)),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rootLp.setMarginEnd(dp(10));
            root.setLayoutParams(rootLp);

            LinearLayout topRow = new LinearLayout(parent.getContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView monogram = new TextView(parent.getContext());
            monogram.setGravity(Gravity.CENTER);
            monogram.setBackgroundResource(R.drawable.bg_nav_active);
            monogram.setTextColor(parent.getContext().getColor(R.color.vl_primary));
            monogram.setTextSize(12f);
            monogram.setTypeface(Typeface.DEFAULT_BOLD);
            topRow.addView(monogram, new LinearLayout.LayoutParams(dp(38), dp(38)));

            TextView badge = new TextView(parent.getContext());
            badge.setGravity(Gravity.CENTER);
            badge.setBackgroundResource(R.drawable.bg_badge_soft_pill);
            badge.setTextColor(parent.getContext().getColor(R.color.vl_primary));
            badge.setTextSize(11f);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setPadding(dp(10), 0, dp(10), 0);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(30)
            );
            badgeLp.setMarginStart(dp(10));
            topRow.addView(badge, badgeLp);

            TextView title = new TextView(parent.getContext());
            title.setTextColor(parent.getContext().getColor(R.color.vl_text));
            title.setTextSize(16f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(0, dp(12), 0, 0);

            TextView meta = new TextView(parent.getContext());
            meta.setTextColor(parent.getContext().getColor(R.color.vl_text_muted));
            meta.setTextSize(12f);
            meta.setMaxLines(1);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            meta.setPadding(0, dp(4), 0, 0);

            LinearLayout progressRow = new LinearLayout(parent.getContext());
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            progressRow.setOrientation(LinearLayout.HORIZONTAL);
            progressRow.setPadding(0, dp(12), 0, 0);

            ProgressBar progressBar = new ProgressBar(parent.getContext(), null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgressDrawable(parent.getContext().getDrawable(R.drawable.bg_progress_home));
            LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(0, dp(6), 1f);
            progressRow.addView(progressBar, progressLp);

            TextView progressText = new TextView(parent.getContext());
            progressText.setTextColor(parent.getContext().getColor(R.color.vl_text_muted));
            progressText.setTextSize(11f);
            progressText.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams progressTextLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            progressTextLp.setMarginStart(dp(8));
            progressRow.addView(progressText, progressTextLp);

            TextView cta = new TextView(parent.getContext());
            cta.setText("Resume practical");
            cta.setTextColor(parent.getContext().getColor(R.color.vl_primary));
            cta.setTextSize(12f);
            cta.setTypeface(Typeface.DEFAULT_BOLD);
            cta.setPadding(0, dp(10), 0, 0);

            root.addView(topRow);
            root.addView(title);
            root.addView(meta);
            root.addView(progressRow);
            root.addView(cta);
            return new VH(root, monogram, badge, title, meta, progressBar, progressText);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            LabItem item = items.get(position);
            holder.monogram.setText(makeDepartmentMonogram(item.subject));
            holder.badge.setText("Pending " + (position + 1) + "/" + items.size());
            holder.title.setText(toDisplayName(item.title));
            String meta = item.meta();
            holder.meta.setText(meta.isEmpty() ? "Ready to continue" : meta);
            holder.progressBar.setProgress(progressPct);
            holder.progressText.setText(progressPct + "%");
            holder.itemView.setOnClickListener(v -> listener.onContinueClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView monogram;
            final TextView badge;
            final TextView title;
            final TextView meta;
            final ProgressBar progressBar;
            final TextView progressText;

            VH(
                    @NonNull View itemView,
                    TextView monogram,
                    TextView badge,
                    TextView title,
                    TextView meta,
                    ProgressBar progressBar,
                    TextView progressText
            ) {
                super(itemView);
                this.monogram = monogram;
                this.badge = badge;
                this.title = title;
                this.meta = meta;
                this.progressBar = progressBar;
                this.progressText = progressText;
            }
        }
    }

    private interface ContinueClickListener {
        void onContinueClick(LabItem item);
    }
}
