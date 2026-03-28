package com.yeapguy.volumescroll;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final float PERCENT_BASE_SCROLL_AMOUNT = 8.75f;
    private static final int MIN_SCROLL_PERCENT = 70;
    private static final int MAX_SCROLL_PERCENT = 120;
    private static final int SCROLL_PERCENT_STEP = 5;

    private SettingsRepository settingsRepository;

    private MaterialSwitch featureSwitch;
    private MaterialSwitch invertDirectionSwitch;
    private MaterialSwitch dualKeyToggleSwitch;
    private Slider stepsSlider;
    private TextView stepsLabelText;
    private TextView stepsValueText;
    private TextView serviceStatusText;
    private SharedPreferences.OnSharedPreferenceChangeListener settingsChangeListener;
    private final Handler statusRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshStatusRunnable = this::bindCurrentSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingsRepository = new SettingsRepository(this);
        settingsChangeListener = (sharedPreferences, key) -> runOnUiThread(this::bindCurrentSettings);

        featureSwitch = findViewById(R.id.switchFeature);
        invertDirectionSwitch = findViewById(R.id.switchInvertDirection);
        dualKeyToggleSwitch = findViewById(R.id.switchDualKeyToggle);
        stepsSlider = findViewById(R.id.sliderSteps);
        stepsLabelText = findViewById(R.id.textStepsLabel);
        stepsValueText = findViewById(R.id.textStepsValue);
        serviceStatusText = findViewById(R.id.textServiceStatus);
        MaterialButton openAccessibilitySettings = findViewById(R.id.buttonOpenAccessibilitySettings);

        stepsSlider.setLabelFormatter(value -> formatPercent(roundToStepPercent(value)));

        bindCurrentSettings();

        featureSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setFeatureEnabled(isChecked);
            boolean serviceEnabled = updateServiceStatus();
            updateSettingsEnabledState(isChecked, serviceEnabled);
        });

        invertDirectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settingsRepository.setInvertDirectionEnabled(isChecked));

        dualKeyToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setShortcutToggleEnabled(isChecked);
        });

        stepsSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) {
                return;
            }
            int percent = roundToStepPercent(value);
            stepsSlider.setValue(percent);
            settingsRepository.setScrollAmount(percentToAmount(percent));
            stepsValueText.setText(formatPercent(percent));
        });

        openAccessibilitySettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindCurrentSettings();
        scheduleStatusRefreshes();
    }

    @Override
    protected void onStart() {
        super.onStart();
        settingsRepository.registerChangeListener(settingsChangeListener);
        bindCurrentSettings();
    }

    @Override
    protected void onStop() {
        super.onStop();
        settingsRepository.unregisterChangeListener(settingsChangeListener);
        statusRefreshHandler.removeCallbacks(refreshStatusRunnable);
    }

    private void scheduleStatusRefreshes() {
        statusRefreshHandler.removeCallbacks(refreshStatusRunnable);
        statusRefreshHandler.postDelayed(refreshStatusRunnable, 150L);
        statusRefreshHandler.postDelayed(refreshStatusRunnable, 400L);
        statusRefreshHandler.postDelayed(refreshStatusRunnable, 900L);
    }

    private void bindCurrentSettings() {
        boolean featureEnabled = settingsRepository.isFeatureEnabled();
        boolean invertEnabled = settingsRepository.isInvertDirectionEnabled();
        boolean dualKeyToggleEnabled = settingsRepository.isShortcutToggleEnabled();
        float scrollAmount = settingsRepository.getScrollAmount();
        int scrollPercent = amountToPercent(scrollAmount);

        featureSwitch.setChecked(featureEnabled);
        invertDirectionSwitch.setChecked(invertEnabled);
        dualKeyToggleSwitch.setChecked(dualKeyToggleEnabled);
        stepsSlider.setValue(scrollPercent);
        stepsValueText.setText(formatPercent(scrollPercent));
        boolean serviceEnabled = updateServiceStatus();
        updateSettingsEnabledState(featureEnabled, serviceEnabled);
    }

    private void updateSettingsEnabledState(boolean featureEnabled, boolean serviceEnabled) {
        boolean dualToggleEnabled = serviceEnabled;
        boolean mainToggleEnabled = serviceEnabled;
        boolean secondaryEnabled = serviceEnabled && featureEnabled;

        featureSwitch.setEnabled(mainToggleEnabled);
        dualKeyToggleSwitch.setEnabled(dualToggleEnabled);
        invertDirectionSwitch.setEnabled(secondaryEnabled);
        stepsSlider.setEnabled(secondaryEnabled);
        stepsLabelText.setEnabled(secondaryEnabled);
        stepsValueText.setEnabled(secondaryEnabled);

        float dualAlpha = dualToggleEnabled ? 1.0f : 0.45f;
        float mainAlpha = mainToggleEnabled ? 1.0f : 0.45f;
        float alpha = secondaryEnabled ? 1.0f : 0.45f;

        dualKeyToggleSwitch.setAlpha(dualAlpha);
        featureSwitch.setAlpha(mainAlpha);
        invertDirectionSwitch.setAlpha(alpha);
        stepsSlider.setAlpha(alpha);
        stepsLabelText.setAlpha(alpha);
        stepsValueText.setAlpha(alpha);
    }

    private int roundToStepPercent(float value) {
        int stepped = Math.round(value / SCROLL_PERCENT_STEP) * SCROLL_PERCENT_STEP;
        return Math.max(MIN_SCROLL_PERCENT, Math.min(stepped, MAX_SCROLL_PERCENT));
    }

    private int amountToPercent(float amount) {
        float rawPercent = (amount / PERCENT_BASE_SCROLL_AMOUNT) * 100f;
        return roundToStepPercent(rawPercent);
    }

    private float percentToAmount(int percent) {
        return (percent / 100f) * PERCENT_BASE_SCROLL_AMOUNT;
    }

    private String formatPercent(int percent) {
        return String.format(Locale.US, "%d%%", percent);
    }

    private boolean updateServiceStatus() {
        boolean enabled = AccessibilityStatusChecker.isServiceEnabled(
                this,
                VolumeScrollAccessibilityService.class
        );
        serviceStatusText.setText(enabled
                ? R.string.status_service_enabled
                : R.string.status_service_disabled);
        int colorAttr = enabled
            ? com.google.android.material.R.attr.colorPrimary
            : com.google.android.material.R.attr.colorError;
        serviceStatusText.setTextColor(MaterialColors.getColor(serviceStatusText, colorAttr));
        return enabled;
    }
}
