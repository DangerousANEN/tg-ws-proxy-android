package com.anen.tgwsproxy;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {
    private TextInputEditText etPort;
    private SwitchMaterial switchLanShare;
    private SwitchMaterial switchAutostart;
    private SwitchMaterial switchCfProxy;
    private TextInputEditText etCfDomains;
    private TextInputEditText etPoolSize;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnBatteryOpt;
    private MaterialButton btnSave;

    private ConfigManager configManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        configManager = new ConfigManager(this);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etPort = findViewById(R.id.et_port);
        switchLanShare = findViewById(R.id.switch_lan_share);
        switchAutostart = findViewById(R.id.switch_autostart);
        switchCfProxy = findViewById(R.id.switch_cf_proxy);
        etCfDomains = findViewById(R.id.et_cf_domains);
        etPoolSize = findViewById(R.id.et_pool_size);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnBatteryOpt = findViewById(R.id.btn_battery_opt);
        btnSave = findViewById(R.id.btn_save);

        loadCurrentSettings();
        setupListeners();
    }

    private void loadCurrentSettings() {
        etPort.setText(String.valueOf(configManager.getPort()));
        switchLanShare.setChecked(configManager.isLanShare());
        switchAutostart.setChecked(configManager.isAutostart());
        switchCfProxy.setChecked(configManager.isCfProxy());
        etCfDomains.setText(configManager.getCfDomains());
        etPoolSize.setText(String.valueOf(configManager.getPoolSize()));
        etUsername.setText(configManager.getUsername());
        etPassword.setText(configManager.getPassword());
    }

    private void setupListeners() {
        btnBatteryOpt.setOnClickListener(v -> requestIgnoreBatteryOptimization());

        btnSave.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
            if (ProxyService.isRunning()) {
                Toast.makeText(this, "Перезапустите прокси тумблером для применения новых настроек", Toast.LENGTH_LONG).show();
            }
            finish();
        });
    }

    private void saveSettings() {
        try {
            int port = Integer.parseInt(etPort.getText().toString().trim());
            configManager.setPort(port);
        } catch (Exception ignored) {
        }

        configManager.setLanShare(switchLanShare.isChecked());
        configManager.setAutostart(switchAutostart.isChecked());
        configManager.setCfProxy(switchCfProxy.isChecked());
        configManager.setCfDomains(etCfDomains.getText() != null ? etCfDomains.getText().toString().trim() : "");

        try {
            int poolSize = Integer.parseInt(etPoolSize.getText().toString().trim());
            configManager.setPoolSize(poolSize);
        } catch (Exception ignored) {
        }

        configManager.setUsername(etUsername.getText() != null ? etUsername.getText().toString().trim() : "");
        configManager.setPassword(etPassword.getText() != null ? etPassword.getText().toString().trim() : "");
    }

    @SuppressLint("BatteryLife")
    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, "Оптимизация батареи уже отключена для этого приложения", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
