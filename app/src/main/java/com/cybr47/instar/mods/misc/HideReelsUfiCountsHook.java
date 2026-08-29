package com.cybr47.instar.mods.misc;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewParent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.log.ModuleLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hides the rendered count hosts in the Reels UFI without touching their action icons.
 *
 * Instagram 435 renders these labels as Litho ComponentHost views, not TextViews. The
 * resource entry name is therefore the useful stable fingerprint; the surrounding Clips
 * viewer hierarchy prevents an identically named feed view from being hidden.
 */
public final class HideReelsUfiCountsHook {

    private static final String HIDDEN_FIELD =
            "com.cybr47.instar.hide_reels_ufi_count";
    private static final String ORIGINAL_VISIBILITY_FIELD =
            "com.cybr47.instar.hide_reels_ufi_count_original_visibility";
    private static final int MAX_PARENT_DEPTH = 32;
    private static final int MAX_PROBED_RESOURCE_NAMES = 48;

    private static final Set<String> LOGGED_RESOURCE_NAMES = ConcurrentHashMap.newKeySet();
    private static final Set<String> PROBED_RESOURCE_NAMES = ConcurrentHashMap.newKeySet();

    public void install() {
        hookAttachment();
        hookIdAssignment();
        hookVisibilityUpdates();
    }

    private void hookAttachment() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            View view = (View) param.thisObject;
                            if (!FeatureFlags.hideReelsUfiCounts) {
                                restoreIfMarked(view);
                                return;
                            }

                            // Litho can finish assigning IDs/hierarchy after the host is attached.
                            inspectAndApply(view);
                            if (shouldDeferInspection(view)) {
                                view.post(() -> inspectAndApply(view));
                            }
                        }
                    });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsUFI): Attachment hook failed: "
                    + error.getMessage());
        }
    }

    private void hookIdAssignment() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "setId", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            View view = (View) param.thisObject;
                            if (!FeatureFlags.hideReelsUfiCounts) {
                                restoreIfMarked(view);
                                return;
                            }

                            // ComponentHost instances are recycled; re-evaluate whenever Litho
                            // assigns a new semantic ID so a former count host is not kept hidden.
                            inspectAndApply(view);
                            if (view.isAttachedToWindow()
                                    && shouldDeferInspection(view)) {
                                view.post(() -> inspectAndApply(view));
                            }
                        }
                    });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsUFI): ID hook failed: " + error.getMessage());
        }
    }

    private void hookVisibilityUpdates() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            View view = (View) param.thisObject;
                            if (!isMarked(view)) return;

                            if (FeatureFlags.hideReelsUfiCounts) {
                                // Litho may re-apply VISIBLE during a recycled component bind.
                                param.args[0] = View.GONE;
                            } else {
                                clearMark(view);
                            }
                        }
                    });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsUFI): Visibility hook failed: "
                    + error.getMessage());
        }
    }

    private static void inspectAndApply(View view) {
        if (view == null) return;
        if (!FeatureFlags.hideReelsUfiCounts) {
            restoreIfMarked(view);
            return;
        }

        String resourceName = resourceEntryName(view);
        boolean isCount = ReelsUfiViewMatcher.isCountResourceName(resourceName);
        boolean isLithoHost = ReelsUfiViewMatcher.isLithoComponentHostClassName(
                view.getClass().getName());
        if (!isCount && !isLithoHost) {
            restoreIfMarked(view);
            return;
        }

        boolean insideReelsViewer = isInsideReelsViewer(view);
        probeResourceName(view, resourceName, isLithoHost, insideReelsViewer);
        boolean shouldHide = isCount && insideReelsViewer;

        if (!shouldHide) {
            restoreIfMarked(view);
            return;
        }

        markAndHide(view);
        FeatureStatusTracker.setHooked("HideReelsUfiCounts");
        if (LOGGED_RESOURCE_NAMES.add(resourceName)) {
            ModuleLog.line("(Instar | ReelsUFI): Hidden count host " + resourceName
                    + " [" + view.getClass().getName() + "]");
        }
    }

    private static boolean shouldDeferInspection(View view) {
        return ReelsUfiViewMatcher.isCountResourceName(resourceEntryName(view))
                || ReelsUfiViewMatcher.isLithoComponentHostClassName(
                view.getClass().getName());
    }

    private static void probeResourceName(View view, String resourceName,
            boolean isLithoHost, boolean insideReelsViewer) {
        if (!isLithoHost || !insideReelsViewer || resourceName == null
                || PROBED_RESOURCE_NAMES.size() >= MAX_PROBED_RESOURCE_NAMES
                || !PROBED_RESOURCE_NAMES.add(resourceName)) {
            return;
        }
        ModuleLog.line("(Instar | ReelsUFI probe): ComponentHost id=" + resourceName
                + " [" + view.getClass().getName() + "]");
    }

    private static boolean isInsideReelsViewer(View view) {
        int depth = 0;
        for (View current = view; current != null && depth++ < MAX_PARENT_DEPTH; ) {
            String resourceName = resourceEntryName(current);
            if (ReelsUfiViewMatcher.isReelsRootResourceName(resourceName)
                    || ReelsUfiViewMatcher.isReelsHierarchyClassName(
                    current.getClass().getName())) {
                return true;
            }

            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static String resourceEntryName(View view) {
        int id = view.getId();
        if (id == View.NO_ID || id == 0) return null;
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Resources.NotFoundException | SecurityException ignored) {
            return null;
        } catch (Throwable ignored) {
            // Resource implementations can be vendor-wrapped; never risk breaking rendering.
            return null;
        }
    }

    private static void markAndHide(View view) {
        if (!isMarked(view)) {
            XposedHelpers.setAdditionalInstanceField(
                    view, ORIGINAL_VISIBILITY_FIELD, view.getVisibility());
            XposedHelpers.setAdditionalInstanceField(view, HIDDEN_FIELD, Boolean.TRUE);
        }
        if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
    }

    private static void restoreIfMarked(View view) {
        if (!isMarked(view)) return;
        Object original = XposedHelpers.getAdditionalInstanceField(
                view, ORIGINAL_VISIBILITY_FIELD);
        clearMark(view);
        if (original instanceof Integer && view.getVisibility() != (Integer) original) {
            view.setVisibility((Integer) original);
        }
    }

    private static boolean isMarked(View view) {
        return Boolean.TRUE.equals(
                XposedHelpers.getAdditionalInstanceField(view, HIDDEN_FIELD));
    }

    private static void clearMark(View view) {
        XposedHelpers.removeAdditionalInstanceField(view, HIDDEN_FIELD);
        XposedHelpers.removeAdditionalInstanceField(view, ORIGINAL_VISIBILITY_FIELD);
    }
}
