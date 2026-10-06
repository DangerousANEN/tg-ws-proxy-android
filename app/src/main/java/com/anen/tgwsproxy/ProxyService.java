package com.anen.tgwsproxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ProxyService extends Service {
    private static final String TAG = "ProxyService";
    public static final String ACTION_START = "com.anen.tgwsproxy.START";
    public static final String ACTION_STOP = "com.anen.tgwsproxy.STOP";
    public static final String ACTION_STATUS_BROADCAST = "com.anen.tgwsproxy.STATUS";
    public static final String ACTION_LOG_BROADCAST = "com.anen.tgwsproxy.LOG";
    public static final String EXTRA_LOG_LINE = "extra_log_line";
    public static final String EXTRA_IS_RUNNING = "extra_is_running";

    private static final String CHANNEL_ID = "tg_ws_proxy_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MAX_LOG_LINES = 300;

    private static volatile boolean isRunning = false;
    private static final List<String> logHistory = Collections.synchronizedList(new LinkedList<String>());

    private Process proxyProcess;
    private Thread stdoutThread;
    private Thread stderrThread;
    private ConfigManager configManager;

    public static boolean isRunning() {
        return isRunning;
    }

    public static List<String> getLogHistory() {
        synchronized (logHistory) {
            return new ArrayList<>(logHistory);
        }
    }

    public static void clearLogs() {
        synchronized (logHistory) {
            logHistory.clear();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        configManager = new ConfigManager(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopProxy();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Запуск прокси..."));
        startProxy();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopProxy();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "TG WS Proxy Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Фоновый сервис прокси для Telegram");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent stopIntent = new Intent(this, ProxyService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TG WS Proxy")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPendingIntent)
                .setOngoing(true)
                .build();
    }

    private synchronized void startProxy() {
        if (isRunning) {
            return;
        }

        File binaryFile = new File(getApplicationInfo().nativeLibraryDir, "libtgproxy.so");
        if (!binaryFile.exists() || !binaryFile.canExecute()) {
            addLog("ОШИБКА: Бинарник libtgproxy.so не найден в " + binaryFile.getAbsolutePath());
            updateStatus(false);
            stopSelf();
            return;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(binaryFile.getAbsolutePath());
        cmd.add("-mode");
        cmd.add("socks5");
        cmd.add("-host");
        cmd.add(configManager.getListenHost());
        cmd.add("-port");
        cmd.add(String.valueOf(configManager.getPort()));

        if (configManager.isCfProxy()) {
            cmd.add("-cf-proxy");
            cmd.add("-cf-proxy-first");
            cmd.add("-cf-balance");
            String customDomains = configManager.getCfDomains();
            if (customDomains != null && !customDomains.trim().isEmpty()) {
                cmd.add("-cf-domain");
                cmd.add(customDomains.trim());
            } else {
                cmd.add("-cf-domain");
                cmd.add(ConfigManager.DEFAULT_CF_DOMAINS);
            }
        }

        cmd.add("-pool-size");
        cmd.add(String.valueOf(configManager.getPoolSize()));

        String username = configManager.getUsername();
        if (username != null && !username.trim().isEmpty()) {
            cmd.add("-username");
            cmd.add(username.trim());
        }

        String password = configManager.getPassword();
        if (password != null && !password.trim().isEmpty()) {
            cmd.add("-password");
            cmd.add(password.trim());
        }

        cmd.add("-verbose");

        addLog("Запуск команды: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().put("HOME", getFilesDir().getAbsolutePath());
        pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());

        try {
            proxyProcess = pb.start();
            isRunning = true;
            updateStatus(true);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification("SOCKS5 активен на " + configManager.getDisplayAddress()));
            }

            stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proxyProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        addLog(line);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Process stream closed: " + e.getMessage());
                }

                if (isRunning) {
                    addLog("Процесс прокси неожиданно завершился.");
                    stopProxy();
                    stopSelf();
                }
            });
            stdoutThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка запуска tg-ws-proxy", e);
            addLog("Исключение при старте: " + e.getMessage());
            updateStatus(false);
            stopSelf();
        }
    }

    private synchronized void stopProxy() {
        isRunning = false;
        if (proxyProcess != null) {
            try {
                proxyProcess.destroy();
            } catch (Exception ignored) {
            }
            proxyProcess = null;
        }

        if (stdoutThread != null) {
            stdoutThread.interrupt();
            stdoutThread = null;
        }

        addLog("Прокси остановлен.");
        updateStatus(false);
    }

    private void addLog(String text) {
        Log.i(TAG, text);
        synchronized (logHistory) {
            if (logHistory.size() >= MAX_LOG_LINES) {
                logHistory.remove(0);
            }
            logHistory.add(text);
        }

        Intent broadcast = new Intent(ACTION_LOG_BROADCAST);
        broadcast.putExtra(EXTRA_LOG_LINE, text);
        broadcast.setPackage(getPackageName());
        sendBroadcast(broadcast);
    }

    private void updateStatus(boolean running) {
        isRunning = running;
        Intent broadcast = new Intent(ACTION_STATUS_BROADCAST);
        broadcast.putExtra(EXTRA_IS_RUNNING, running);
        broadcast.setPackage(getPackageName());
        sendBroadcast(broadcast);
    }
}
