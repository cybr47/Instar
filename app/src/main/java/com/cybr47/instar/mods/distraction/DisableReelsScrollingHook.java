package com.cybr47.instar.mods.distraction;

import android.view.MotionEvent;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.log.ModuleLog;

/** Runtime port of Piko's Instagram 435 "Disable Reels scrolling" patch. */
public final class DisableReelsScrollingHook {

    private static final String VIEW_AT_INDEX_ANCHOR = "ClipsViewPagerImpl_getViewAtIndex";
    private static final String SWIPE_REFRESH_CLASS =
            "instagram.features.clips.viewer.ui.ClipsSwipeRefreshLayout";
    private static final String VIEW_PAGER_CLASS = "androidx.viewpager2.widget.ViewPager2";

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        hookPagerDiscovery(bridge, classLoader);
        hookPullToRefresh(classLoader);
    }

    private void hookPagerDiscovery(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            List<MethodData> candidates = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings(VIEW_AT_INDEX_ANCHOR)));
            for (MethodData data : candidates) {
                Method method = data.getMethodInstance(classLoader);
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!FeatureFlags.disableReelsScrolling || param.thisObject == null) return;
                        disablePagerInput(param.thisObject);
                    }
                });
            }
            ModuleLog.line("(Instar | ReelsScroll): Pager candidates: " + candidates.size());
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsScroll): Pager discovery failed: " + error.getMessage());
        }
    }

    private void hookPullToRefresh(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(SWIPE_REFRESH_CLASS, classLoader,
                    "onInterceptTouchEvent", MotionEvent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (FeatureFlags.disableReelsScrolling) param.setResult(false);
                        }
                    });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | ReelsScroll): Refresh hook failed: " + error.getMessage());
        }
    }

    private static void disablePagerInput(Object owner) {
        for (Class<?> type = owner.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!VIEW_PAGER_CLASS.equals(field.getType().getName())) continue;
                try {
                    field.setAccessible(true);
                    Object pager = field.get(owner);
                    if (pager == null) continue;
                    Method setter = pager.getClass().getMethod("setUserInputEnabled", boolean.class);
                    setter.invoke(pager, false);
                    FeatureStatusTracker.setHooked("DisableReelsScrolling");
                    return;
                } catch (Throwable error) {
                    ModuleLog.line("(Instar | ReelsScroll): Pager update failed: " + error.getMessage());
                }
            }
        }
    }
}
