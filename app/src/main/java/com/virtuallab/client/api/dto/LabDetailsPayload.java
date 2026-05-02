package com.virtuallab.client.api.dto;

import java.util.List;

public class LabDetailsPayload {
    public int id;
    public String title;
    public String subject;
    public String lab_name;
    public int duration_minutes;
    public String difficulty;
    public String overview;
    public String objectives;
    public String materials;
    public String procedure;
    public String program_code;
    public String program_output;
    public String code_description;
    public List<String> figures;
    public String simulator_url;
    public String version;
    public String file_size;
    public String checksum;
    public String simulation_zip_url;
    public String main_file;
    public String thumbnail_url;
    public String last_updated;
    public boolean offline_available;
}
