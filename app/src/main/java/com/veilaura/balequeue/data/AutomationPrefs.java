package com.veilaura.balequeue.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.veilaura.balequeue.domain.BaleTextRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AutomationPrefs {
    private static final String FILE = "bale_automation_v3";
    private static final String ACTIVE = "active";
    private static final String CHANNEL = "channel";
    private static final String SELECTED_COUNT = "selected_count";
    private static final String SELECTED_KEYS = "selected_keys";
    private static final String STATUS = "status";
    private static final String PROCESSED_PREFIX = "processed_";
    private static final String PENDING_PREFIX = "pending_";
    private static final String ADDED_PREFIX = "added_";
    private static final String FAILED_PREFIX = "failed_";

    private final SharedPreferences prefs;

    public AutomationPrefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public void start(String channel) {
        String normalized = normalizeChannel(channel);
        prefs.edit()
                .putBoolean(ACTIVE, true)
                .putString(CHANNEL, normalized)
                .putInt(SELECTED_COUNT, 0)
                .putStringSet(SELECTED_KEYS, new HashSet<>())
                .putString(STATUS, "در حال بازکردن بله")
                .apply();
    }

    public boolean isActive() {
        return prefs.getBoolean(ACTIVE, false);
    }

    public String channel() {
        return prefs.getString(CHANNEL, "");
    }

    public int selectedCount() {
        return prefs.getInt(SELECTED_COUNT, 0);
    }

    public String status() {
        return prefs.getString(STATUS, "آماده");
    }

    public boolean hasProcessedKey(String key) {
        return key != null && readSet(storageKey(PROCESSED_PREFIX, channel())).contains(key);
    }

    public synchronized int recordCheckedSelection(String key) {
        int selected = selectedCount();
        if (!isActive() || key == null || key.trim().isEmpty()) return selected;

        Set<String> current = readSet(SELECTED_KEYS);
        if (current.contains(key)) return selected;

        current.add(key);
        Set<String> processed = readSet(storageKey(PROCESSED_PREFIX, channel()));
        Set<String> pending = readSet(storageKey(PENDING_PREFIX, channel()));
        processed.add(key);
        pending.add(key);
        int next = selected + 1;

        prefs.edit()
                .putInt(SELECTED_COUNT, next)
                .putStringSet(SELECTED_KEYS, current)
                .putStringSet(storageKey(PROCESSED_PREFIX, channel()), processed)
                .putStringSet(storageKey(PENDING_PREFIX, channel()), pending)
                .apply();
        return next;
    }

    public int processedCount(String channel) {
        return readSet(storageKey(PROCESSED_PREFIX, channel)).size();
    }

    public int pendingCount(String channel) {
        return readSet(storageKey(PENDING_PREFIX, channel)).size();
    }

    public int addedCount(String channel) {
        return readSet(storageKey(ADDED_PREFIX, channel)).size();
    }

    public int failedCount(String channel) {
        return readSet(storageKey(FAILED_PREFIX, channel)).size();
    }

    public List<String> pendingNames(String channel) {
        return sorted(readSet(storageKey(PENDING_PREFIX, channel)));
    }

    public List<String> addedNames(String channel) {
        return sorted(readSet(storageKey(ADDED_PREFIX, channel)));
    }

    public List<String> failedNames(String channel) {
        return sorted(readSet(storageKey(FAILED_PREFIX, channel)));
    }

    public synchronized void resolvePending(String channel, Set<String> confirmedAdded) {
        Set<String> pending = readSet(storageKey(PENDING_PREFIX, channel));
        Set<String> added = readSet(storageKey(ADDED_PREFIX, channel));
        Set<String> failed = readSet(storageKey(FAILED_PREFIX, channel));

        for (String name : pending) {
            if (confirmedAdded.contains(name)) {
                added.add(name);
                failed.remove(name);
            } else {
                failed.add(name);
                added.remove(name);
            }
        }

        prefs.edit()
                .putStringSet(storageKey(ADDED_PREFIX, channel), added)
                .putStringSet(storageKey(FAILED_PREFIX, channel), failed)
                .putStringSet(storageKey(PENDING_PREFIX, channel), new HashSet<>())
                .putStringSet(SELECTED_KEYS, new HashSet<>())
                .putInt(SELECTED_COUNT, 0)
                .putBoolean(ACTIVE, false)
                .putString(STATUS, "نتیجه افزودن ثبت شد")
                .apply();
    }

    public synchronized void retryPending(String channel) {
        Set<String> pending = readSet(storageKey(PENDING_PREFIX, channel));
        Set<String> processed = readSet(storageKey(PROCESSED_PREFIX, channel));
        processed.removeAll(pending);
        prefs.edit()
                .putStringSet(storageKey(PROCESSED_PREFIX, channel), processed)
                .putStringSet(storageKey(PENDING_PREFIX, channel), new HashSet<>())
                .putStringSet(SELECTED_KEYS, new HashSet<>())
                .putInt(SELECTED_COUNT, 0)
                .putBoolean(ACTIVE, false)
                .putString(STATUS, "افراد در انتظار برای تلاش دوباره آزاد شدند")
                .apply();
    }

    public synchronized void clearAllHistory(String channel) {
        prefs.edit()
                .remove(storageKey(PROCESSED_PREFIX, channel))
                .remove(storageKey(PENDING_PREFIX, channel))
                .remove(storageKey(ADDED_PREFIX, channel))
                .remove(storageKey(FAILED_PREFIX, channel))
                .putBoolean(ACTIVE, false)
                .putInt(SELECTED_COUNT, 0)
                .putStringSet(SELECTED_KEYS, new HashSet<>())
                .putString(STATUS, "همه سوابق این کانال پاک شد")
                .apply();
    }

    private Set<String> readSet(String key) {
        Set<String> source = prefs.getStringSet(key, Collections.emptySet());
        return new HashSet<>(source == null ? Collections.emptySet() : source);
    }

    private List<String> sorted(Set<String> values) {
        List<String> result = new ArrayList<>(values);
        Collections.sort(result);
        return result;
    }

    private String storageKey(String prefix, String channel) {
        return prefix + normalizeChannel(channel).toLowerCase(Locale.ROOT);
    }

    private String normalizeChannel(String channel) {
        String normalized = BaleTextRules.normalizeChannel(channel);
        return normalized == null ? "" : normalized;
    }

    public void setStatus(String status) {
        prefs.edit().putString(STATUS, status).apply();
    }

    public void finishSelection(String status) {
        prefs.edit()
                .putBoolean(ACTIVE, false)
                .putString(STATUS, status)
                .apply();
    }

    public void stopByUser() {
        prefs.edit()
                .putBoolean(ACTIVE, false)
                .putString(STATUS, "با دستور شما متوقف شد")
                .apply();
    }

    public void discard(String status) {
        prefs.edit()
                .putBoolean(ACTIVE, false)
                .putInt(SELECTED_COUNT, 0)
                .putStringSet(SELECTED_KEYS, new HashSet<>())
                .putString(STATUS, status)
                .apply();
    }
}
