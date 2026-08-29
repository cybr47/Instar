package com.cybr47.instar.mods.distraction;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.log.ModuleLog;

/** Ports Piko's resource-anchored Notes and Stories tray hiding without adding Piko UI. */
public final class HidePikoUiElementsHook {

    private static volatile int notesTrayId;
    private static volatile int storiesTrayId;
    private static volatile int storiesSpacerId;

    public void install() {
        try {
            XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!FeatureFlags.hideNotesTray && !FeatureFlags.hideStoriesTray) return;
                    View view = (View) param.thisObject;
                    cacheIds(view);
                    int id = view.getId();

                    if (FeatureFlags.hideNotesTray && notesTrayId != 0 && id == notesTrayId) {
                        view.setVisibility(View.GONE);
                        FeatureStatusTracker.setHooked("HideNotesTray");
                    }
                    if (FeatureFlags.hideStoriesTray
                            && ((storiesTrayId != 0 && id == storiesTrayId)
                            || (storiesSpacerId != 0 && id == storiesSpacerId))) {
                        view.setVisibility(View.GONE);
                        FeatureStatusTracker.setHooked("HideStoriesTray");
                    }
                }
            });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | PikoUI): Install failed: " + error.getMessage());
        }
    }

    private static void cacheIds(View view) {
        try {
            String pkg = view.getContext().getPackageName();
            if (notesTrayId == 0) {
                notesTrayId = view.getResources().getIdentifier("cf_hub_recycler_view", "id", pkg);
            }
            if (storiesTrayId == 0) {
                storiesTrayId = view.getResources().getIdentifier("litho_main_feed_stories_tray", "id", pkg);
            }
            if (storiesSpacerId == 0) {
                storiesSpacerId = view.getResources().getIdentifier("floating_tray_spacer", "id", pkg);
            }
        } catch (Throwable ignored) {
        }
    }
}
