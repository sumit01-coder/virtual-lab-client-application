package com.virtuallab.client.api.dto;

import java.util.List;

public class HomePayload {
    public String greeting;
    public Stats stats;
    public List<LabListItem> featured;
    public List<LabListItem> continue_learning;

    public static class Stats {
        public int departments;
        public int labs;
        public int practicals;
        public int completed_practicals;
        public int streak_days;
    }
}
