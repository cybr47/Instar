package com.cybr47.instar.mods.misc;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.log.ModuleLog;

/** Hides numeric labels inside the Reels UFI while leaving every action icon clickable. */
public final class HideReelsUfiCountsHook {

    private static final String REELS_UFI_ID = "clips_ufi_component";
    private static final String REELS_LIKE_ID = "like_button";
    private static final Map<View, Boolean> OBSERVED_CONTAINERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile int reelsUfiId;
    private static volatile int reelsLikeId;

    public void install() {
        hookTextBinding();
        hookUfiAttachment();
    }

    private void hookTextBinding() {
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText",
                    CharSequence.class, TextView.BufferType.class, boolean.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!FeatureFlags.hideReelsUfiCounts) return;
                            TextView view = (TextView) param.thisObject;
                            CharSequence text = (CharSequence) param.args[0];
                            if (!looksLikeCount(text) || !isInsideReelsUfi(view)) return;
                            param.args[0] = "";
                            FeatureStatusTracker.setHooked("HideReelsUfiCounts");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!FeatureFlags.hideReelsUfiCounts) return;
                            TextView view = (TextView) param.thisObject;
                            if (isInsideReelsUfi(view) && view.getText().length() == 0
                                    && !hasCompoundDrawable(view)) {
                                view.setVisibility(View.GONE);
                            }
                        }
                    });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsUFI): Text binding hook failed: " + error.getMessage());
        }
    }

    private void hookUfiAttachment() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlags.hideReelsUfiCounts) return;
                    View view = (View) param.thisObject;
                    cacheIds(view);
                    boolean ufiRoot = reelsUfiId != 0 && view.getId() == reelsUfiId;
                    boolean likeAnchor = reelsLikeId != 0 && view.getId() == reelsLikeId;
                    if (!ufiRoot && !likeAnchor) return;

                    View container = ufiRoot ? view : findLikelyUfiContainer(view);
                    int cleared = clearCounts(container);
                    if (OBSERVED_CONTAINERS.put(container, Boolean.TRUE) == null) {
                        container.addOnLayoutChangeListener((v, left, top, right, bottom,
                                oldLeft, oldTop, oldRight, oldBottom) -> {
                            if (FeatureFlags.hideReelsUfiCounts) clearCounts(v);
                        });
                    }
                    if (cleared > 0) FeatureStatusTracker.setHooked("HideReelsUfiCounts");
                }
            });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsUFI): Attachment hook failed: " + error.getMessage());
        }
    }

    private static int clearCounts(View view) {
        if (view instanceof TextView textView && looksLikeCount(textView.getText())) {
            textView.setText("");
            if (!hasCompoundDrawable(textView)) textView.setVisibility(View.GONE);
            return 1;
        }
        int cleared = 0;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                cleared += clearCounts(group.getChildAt(i));
            }
        }
        return cleared;
    }

    private static boolean isInsideReelsUfi(View view) {
        cacheIds(view);
        int depth = 0;
        for (View current = view; current != null && depth++ < 6; ) {
            if (reelsUfiId != 0 && current.getId() == reelsUfiId) return true;
            if (reelsLikeId != 0 && current instanceof ViewGroup
                    && current.findViewById(reelsLikeId) != null) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static View findLikelyUfiContainer(View anchor) {
        android.view.ViewParent immediateParent = anchor.getParent();
        View fallback = immediateParent instanceof View ? (View) immediateParent : anchor;
        int depth = 0;
        for (View current = anchor; current != null && depth < 6; depth++) {
            if (reelsUfiId != 0 && current.getId() == reelsUfiId) return current;
            if (current instanceof ViewGroup && countNumericTexts(current, 0) >= 2) {
                return current;
            }
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return fallback;
    }

    private static int countNumericTexts(View view, int depth) {
        if (depth > 5) return 0;
        if (view instanceof TextView textView) return looksLikeCount(textView.getText()) ? 1 : 0;
        int count = 0;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount() && count < 2; i++) {
                count += countNumericTexts(group.getChildAt(i), depth + 1);
            }
        }
        return count;
    }

    private static void cacheIds(View view) {
        if ((reelsUfiId != 0 && reelsLikeId != 0) || view == null) return;
        try {
            if (reelsUfiId == 0) {
                reelsUfiId = view.getResources().getIdentifier(
                        REELS_UFI_ID, "id", view.getContext().getPackageName());
            }
            if (reelsLikeId == 0) {
                reelsLikeId = view.getResources().getIdentifier(
                        REELS_LIKE_ID, "id", view.getContext().getPackageName());
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean looksLikeCount(CharSequence value) {
        if (value == null) return false;
        String text = value.toString().trim();
        if (text.isEmpty() || text.length() > 18) return false;

        boolean hasDigit = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                hasDigit = true;
                continue;
            }
            if (Character.isWhitespace(c) || c == '.' || c == ',' || c == '\''
                    || c == '’' || c == 'K' || c == 'k' || c == 'M' || c == 'm'
                    || c == 'B' || c == 'b' || c == 'T' || c == 't'
                    || c == '万' || c == '億' || c == '亿' || c == '千') {
                continue;
            }
            return false;
        }
        return hasDigit;
    }

    private static boolean hasCompoundDrawable(TextView view) {
        for (Drawable drawable : view.getCompoundDrawablesRelative()) {
            if (drawable != null) return true;
        }
        return false;
    }
}
