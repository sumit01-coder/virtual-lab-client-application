package com.virtuallab.client.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.CertificatePayload;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CertificateActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certificate);

        int deptId = getIntent().getIntExtra("dept_id", 0);
        String deptName = getIntent().getStringExtra("dept_name");

        TextView txtDept = findViewById(R.id.txtCertDepartment);
        TextView txtName = findViewById(R.id.txtCertName);
        TextView txtIssued = findViewById(R.id.txtCertIssued);
        TextView txtHash = findViewById(R.id.txtCertHash);
        TextView txtProgress = findViewById(R.id.txtCertProgress);

        findViewById(R.id.btnCloseCert).setOnClickListener(v -> finish());
        txtDept.setText((deptName != null && !deptName.trim().isEmpty()) ? deptName : "Department");

        if (deptId <= 0) {
            Toast.makeText(this, "Invalid department", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("dept_id", deptId);
        ApiClient.get().issueCertificate(body).enqueue(new Callback<ApiEnvelope<CertificatePayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<CertificatePayload>> call, Response<ApiEnvelope<CertificatePayload>> response) {
                ApiEnvelope<CertificatePayload> env = response.body();
                if (!response.isSuccessful() || env == null || !env.status || env.data == null) {
                    Toast.makeText(CertificateActivity.this, env != null ? env.message : "Unable to generate certificate", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                CertificatePayload d = env.data;
                txtDept.setText(d.department_name != null ? d.department_name : "Department");
                txtName.setText(d.student_name != null ? d.student_name : "Student");
                txtIssued.setText("Issued: " + formatDate(d.issued_at));
                txtHash.setText("Certificate ID: " + (d.certificate_hash != null ? d.certificate_hash : "N/A"));
                txtProgress.setText("Completion: " + d.completed_practicals + "/" + d.total_practicals + " (100%)");
            }

            @Override
            public void onFailure(Call<ApiEnvelope<CertificatePayload>> call, Throwable t) {
                Toast.makeText(CertificateActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private String formatDate(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) return "-";
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date dt = in.parse(raw);
            if (dt == null) return raw;
            return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(dt);
        } catch (Exception e) {
            return raw != null ? raw : "-";
        }
    }
}

