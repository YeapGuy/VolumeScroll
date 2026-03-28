package com.yeapguy.volumescroll;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class SettingsRepository {

    private static final String PREFS_NAME = "volume_scroll_prefs";
    private static final String KEY_FEATURE_ENABLED = "feature_enabled";
    private static final String KEY_INVERT_DIRECTION = "invert_direction";
    private static final String KEY_SCROLL_AMOUNT = "scroll_amount";
    private static final String KEY_SHORTCUT_TOGGLE_ENABLED = "shortcut_toggle_enabled";
    private static final String KEY_APP_FILTER_ENABLED = "app_filter_enabled";
    private static final String KEY_ALLOWED_APP_PACKAGES = "allowed_app_packages";

    private static final boolean DEFAULT_FEATURE_ENABLED = true;
    private static final boolean DEFAULT_INVERT_DIRECTION = false;
    private static final boolean DEFAULT_SHORTCUT_TOGGLE_ENABLED = true;
    private static final boolean DEFAULT_APP_FILTER_ENABLED = false;
    private static final float BASE_SCROLL_AMOUNT = 8.75f;
    private static final int DEFAULT_SCROLL_PERCENT = 100;
    private static final int MIN_SCROLL_PERCENT = 70;
    private static final int MAX_SCROLL_PERCENT = 120;
    private static final int SCROLL_PERCENT_STEP = 5;
    private static final float DEFAULT_SCROLL_AMOUNT = BASE_SCROLL_AMOUNT;

    private final SharedPreferences sharedPreferences;

    public SettingsRepository(Context context) {
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFeatureEnabled() {
        return sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, DEFAULT_FEATURE_ENABLED);
    }

    public void setFeatureEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_FEATURE_ENABLED, enabled).apply();
    }

    public boolean isInvertDirectionEnabled() {
        return sharedPreferences.getBoolean(KEY_INVERT_DIRECTION, DEFAULT_INVERT_DIRECTION);
    }

    public void setInvertDirectionEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_INVERT_DIRECTION, enabled).apply();
    }

    public float getScrollAmount() {
        float saved = sharedPreferences.getFloat(KEY_SCROLL_AMOUNT, DEFAULT_SCROLL_AMOUNT);
        return clampToQuarterStep(saved);
    }

    public void setScrollAmount(float amount) {
        sharedPreferences.edit().putFloat(KEY_SCROLL_AMOUNT, clampToQuarterStep(amount)).apply();
    }

    public boolean isShortcutToggleEnabled() {
        return sharedPreferences.getBoolean(KEY_SHORTCUT_TOGGLE_ENABLED, DEFAULT_SHORTCUT_TOGGLE_ENABLED);
    }

    public void setShortcutToggleEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_SHORTCUT_TOGGLE_ENABLED, enabled).apply();
    }

    public boolean isAppFilterEnabled() {
        return sharedPreferences.getBoolean(KEY_APP_FILTER_ENABLED, DEFAULT_APP_FILTER_ENABLED);
    }

    public void setAppFilterEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_APP_FILTER_ENABLED, enabled).apply();
    }

    public Set<String> getAllowedAppPackages() {
        Set<String> saved = sharedPreferences.getStringSet(KEY_ALLOWED_APP_PACKAGES, new HashSet<>());
        if (saved == null) {
            return new HashSet<>();
        }
        return new HashSet<>(saved);
    }

    public void setAllowedAppPackages(Set<String> packages) {
        sharedPreferences.edit().putStringSet(KEY_ALLOWED_APP_PACKAGES, new HashSet<>(packages)).apply();
    }

    public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private float clampToQuarterStep(float amount) {
        float percent = (amount / BASE_SCROLL_AMOUNT) * 100f;
        int steppedPercent = Math.round(percent / SCROLL_PERCENT_STEP) * SCROLL_PERCENT_STEP;
        int clampedPercent = Math.max(MIN_SCROLL_PERCENT, Math.min(steppedPercent, MAX_SCROLL_PERCENT));
        return (clampedPercent / 100f) * BASE_SCROLL_AMOUNT;
    }
}
