package com.virtuallab.client.api.dto;

import java.util.List;

public class DepartmentTreeItem {
    public int id;
    public String name;
    public String description;
    public List<LabNode> labs;

    public static class LabNode {
        public int id;
        public String name;
        public String subject;
        public String description;
        public boolean preview_allowed;
        public boolean locked;
        public boolean requires_login;
        public List<PracticalNode> practicals;
    }

    public static class PracticalNode {
        public int id;
        public String title;
        public String overview;
        public boolean preview_allowed;
        public boolean locked;
        public boolean requires_login;
    }
}
