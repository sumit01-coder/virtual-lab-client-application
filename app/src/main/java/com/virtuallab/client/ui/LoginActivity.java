package com.virtuallab.client.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.virtuallab.client.Config;
import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.LoginPayload;
import com.virtuallab.client.data.SessionStore;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE = 1001;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(Config.GOOGLE_WEB_CLIENT_ID)
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        EditText etLinkCode = findViewById(R.id.etLinkCode);
        TextView txtForgot = findViewById(R.id.txtForgot);
        TextView txtOtp = findViewById(R.id.txtOtp);

        txtForgot.setOnClickListener(v ->
                Toast.makeText(this, "Forgot password flow will be added next.", Toast.LENGTH_SHORT).show()
        );

        txtOtp.setOnClickListener(v ->
                Toast.makeText(this, "OTP login flow will be added next.", Toast.LENGTH_SHORT).show()
        );

        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            String identifier = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (identifier.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email/username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("identifier", identifier);
            body.put("password", password);

            ApiClient.get().login(body).enqueue(new Callback<ApiEnvelope<LoginPayload>>() {
                @Override
                public void onResponse(Call<ApiEnvelope<LoginPayload>> call, Response<ApiEnvelope<LoginPayload>> response) {
                    ApiEnvelope<LoginPayload> env = response.body();
                    if (response.isSuccessful() && env != null && env.status && env.data != null && env.data.token != null) {
                        SessionStore.setAuthToken(env.data.token);
                        openMain();
                    } else {
                        Toast.makeText(LoginActivity.this, env != null ? env.message : "Login failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ApiEnvelope<LoginPayload>> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        findViewById(R.id.btnGoogle).setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE);
        });

        findViewById(R.id.btnLinkCode).setOnClickListener(v -> {
            String code = etLinkCode.getText() != null ? etLinkCode.getText().toString().trim() : "";
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter link code", Toast.LENGTH_SHORT).show();
                return;
            }
            linkWithCode(code);
        });

        String deepCode = extractCodeFromIntent(getIntent());
        if (!deepCode.isEmpty()) {
            etLinkCode.setText(deepCode);
            linkWithCode(deepCode);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account == null) {
                    Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show();
                    return;
                }

                Map<String, Object> body = new HashMap<>();
                body.put("id_token", account.getIdToken());
                body.put("email", account.getEmail());
                body.put("name", account.getDisplayName());
                body.put("google_id", account.getId());
                body.put("picture", account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : "");

                ApiClient.get().googleLogin(body).enqueue(new Callback<ApiEnvelope<LoginPayload>>() {
                    @Override
                    public void onResponse(Call<ApiEnvelope<LoginPayload>> call, Response<ApiEnvelope<LoginPayload>> response) {
                        ApiEnvelope<LoginPayload> env = response.body();
                        if (response.isSuccessful() && env != null && env.status && env.data != null && env.data.token != null) {
                            SessionStore.setAuthToken(env.data.token);
                            openMain();
                        } else {
                            Toast.makeText(LoginActivity.this, env != null ? env.message : "Google login failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiEnvelope<LoginPayload>> call, Throwable t) {
                        Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void linkWithCode(String codeRaw) {
        String code = codeRaw.toUpperCase().trim();
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
        body.put("platform", "android-" + Build.VERSION.RELEASE);
        ApiClient.get().linkLogin(body).enqueue(new Callback<ApiEnvelope<LoginPayload>>() {
            @Override
            public void onResponse(Call<ApiEnvelope<LoginPayload>> call, Response<ApiEnvelope<LoginPayload>> response) {
                ApiEnvelope<LoginPayload> env = response.body();
                if (response.isSuccessful() && env != null && env.status && env.data != null && env.data.token != null) {
                    SessionStore.setAuthToken(env.data.token);
                    Toast.makeText(LoginActivity.this, "Device linked", Toast.LENGTH_SHORT).show();
                    openMain();
                } else {
                    Toast.makeText(LoginActivity.this, env != null ? env.message : "Link failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiEnvelope<LoginPayload>> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String extractCodeFromIntent(Intent intent) {
        if (intent == null) return "";
        Uri data = intent.getData();
        if (data == null) return "";
        String code = data.getQueryParameter("code");
        return code != null ? code.trim() : "";
    }
}
