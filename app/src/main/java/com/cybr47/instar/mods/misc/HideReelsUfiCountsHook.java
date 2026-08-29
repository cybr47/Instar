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
    private static final Map<View, Boolean> OBSERVED_CONTAINERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile int reelsUfiId;

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
                    cacheUfiId(view);
                    if (reelsUfiId == 0 || view.getId() != reelsUfiId) return;

                    clearCounts(view);
                    if (OBSERVED_CONTAINERS.put(view, Boolean.TRUE) == null) {
                        view.addOnLayoutChangeListener((v, left, top, right, bottom,
                                oldLeft, oldTop, oldRight, oldBottom) -> {
                            if (FeatureFlags.hideReelsUfiCounts) clearCounts(v);
                        });
                    }
                    FeatureStatusTracker.setHooked("HideReelsUfiCounts");
                }
            });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsUFI): Attachment hook failed: " + error.getMessage());
        }
    }

    private static void clearCounts(View view) {
        if (view instanceof TextView textView && looksLikeCount(textView.getText())) {
            textView.setText("");
            if (!hasCompoundDrawable(textView)) textView.setVisibility(View.GONE);
            return;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                clearCounts(group.getChildAt(i));
            }
        }
    }

    private static boolean isInsideReelsUfi(View view) {
        cacheUfiId(view);
        if (reelsUfiId == 0) return false;
        for (View current = view; current != null; ) {
            if (current.getId() == reelsUfiId) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static void cacheUfiId(View view) {
        if (reelsUfiId != 0 || view == null) return;
        try {
            reelsUfiId = view.getResources().getIdentifier(
                    REELS_UFI_ID, "id", view.getContext().getPackageName());
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
