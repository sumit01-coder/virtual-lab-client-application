package com.virtuallab.client.api;

import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.AccessPayload;
import com.virtuallab.client.api.dto.CertificatePayload;
import com.virtuallab.client.api.dto.HomePayload;
import com.virtuallab.client.api.dto.LabDetailsPayload;
import com.virtuallab.client.api.dto.LabListItem;
import com.virtuallab.client.api.dto.LoginPayload;
import com.virtuallab.client.api.dto.ProfilePayload;
import com.virtuallab.client.api.dto.ProgressPayload;
import com.virtuallab.client.api.dto.DepartmentTreeItem;
import com.virtuallab.client.api.dto.CatalogPayload;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    @POST("login.php")
    Call<ApiEnvelope<LoginPayload>> login(@Body Map<String, Object> body);

    @POST("google_login.php")
    Call<ApiEnvelope<LoginPayload>> googleLogin(@Body Map<String, Object> body);

    @POST("link_login.php")
    Call<ApiEnvelope<LoginPayload>> linkLogin(@Body Map<String, Object> body);

    @GET("home.php")
    Call<ApiEnvelope<HomePayload>> home();

    @GET("explore.php")
    Call<ApiEnvelope<java.util.List<LabListItem>>> explore(@Query("q") String q, @Query("subject") String subject, @Query("difficulty") String difficulty);

    @GET("lab_details.php")
    Call<ApiEnvelope<LabDetailsPayload>> labDetails(@Query("id") int id);

    @GET("progress.php")
    Call<ApiEnvelope<ProgressPayload>> progress();

    @GET("profile.php")
    Call<ApiEnvelope<ProfilePayload>> profile();

    @GET("departments_tree.php")
    Call<ApiEnvelope<CatalogPayload>> departmentsTree();

    @POST("profile.php")
    Call<ApiEnvelope<Object>> updateProfile(@Body Map<String, Object> body);

    @POST("mark_complete.php")
    Call<ApiEnvelope<Object>> markComplete(@Body Map<String, Object> body);

    @POST("access_practical.php")
    Call<ApiEnvelope<AccessPayload>> accessPractical(@Body Map<String, Object> body);

    @POST("certificate_issue.php")
    Call<ApiEnvelope<CertificatePayload>> issueCertificate(@Body Map<String, Object> body);
}
