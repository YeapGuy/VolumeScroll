package com.yeapguy.volumescroll;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Menu;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final float PERCENT_BASE_SCROLL_AMOUNT = 8.75f;
    private static final int MIN_SCROLL_PERCENT = 70;
    private static final int MAX_SCROLL_PERCENT = 120;
    private static final int SCROLL_PERCENT_STEP = 5;
    private static final int APP_SELECTION_ACTION_SELECT_ALL = 1;
    private static final int APP_SELECTION_ACTION_INVERT = 2;
    private static final int APP_SELECTION_ACTION_CLEAR = 3;

    private SettingsRepository settingsRepository;

    private MaterialSwitch featureSwitch;
    private MaterialSwitch appFilterSwitch;
    private MaterialSwitch invertDirectionSwitch;
    private MaterialSwitch dualKeyToggleSwitch;
    private Slider stepsSlider;
    private TextView stepsLabelText;
    private TextView stepsValueText;
    private TextView selectedAppsSummaryText;
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
        appFilterSwitch = findViewById(R.id.switchAppFilter);
        invertDirectionSwitch = findViewById(R.id.switchInvertDirection);
        dualKeyToggleSwitch = findViewById(R.id.switchDualKeyToggle);
        stepsSlider = findViewById(R.id.sliderSteps);
        stepsLabelText = findViewById(R.id.textStepsLabel);
        stepsValueText = findViewById(R.id.textStepsValue);
        selectedAppsSummaryText = findViewById(R.id.textSelectedAppsSummary);
        serviceStatusText = findViewById(R.id.textServiceStatus);
        MaterialButton openAccessibilitySettings = findViewById(R.id.buttonOpenAccessibilitySettings);
        MaterialButton selectAppsButton = findViewById(R.id.buttonSelectApps);

        stepsSlider.setLabelFormatter(value -> formatPercent(roundToStepPercent(value)));

        bindCurrentSettings();

        featureSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setFeatureEnabled(isChecked);
            boolean serviceEnabled = updateServiceStatus();
            updateSettingsEnabledState(isChecked, serviceEnabled, settingsRepository.isAppFilterEnabled());
        });

        appFilterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsRepository.setAppFilterEnabled(isChecked);
            updateSelectedAppsSummary(isChecked, settingsRepository.getAllowedAppPackages().size());
            boolean serviceEnabled = updateServiceStatus();
            updateSettingsEnabledState(settingsRepository.isFeatureEnabled(), serviceEnabled, isChecked);
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

        selectAppsButton.setOnClickListener(v -> showAppSelectionDialog());

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
        boolean appFilterEnabled = settingsRepository.isAppFilterEnabled();
        int selectedAppsCount = settingsRepository.getAllowedAppPackages().size();
        float scrollAmount = settingsRepository.getScrollAmount();
        int scrollPercent = amountToPercent(scrollAmount);

        featureSwitch.setChecked(featureEnabled);
        appFilterSwitch.setChecked(appFilterEnabled);
        invertDirectionSwitch.setChecked(invertEnabled);
        dualKeyToggleSwitch.setChecked(dualKeyToggleEnabled);
        stepsSlider.setValue(scrollPercent);
        stepsValueText.setText(formatPercent(scrollPercent));
        updateSelectedAppsSummary(appFilterEnabled, selectedAppsCount);
        boolean serviceEnabled = updateServiceStatus();
        updateSettingsEnabledState(featureEnabled, serviceEnabled, appFilterEnabled);
    }

    private void updateSettingsEnabledState(boolean featureEnabled, boolean serviceEnabled, boolean appFilterEnabled) {
        boolean dualToggleEnabled = serviceEnabled;
        boolean mainToggleEnabled = serviceEnabled;
        boolean appFilterToggleEnabled = serviceEnabled && featureEnabled;
        boolean appPickerEnabled = appFilterToggleEnabled && appFilterEnabled;
        boolean secondaryEnabled = serviceEnabled && featureEnabled;
        MaterialButton selectAppsButton = findViewById(R.id.buttonSelectApps);

        featureSwitch.setEnabled(mainToggleEnabled);
        dualKeyToggleSwitch.setEnabled(dualToggleEnabled);
        appFilterSwitch.setEnabled(appFilterToggleEnabled);
        selectAppsButton.setEnabled(appPickerEnabled);
        invertDirectionSwitch.setEnabled(secondaryEnabled);
        stepsSlider.setEnabled(secondaryEnabled);
        stepsLabelText.setEnabled(secondaryEnabled);
        stepsValueText.setEnabled(secondaryEnabled);
        selectedAppsSummaryText.setEnabled(appPickerEnabled);

        float dualAlpha = dualToggleEnabled ? 1.0f : 0.45f;
        float mainAlpha = mainToggleEnabled ? 1.0f : 0.45f;
        float appFilterAlpha = appFilterToggleEnabled ? 1.0f : 0.45f;
        float appPickerAlpha = appPickerEnabled ? 1.0f : 0.45f;
        float alpha = secondaryEnabled ? 1.0f : 0.45f;

        dualKeyToggleSwitch.setAlpha(dualAlpha);
        featureSwitch.setAlpha(mainAlpha);
        appFilterSwitch.setAlpha(appFilterAlpha);
        selectAppsButton.setAlpha(appPickerAlpha);
        invertDirectionSwitch.setAlpha(alpha);
        stepsSlider.setAlpha(alpha);
        stepsLabelText.setAlpha(alpha);
        stepsValueText.setAlpha(alpha);
        selectedAppsSummaryText.setAlpha(appPickerAlpha);
    }

    private void updateSelectedAppsSummary(boolean appFilterEnabled, int selectedAppsCount) {
        if (!appFilterEnabled) {
            selectedAppsSummaryText.setText(R.string.hint_selected_apps_off);
            return;
        }
        if (selectedAppsCount <= 0) {
            selectedAppsSummaryText.setText(R.string.hint_selected_apps_none);
            return;
        }
        selectedAppsSummaryText.setText(getString(R.string.hint_selected_apps_count, selectedAppsCount));
    }

    private void showAppSelectionDialog() {
        List<AppEntry> launchableApps = getLaunchableApps();
        Set<String> currentSelectedPackages = settingsRepository.getAllowedAppPackages();

        CharSequence[] labels = new CharSequence[launchableApps.size()];
        boolean[] checked = new boolean[launchableApps.size()];
        for (int i = 0; i < launchableApps.size(); i++) {
            AppEntry app = launchableApps.get(i);
            labels[i] = app.label;
            checked[i] = currentSelectedPackages.contains(app.packageName);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_select_apps_title)
                .setMultiChoiceItems(labels, checked, (dialogInterface, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.action_save, (dialogInterface, which) -> {
                    Set<String> selectedPackages = new HashSet<>();
                    for (int i = 0; i < launchableApps.size(); i++) {
                        if (checked[i]) {
                            selectedPackages.add(launchableApps.get(i).packageName);
                        }
                    }
                    settingsRepository.setAllowedAppPackages(selectedPackages);
                    updateSelectedAppsSummary(settingsRepository.isAppFilterEnabled(), selectedPackages.size());
                })
                .setNeutralButton(R.string.action_actions, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                PopupMenu menu = new PopupMenu(this, view);
                Menu popupMenu = menu.getMenu();
                popupMenu.add(0, APP_SELECTION_ACTION_SELECT_ALL, 0, R.string.action_select_all);
                popupMenu.add(0, APP_SELECTION_ACTION_INVERT, 1, R.string.action_invert_selection);
                popupMenu.add(0, APP_SELECTION_ACTION_CLEAR, 2, R.string.action_clear);

                menu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == APP_SELECTION_ACTION_SELECT_ALL) {
                        setCheckedStates(dialog, checked, true);
                        return true;
                    }
                    if (itemId == APP_SELECTION_ACTION_INVERT) {
                        invertCheckedStates(dialog, checked);
                        return true;
                    }
                    if (itemId == APP_SELECTION_ACTION_CLEAR) {
                        setCheckedStates(dialog, checked, false);
                        return true;
                    }
                    return false;
                });
                menu.show();
            });
        });

        dialog.show();
    }

    private void setCheckedStates(AlertDialog dialog, boolean[] checked, boolean checkedState) {
        ListView listView = dialog.getListView();
        for (int i = 0; i < checked.length; i++) {
            checked[i] = checkedState;
            listView.setItemChecked(i, checkedState);
        }
    }

    private void invertCheckedStates(AlertDialog dialog, boolean[] checked) {
        ListView listView = dialog.getListView();
        for (int i = 0; i < checked.length; i++) {
            checked[i] = !checked[i];
            listView.setItemChecked(i, checked[i]);
        }
    }

    private List<AppEntry> getLaunchableApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(intent, 0);
        List<AppEntry> apps = new ArrayList<>();
        Set<String> seenPackages = new HashSet<>();

        for (ResolveInfo resolveInfo : resolveInfos) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (packageName == null || seenPackages.contains(packageName)) {
                continue;
            }
            seenPackages.add(packageName);

            CharSequence label = resolveInfo.loadLabel(getPackageManager());
            String labelText = label == null ? packageName : label.toString();
            apps.add(new AppEntry(labelText, packageName));
        }

        apps.sort((left, right) -> left.label.compareToIgnoreCase(right.label));
        return apps;
    }

    private static final class AppEntry {
        final String label;
        final String packageName;

        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
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
