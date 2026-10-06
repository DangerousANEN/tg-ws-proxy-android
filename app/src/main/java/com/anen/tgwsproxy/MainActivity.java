package com.anen.tgwsproxy;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private SwitchMaterial switchProxy;
    private TextView tvStatus;
    private TextView tvProxyAddress;
    private MaterialButton btnCopy;
    private MaterialButton btnConnectTelegram;
    private TextView tvListenHost;
    private TextView tvCfStatus;
    private TextView tvLogs;
    private ScrollView scrollLogs;
    private MaterialButton btnClearLogs;

    private ConfigManager configManager;
    private boolean isUpdatingSwitch = false;

    private final BroadcastReceiver proxyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;

            if (ProxyService.ACTION_STATUS_BROADCAST.equals(intent.getAction())) {
                boolean running = intent.getBooleanExtra(ProxyService.EXTRA_IS_RUNNING, false);
                updateUiState(running);
            } else if (ProxyService.ACTION_LOG_BROADCAST.equals(intent.getAction())) {
                String line = intent.getStringExtra(ProxyService.EXTRA_LOG_LINE);
                if (line != null) {
                    appendLogLine(line);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configManager = new ConfigManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        switchProxy = findViewById(R.id.switch_proxy);
        tvStatus = findViewById(R.id.tv_status);
        tvProxyAddress = findViewById(R.id.tv_proxy_address);
        btnCopy = findViewById(R.id.btn_copy);
        btnConnectTelegram = findViewById(R.id.btn_connect_telegram);
        tvListenHost = findViewById(R.id.tv_listen_host);
        tvCfStatus = findViewById(R.id.tv_cf_status);
        tvLogs = findViewById(R.id.tv_logs);
        scrollLogs = findViewById(R.id.scroll_logs);
        btnClearLogs = findViewById(R.id.btn_clear_logs);

        setupListeners();
        requestNotificationPermission();
    }

    private void setupListeners() {
        switchProxy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingSwitch) return;

            if (isChecked) {
                startProxyService();
            } else {
                stopProxyService();
            }
        });

        btnCopy.setOnClickListener(v -> {
            String address = tvProxyAddress.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("TG Proxy Address", address);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            }
        });

        btnConnectTelegram.setOnClickListener(v -> {
            String deepLink = configManager.getTelegramDeepLink();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                // Telegram app not installed or cannot handle tg:// scheme, fallback to web link
                try {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(configManager.getTelegramWebLink()));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(webIntent);
                    Toast.makeText(this, R.string.telegram_not_found, Toast.LENGTH_LONG).show();
                } catch (Exception ex) {
                    Toast.makeText(this, "Не удалось открыть Telegram: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnClearLogs.setOnClickListener(v -> {
            ProxyService.clearLogs();
            tvLogs.setText("");
        });
    }

    private void startProxyService() {
        Intent serviceIntent = new Intent(this, ProxyService.class);
        serviceIntent.setAction(ProxyService.ACTION_START);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void stopProxyService() {
        Intent serviceIntent = new Intent(this, ProxyService.class);
        serviceIntent.setAction(ProxyService.ACTION_STOP);
        startService(serviceIntent);
    }

    private void updateUiState(boolean running) {
        isUpdatingSwitch = true;
        switchProxy.setChecked(running);
        isUpdatingSwitch = false;

        if (running) {
            tvStatus.setText("🟢 " + getString(R.string.status_running));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active));
        } else {
            tvStatus.setText("🔴 " + getString(R.string.status_stopped));
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_inactive));
        }
    }

    private void appendLogLine(String line) {
        tvLogs.append("\n" + line);
        scrollLogs.post(() -> scrollLogs.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ProxyService.ACTION_STATUS_BROADCAST);
        filter.addAction(ProxyService.ACTION_LOG_BROADCAST);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(proxyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(proxyReceiver, filter);
        }

        tvProxyAddress.setText(configManager.getDisplayAddress());
        tvListenHost.setText(configManager.isLanShare() ? "0.0.0.0 (Все интерфейсы LAN)" : "127.0.0.1 (Loopback)");
        tvCfStatus.setText(configManager.isCfProxy() ? "Включено (CF Route)" : "Выключено (Direct)");

        boolean running = ProxyService.isRunning();
        updateUiState(running);

        List<String> history = ProxyService.getLogHistory();
        if (!history.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String log : history) {
                sb.append(log).append("\n");
            }
            tvLogs.setText(sb.toString().trim());
            scrollLogs.post(() -> scrollLogs.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(proxyReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }
    }
}
