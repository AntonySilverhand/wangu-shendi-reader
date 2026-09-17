package org.wanshu.reader.download;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.app.KeyguardManager;
import org.wanshu.reader.data.content.entity.DownloadPolicyEntity;

public class ReadingSessionGate {

    private final Context appContext;

    private volatile boolean activityResumed = false;
    private volatile boolean readerActive = false;
    private volatile String activeReaderBookId = "";
    private volatile boolean highLoadRunning = false;

    public ReadingSessionGate(Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
    }

    public void setActivityResumed(boolean resumed) {
        this.activityResumed = resumed;
    }

    public boolean isActivityResumed() {
        return activityResumed;
    }

    public void setReaderActive(boolean active, String bookId) {
        this.readerActive = active;
        this.activeReaderBookId = bookId != null ? bookId : "";
    }

    public boolean isReaderActive() {
        return readerActive;
    }

    public String getActiveReaderBookId() {
        return activeReaderBookId;
    }

    public void setHighLoadRunning(boolean running) {
        this.highLoadRunning = running;
    }

    public boolean isHighLoadRunning() {
        return highLoadRunning;
    }

    public GateResult evaluate(String bookId, DownloadPolicyEntity policy, long currentCacheBytes) {
        if (!activityResumed) {
            return new GateResult(false, PauseReason.APP_HIDDEN);
        }

        if (!readerActive || activeReaderBookId == null || !activeReaderBookId.equals(bookId)) {
            return new GateResult(false, PauseReason.NOT_READING);
        }

        // Local books never trigger online auto-cache
        if (bookId == null || !bookId.equals("36780")) {
            return new GateResult(false, PauseReason.NOT_READING);
        }

        if (highLoadRunning) {
            return new GateResult(false, PauseReason.HIGH_LOAD);
        }

        if (appContext != null) {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                if (!pm.isInteractive()) {
                    return new GateResult(false, PauseReason.APP_HIDDEN);
                }
                if (pm.isPowerSaveMode()) {
                    return new GateResult(false, PauseReason.POWER_SAVE);
                }
            }

            KeyguardManager km = (KeyguardManager) appContext.getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) {
                return new GateResult(false, PauseReason.APP_HIDDEN);
            }

            // Battery check: <= 20% pauses auto-cache
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = appContext.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    float pct = (level * 100.0f) / (float) scale;
                    if (pct <= 20.0f) {
                        return new GateResult(false, PauseReason.BATTERY_LOW);
                    }
                }
            }

            // Connectivity check
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network activeNet = cm.getActiveNetwork();
                if (activeNet == null) {
                    return new GateResult(false, PauseReason.OFFLINE);
                }
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return new GateResult(false, PauseReason.OFFLINE);
                }
                boolean allowMetered = policy != null && policy.allowMetered;
                if (!allowMetered && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                    return new GateResult(false, PauseReason.METERED);
                }
            }
        }

        // Cache storage check
        long maxCache = policy != null && policy.maxCacheBytes > 0 ? policy.maxCacheBytes : 256L * 1024L * 1024L;
        if (currentCacheBytes > 0 && currentCacheBytes >= maxCache) {
            return new GateResult(false, PauseReason.STORAGE_LOW);
        }

        return new GateResult(true, PauseReason.NONE);
    }
}
