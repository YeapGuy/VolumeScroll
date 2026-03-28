package com.yeapguy.volumescroll;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Gravity;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.graphics.Path;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

public class VolumeScrollAccessibilityService extends AccessibilityService {

    private static final long CHORD_WINDOW_MS = 50L;

    private SettingsRepository settingsRepository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean volumeUpPressed = false;
    private boolean volumeDownPressed = false;
    private boolean volumeUpPassedToSystem = false;
    private boolean volumeDownPassedToSystem = false;
    private boolean chordConsumed = false;
    private String latestForegroundPackage;
    private int pendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private int pendingToken = 0;
    private View feedbackOverlayView;
    private WindowManager windowManager;
    private final Runnable removeFeedbackOverlayRunnable = this::removeFeedbackOverlay;

    @Override
    public void onCreate() {
        super.onCreate();
        settingsRepository = new SettingsRepository(this);
        windowManager = getSystemService(WindowManager.class);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.flags = info.flags
                | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 50;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        latestForegroundPackage = event.getPackageName().toString();
    }

    @Override
    public void onInterrupt() {
        cancelPendingScroll();
        removeFeedbackOverlay();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeFeedbackOverlay();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event);
        }

        boolean featureEnabled = settingsRepository.isFeatureEnabled();
        boolean shortcutToggleEnabled = settingsRepository.isShortcutToggleEnabled();

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                return featureEnabled;
            }

            setPressedState(keyCode, true);

            if (shortcutToggleEnabled && volumeUpPressed && volumeDownPressed) {
                cancelPendingScroll();
                if (!chordConsumed) {
                    toggleFeatureEnabled();
                    chordConsumed = true;
                }
                return true;
            }

            if (!featureEnabled) {
                // Feature is off: do not consume single volume keys so Android can adjust volume.
                markKeyPassedToSystem(keyCode, true);
                return false;
            }

            if (!isScrollAllowedForForegroundApp()) {
                // App filter is enabled and current app is not allowed: pass volume keys through.
                markKeyPassedToSystem(keyCode, true);
                return false;
            }

            if (shortcutToggleEnabled) {
                schedulePendingScroll(keyCode);
            } else {
                cancelPendingScroll();
                executeScrollForKeyCode(keyCode);
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            setPressedState(keyCode, false);
            if (wasKeyPassedToSystem(keyCode)) {
                markKeyPassedToSystem(keyCode, false);
                return false;
            }
            if (!volumeUpPressed && !volumeDownPressed) {
                chordConsumed = false;
            }
            return featureEnabled;
        }

        return super.onKeyEvent(event);
    }

    private void schedulePendingScroll(int keyCode) {
        cancelPendingScroll();
        pendingKeyCode = keyCode;
        int token = ++pendingToken;
        handler.postDelayed(() -> executePendingScroll(token), CHORD_WINDOW_MS);
    }

    private void executePendingScroll(int token) {
        if (token != pendingToken) {
            return;
        }

        if (volumeUpPressed && volumeDownPressed) {
            return;
        }

        int keyCode = pendingKeyCode;
        pendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;

        executeScrollForKeyCode(keyCode);
    }

    private void executeScrollForKeyCode(int keyCode) {
        if (!settingsRepository.isFeatureEnabled()) {
            return;
        }
        if (!isScrollAllowedForForegroundApp()) {
            return;
        }

        boolean shouldScrollForward = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
        if (settingsRepository.isInvertDirectionEnabled()) {
            shouldScrollForward = !shouldScrollForward;
        }

        boolean handled = performSmoothScrollWithGesture(
                shouldScrollForward,
                settingsRepository.getScrollAmount()
        );
        if (!handled) {
            handled = performScrollWithNodeActions(shouldScrollForward);
        }
    }

    private boolean isScrollAllowedForForegroundApp() {
        if (!settingsRepository.isAppFilterEnabled()) {
            return true;
        }

        Set<String> allowedPackages = settingsRepository.getAllowedAppPackages();
        if (allowedPackages.isEmpty()) {
            return false;
        }

        String foregroundPackage = getForegroundPackageName();
        if (foregroundPackage == null || foregroundPackage.isEmpty()) {
            return false;
        }
        return allowedPackages.contains(foregroundPackage);
    }

    private String getForegroundPackageName() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) {
            return root.getPackageName().toString();
        }
        return latestForegroundPackage;
    }

    private void cancelPendingScroll() {
        pendingToken++;
        pendingKeyCode = KeyEvent.KEYCODE_UNKNOWN;
        handler.removeCallbacksAndMessages(null);
    }

    private void setPressedState(int keyCode, boolean pressed) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressed = pressed;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPressed = pressed;
        }
    }

    private void markKeyPassedToSystem(int keyCode, boolean passedToSystem) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPassedToSystem = passedToSystem;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPassedToSystem = passedToSystem;
        }
    }

    private boolean wasKeyPassedToSystem(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return volumeUpPassedToSystem;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return volumeDownPassedToSystem;
        }
        return false;
    }

    private void toggleFeatureEnabled() {
        boolean newEnabled = !settingsRepository.isFeatureEnabled();
        settingsRepository.setFeatureEnabled(newEnabled);
        handler.post(() -> {
            int feedbackRes = newEnabled
                    ? R.string.overlay_shortcut_enabled
                    : R.string.overlay_shortcut_disabled;
            showFeedbackMessage(feedbackRes);
            vibrateToggleFeedback();
        });
    }

    private void showFeedbackMessage(int messageResId) {
        String message = getString(messageResId);
        showFeedbackOverlay(message);
    }

    private void showFeedbackOverlay(String message) {
        if (windowManager == null) {
            return;
        }

        removeFeedbackOverlay();

        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(15f);
        textView.setPadding(34, 22, 34, 22);
        textView.setBackgroundColor(0xD9202020);

        FrameLayout container = new FrameLayout(this);
        container.setClickable(false);
        container.setFocusable(false);
        container.addView(textView);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 110;

        try {
            windowManager.addView(container, params);
            feedbackOverlayView = container;
            handler.postDelayed(removeFeedbackOverlayRunnable, 1200L);
        } catch (RuntimeException ignored) {
            feedbackOverlayView = null;
        }
    }

    private void removeFeedbackOverlay() {
        handler.removeCallbacks(removeFeedbackOverlayRunnable);
        if (feedbackOverlayView == null || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(feedbackOverlayView);
        } catch (RuntimeException ignored) {
            // View can already be detached on rapid toggle spam.
        }
        feedbackOverlayView = null;
    }

    private void vibrateToggleFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = getSystemService(VibratorManager.class);
            if (vibratorManager == null) {
                return;
            }
            Vibrator vibrator = vibratorManager.getDefaultVibrator();
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE));
            return;
        }

        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private boolean performSmoothScrollWithGesture(boolean forward, float scrollAmount) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        if (width <= 0 || height <= 0) {
            return false;
        }

        float safeAmount = Math.max(6.125f, Math.min(scrollAmount, 10.5f));

        float distanceRatio = 0.04f + ((safeAmount - 3.0f) * 0.035f);
        float distancePx = height * distanceRatio;
        float x = width * 0.5f;
        float centerY = height * 0.5f;
        float halfDistance = distancePx * 0.5f;
        float startY = forward ? centerY + halfDistance : centerY - halfDistance;
        float endY = forward ? centerY - halfDistance : centerY + halfDistance;

        long duration = 130L;
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration, false);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean performScrollWithNodeActions(boolean forward) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }

        AccessibilityNodeInfo target = findBestScrollableNode(root, forward);
        if (target == null) {
            return false;
        }

        int action = forward
                ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId()
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId();

        return target.performAction(action);
    }

    private AccessibilityNodeInfo findBestScrollableNode(AccessibilityNodeInfo root, boolean forward) {
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        AccessibilityNodeInfo fromFocusedTree = findScrollableInAncestors(focused, forward);
        if (fromFocusedTree != null) {
            return fromFocusedTree;
        }

        AccessibilityNodeInfo accessibilityFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        AccessibilityNodeInfo fromA11yFocusedTree = findScrollableInAncestors(accessibilityFocused, forward);
        if (fromA11yFocusedTree != null) {
            return fromA11yFocusedTree;
        }

        return breadthFirstScrollableSearch(root, forward);
    }

    private AccessibilityNodeInfo findScrollableInAncestors(AccessibilityNodeInfo node, boolean forward) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (canScroll(current, forward)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo breadthFirstScrollableSearch(AccessibilityNodeInfo root, boolean forward) {
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node == null) {
                continue;
            }

            if (canScroll(node, forward)) {
                return node;
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    queue.addLast(child);
                }
            }
        }
        return null;
    }

    private boolean canScroll(AccessibilityNodeInfo node, boolean forward) {
        if (!node.isScrollable()) {
            return false;
        }
        int requiredActionId = forward
                ? AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId()
                : AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId();

        List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
        if (actions == null) {
            return false;
        }

        for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
            if (action != null && action.getId() == requiredActionId) {
                return true;
            }
        }
        return false;
    }
}
