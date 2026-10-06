package com.anen.tgwsproxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class ConfigManager {
    private static final String PREF_NAME = "tg_ws_proxy_prefs";
    public static final String KEY_PORT = "pref_port";
    public static final String KEY_LAN_SHARE = "pref_lan_share";
    public static final String KEY_AUTOSTART = "pref_autostart";
    public static final String KEY_CF_PROXY = "pref_cf_proxy";
    public static final String KEY_CF_DOMAINS = "pref_cf_domains";
    public static final String KEY_POOL_SIZE = "pref_pool_size";
    public static final String KEY_USERNAME = "pref_username";
    public static final String KEY_PASSWORD = "pref_password";

    public static final String DEFAULT_CF_DOMAINS = "pclead.co.uk,offshor.co.uk,cakeisalie.co.uk,noskomnadzor.co.uk,lovetrue.co.uk,sorokdva.co.uk,pyatdesyatdva.co.uk,kartoshka.co.uk";

    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getPort() {
        try {
            return Integer.parseInt(prefs.getString(KEY_PORT, "1080"));
        } catch (Exception e) {
            return 1080;
        }
    }

    public void setPort(int port) {
        prefs.edit().putString(KEY_PORT, String.valueOf(port)).apply();
    }

    public boolean isLanShare() {
        return prefs.getBoolean(KEY_LAN_SHARE, false);
    }

    public void setLanShare(boolean enabled) {
        prefs.edit().putBoolean(KEY_LAN_SHARE, enabled).apply();
    }

    public boolean isAutostart() {
        return prefs.getBoolean(KEY_AUTOSTART, false);
    }

    public void setAutostart(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTOSTART, enabled).apply();
    }

    public boolean isCfProxy() {
        return prefs.getBoolean(KEY_CF_PROXY, true);
    }

    public void setCfProxy(boolean enabled) {
        prefs.edit().putBoolean(KEY_CF_PROXY, enabled).apply();
    }

    public String getCfDomains() {
        return prefs.getString(KEY_CF_DOMAINS, "");
    }

    public void setCfDomains(String domains) {
        prefs.edit().putString(KEY_CF_DOMAINS, domains).apply();
    }

    public int getPoolSize() {
        try {
            return Integer.parseInt(prefs.getString(KEY_POOL_SIZE, "8"));
        } catch (Exception e) {
            return 8;
        }
    }

    public void setPoolSize(int poolSize) {
        prefs.edit().putString(KEY_POOL_SIZE, String.valueOf(poolSize)).apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public void setUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, "");
    }

    public void setPassword(String password) {
        prefs.edit().putString(KEY_PASSWORD, password).apply();
    }

    public String getListenHost() {
        return isLanShare() ? "0.0.0.0" : "127.0.0.1";
    }

    public String getDisplayAddress() {
        return "127.0.0.1:" + getPort();
    }

    public String getTelegramDeepLink() {
        Uri.Builder b = Uri.parse("tg://socks").buildUpon();
        b.appendQueryParameter("server", "127.0.0.1");
        b.appendQueryParameter("port", String.valueOf(getPort()));
        String user = getUsername();
        String pass = getPassword();
        if (user != null && !user.trim().isEmpty()) {
            b.appendQueryParameter("user", user.trim());
        }
        if (pass != null && !pass.trim().isEmpty()) {
            b.appendQueryParameter("pass", pass);
        }
        return b.build().toString();
    }

    public String getTelegramWebLink() {
        Uri.Builder b = Uri.parse("https://t.me/socks").buildUpon();
        b.appendQueryParameter("server", "127.0.0.1");
        b.appendQueryParameter("port", String.valueOf(getPort()));
        String user = getUsername();
        String pass = getPassword();
        if (user != null && !user.trim().isEmpty()) {
            b.appendQueryParameter("user", user.trim());
        }
        if (pass != null && !pass.trim().isEmpty()) {
            b.appendQueryParameter("pass", pass);
        }
        return b.build().toString();
    }
}
