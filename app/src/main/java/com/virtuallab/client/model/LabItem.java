package com.virtuallab.client.model;

import java.util.ArrayList;
import java.util.List;

public class LabItem {
    public final int id;
    public final String title;
    public final String subject;
    public final String duration;
    public final String difficulty;
    public final boolean locked;
    public final String accessLabel;
    public final String imageUrl;

    public LabItem(int id, String title, String subject, String duration, String difficulty, boolean locked) {
        this(id, title, subject, duration, difficulty, locked, "", "");
    }

    public LabItem(int id, String title, String subject, String duration, String difficulty, boolean locked, String accessLabel) {
        this(id, title, subject, duration, difficulty, locked, accessLabel, "");
    }

    public LabItem(int id, String title, String subject, String duration, String difficulty, boolean locked, String accessLabel, String imageUrl) {
        this.id = id;
        this.title = title;
        this.subject = subject;
        this.duration = duration;
        this.difficulty = difficulty;
        this.locked = locked;
        this.accessLabel = accessLabel != null ? accessLabel : "";
        this.imageUrl = imageUrl != null ? imageUrl : "";
    }

    public String meta() {
        List<String> parts = new ArrayList<>();
        if (subject != null && !subject.trim().isEmpty()) parts.add(subject.trim());
        if (duration != null && !duration.trim().isEmpty()) parts.add(duration.trim());
        if (difficulty != null && !difficulty.trim().isEmpty()) parts.add(difficulty.trim());
        return String.join(" | ", parts);
    }
}


