package com.virtuallab.client.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SecureStorageManager {

    private static final String PREF_NAME = "secure_sim_meta";
    private static final String KEY_LIST = "sim_list";

    private final SharedPreferences prefs;

    public SecureStorageManager(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        prefs = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public synchronized void saveMeta(SimulationMeta meta) {
        if (meta == null || TextUtils.isEmpty(meta.labId)) return;
        List<SimulationMeta> all = getAllMetas();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (meta.labId.equals(all.get(i).labId)) {
                all.set(i, meta);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(meta);
        persist(all);
    }

    public synchronized SimulationMeta getMeta(String labId) {
        for (SimulationMeta meta : getAllMetas()) {
            if (labId.equals(meta.labId)) return meta;
        }
        return null;
    }

    public synchronized List<SimulationMeta> getAllMetas() {
        List<SimulationMeta> list = new ArrayList<>();
        String raw = prefs.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                SimulationMeta m = new SimulationMeta();
                m.labId = o.optString("lab_id", "");
                m.title = o.optString("title", "");
                m.subject = o.optString("subject", "");
                m.difficulty = o.optString("difficulty", "");
                m.duration = o.optString("duration", "");
                m.version = o.optString("version", "");
                m.fileSize = o.optLong("file_size", 0L);
                m.encryptedFilePath = o.optString("encrypted_file_path", "");
                m.mainFile = o.optString("main_file", "index.html");
                m.downloadedAt = o.optLong("downloaded_at", 0L);
                m.checksum = o.optString("checksum", "");
                m.thumbnailUrl = o.optString("thumbnail_url", "");
                m.lastUpdated = o.optString("last_updated", "");
                m.overview = o.optString("overview", "");
                m.objectives = o.optString("objectives", "");
                m.materials = o.optString("materials", "");
                m.procedure = o.optString("procedure", "");
                if (!TextUtils.isEmpty(m.labId)) list.add(m);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public synchronized void deleteMeta(String labId) {
        List<SimulationMeta> all = getAllMetas();
        List<SimulationMeta> filtered = new ArrayList<>();
        for (SimulationMeta m : all) {
            if (!labId.equals(m.labId)) filtered.add(m);
        }
        persist(filtered);
    }

    public synchronized void clearAll() {
        prefs.edit().remove(KEY_LIST).apply();
    }

    private void persist(List<SimulationMeta> list) {
        JSONArray arr = new JSONArray();
        for (SimulationMeta m : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("lab_id", m.labId);
                o.put("title", m.title);
                o.put("subject", m.subject);
                o.put("difficulty", m.difficulty);
                o.put("duration", m.duration);
                o.put("version", m.version);
                o.put("file_size", m.fileSize);
                o.put("encrypted_file_path", m.encryptedFilePath);
                o.put("main_file", m.mainFile);
                o.put("downloaded_at", m.downloadedAt);
                o.put("checksum", m.checksum);
                o.put("thumbnail_url", m.thumbnailUrl);
                o.put("last_updated", m.lastUpdated);
                o.put("overview", m.overview);
                o.put("objectives", m.objectives);
                o.put("materials", m.materials);
                o.put("procedure", m.procedure);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply();
    }
}
