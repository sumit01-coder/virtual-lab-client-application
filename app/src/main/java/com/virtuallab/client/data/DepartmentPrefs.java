package com.virtuallab.client.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class DepartmentPrefs {
    private static final String PREF = "user_prefs";
    private static final String KEY_SUBJECTS = "preferred_subjects";
    private static final String KEY_VERSION = "preferred_subjects_version";

    private DepartmentPrefs() {}

    public static Set<String> getSelectedSubjects(Context context) {
        if (context == null) return Collections.emptySet();
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> raw = sp.getStringSet(KEY_SUBJECTS, Collections.emptySet());
        return new HashSet<>(raw);
    }

    public static void setSelectedSubjects(Context context, Set<String> subjects) {
        if (context == null) return;
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> normalized = new HashSet<>();
        if (subjects != null) {
            for (String s : subjects) {
                String n = normalize(s);
                if (!n.isEmpty()) normalized.add(s.trim());
            }
        }
        sp.edit()
                .putStringSet(KEY_SUBJECTS, normalized)
                .putLong(KEY_VERSION, System.currentTimeMillis())
                .apply();
    }

    public static boolean allows(Context context, String subject) {
        Set<String> selected = getSelectedSubjects(context);
        if (selected.isEmpty()) return true; // No preference = show all
        String target = normalize(subject);
        if (target.isEmpty()) return false;
        for (String s : selected) {
            if (normalize(s).equals(target)) return true;
        }
        return false;
    }

    public static long getVersion(Context context) {
        if (context == null) return 0L;
        SharedPreferences sp = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getLong(KEY_VERSION, 0L);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
