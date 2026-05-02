package com.virtuallab.client.api.dto;

import java.util.List;

public class ProgressPayload {
    public Summary summary;
    public List<CompletedItem> completed;
    public List<DepartmentProgress> departments;
    public List<String> badges;

    public static class Summary {
        public int completed_labs;
        public int tokens;
        public int streak_days;
    }

    public static class CompletedItem {
        public int practical_id;
        public String completed_at;
        public String title;
        public String subject;
    }

    public static class DepartmentProgress {
        public int department_id;
        public String department_name;
        public int completed_practicals;
        public int total_practicals;
        public int progress_percent;
        public boolean certificate_ready;
        public String certificate_url;
    }
}
