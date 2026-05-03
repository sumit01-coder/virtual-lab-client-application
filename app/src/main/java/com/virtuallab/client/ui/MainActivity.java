package com.virtuallab.client.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.virtuallab.client.R;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.offline.OfflineSyncManager;
import com.virtuallab.client.ui.fragment.ExploreFragment;
import com.virtuallab.client.ui.fragment.HomeFragment;
import com.virtuallab.client.ui.fragment.LabsFragment;
import com.virtuallab.client.ui.fragment.ProfileFragment;
import com.virtuallab.client.ui.fragment.ProgressFragment;
import com.virtuallab.client.util.NetworkHealthManager;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNav;
    private int pendingDepartmentId = -1;
    private String pendingDepartmentName = "";
    private String pendingExploreQuery = "";
    private boolean networkPanelOpening = false;

    public void openLabsForDepartment(int departmentId, String departmentName) {
        pendingDepartmentId = departmentId;
        pendingDepartmentName = departmentName != null ? departmentName : "";
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_labs);
        }
    }

    public void openExploreWithQuery(String query) {
        pendingExploreQuery = query != null ? query : "";
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_explore);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment;
            if (id == R.id.nav_explore) {
                fragment = ExploreFragment.newInstance(pendingExploreQuery);
                pendingExploreQuery = "";
            } else if (id == R.id.nav_labs) {
                if (pendingDepartmentId > 0) {
                    fragment = LabsFragment.newInstance(pendingDepartmentId, pendingDepartmentName);
                    pendingDepartmentId = -1;
                    pendingDepartmentName = "";
                } else {
                    fragment = new LabsFragment();
                }
            } else if (id == R.id.nav_progress) {
                if (!SessionStore.isLoggedIn()) {
                    Toast.makeText(this, "Login required for Progress", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    return false;
                }
                fragment = new ProgressFragment();
            } else if (id == R.id.nav_profile) {
                if (!SessionStore.isLoggedIn()) {
                    Toast.makeText(this, "Login required for Profile", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    return false;
                }
                fragment = new ProfileFragment();
            } else {
                fragment = new HomeFragment();
            }

            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_slide_in, R.anim.fade_out)
                    .replace(R.id.mainContainer, fragment)
                    .commit();
            return true;
        });

        checkNetworkBeforeServerWork();
    }

    private void startOnlineExperience() {
        String tab = getIntent().getStringExtra("tab");
        pendingExploreQuery = getIntent().getStringExtra("query");
        if ("explore".equals(tab)) {
            bottomNav.setSelectedItemId(R.id.nav_explore);
        } else if ("labs".equals(tab)) {
            bottomNav.setSelectedItemId(R.id.nav_labs);
        } else if ("progress".equals(tab)) {
            bottomNav.setSelectedItemId(R.id.nav_progress);
        } else if ("profile".equals(tab)) {
            bottomNav.setSelectedItemId(R.id.nav_profile);
        } else {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
        // Auto-detect newer simulation packages on server and sync downloaded offline labs.
        try {
            OfflineSyncManager syncManager = new OfflineSyncManager(this);
            syncManager.syncDownloadedSimulations(false, message ->
                    runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show()));
        } catch (Exception ignored) {
        }
    }

    private void checkNetworkBeforeServerWork() {
        NetworkHealthManager.checkAsync(this, result -> {
            if (isFinishing()) return;
            if (result.isGoodForServerData()) {
                startOnlineExperience();
                return;
            }
            if (networkPanelOpening) return;
            networkPanelOpening = true;
            Intent i = new Intent(this, NetworkStatusActivity.class);
            i.putExtra(NetworkStatusActivity.EXTRA_NEXT_SCREEN, NetworkStatusActivity.NEXT_MAIN);
            startActivity(i);
            finish();
        });
    }
}

