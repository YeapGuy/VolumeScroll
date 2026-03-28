package com.yeapguy.volumescroll;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.view.accessibility.AccessibilityManager;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.List;

public final class AccessibilityStatusChecker {

    private AccessibilityStatusChecker() {
    }

    public static boolean isServiceEnabled(Context context, Class<?> serviceClass) {
        AccessibilityManager accessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (accessibilityManager != null) {
            List<AccessibilityServiceInfo> enabledServices =
                    accessibilityManager.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    );
            String expectedClassName = serviceClass.getName();
            String expectedPackageName = context.getPackageName();
            for (AccessibilityServiceInfo serviceInfo : enabledServices) {
                if (serviceInfo == null || serviceInfo.getResolveInfo() == null
                        || serviceInfo.getResolveInfo().serviceInfo == null) {
                    continue;
                }
                String packageName = serviceInfo.getResolveInfo().serviceInfo.packageName;
                String className = serviceInfo.getResolveInfo().serviceInfo.name;
                if (expectedPackageName.equals(packageName) && expectedClassName.equals(className)) {
                    return true;
                }
            }
        }

        // Fallback for devices that lag on AccessibilityManager state propagation.
        int accessibilityEnabled;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }

        if (accessibilityEnabled != 1) {
            return false;
        }

        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        String expectedService = new ComponentName(context, serviceClass).flattenToString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);

        while (splitter.hasNext()) {
            String service = splitter.next();
            if (expectedService.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }
}
