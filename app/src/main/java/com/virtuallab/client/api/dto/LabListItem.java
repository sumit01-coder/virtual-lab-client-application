package com.virtuallab.client.api.dto;

import java.util.List;

public class LabListItem {
    public int id;
    public String title;
    public String overview;
    public String subject;
    public String lab_name;
    public int duration_minutes;
    public String difficulty;
    public String image;
    public List<String> images;
    public boolean preview_allowed;
    public boolean locked;
    public boolean requires_login;
}
