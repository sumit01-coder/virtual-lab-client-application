package com.virtuallab.client.api.dto;

import java.util.List;

public class CatalogPayload {
    public boolean guest_mode;
    public boolean advanced_features_locked;
    public FeatureFlags features;
    public List<DepartmentTreeItem> departments;

    public static class FeatureFlags {
        public boolean ai_chat;
        public boolean progress;
        public boolean certificate;
        public boolean full_practicals;
    }
}
