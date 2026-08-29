package com.cybr47.instar.mods.ads;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import com.cybr47.instar.utils.core.DexKitCache;
import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.log.ModuleLog;

public class AdBlocker {

    // Marker string referenced inside the ad-insertion decision method.
    // IG >= 437 refactored SponsoredContentController and dropped the old
    // "SponsoredContentController.insertItem" trace tag, so we try the new
    // marker first and fall back to the legacy one for older installs.
    private static final String[] INSERT_ITEM_MARKERS = {
            "Is ad pod",
            "SponsoredContentController.insertItem"
    };

    public void disableSponsoredContent(DexKitBridge bridge, ClassLoader classLoader) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (FeatureFlags.isAdBlockEnabled) param.setResult(false);
            }
        };

        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("AdBlocker", classLoader);
            if (cached != null) {
                XposedBridge.hookMethod(cached, hook);
                FeatureStatusTracker.setHooked("AdBlocker");
                return;
            }
        }

        try {
            for (String marker : INSERT_ITEM_MARKERS) {
                List<MethodData> methods = bridge.findMethod(
                        FindMethod.create().matcher(
                                MethodMatcher.create().usingStrings(marker)
                        )
                );

                if (methods.isEmpty()) {
                    ModuleLog.line("(Instar | AdBlocker): ⚠️ No methods found referencing '" + marker + "'");
                    continue;
                }

                for (MethodData method : methods) {
                    String returnType = String.valueOf(method.getReturnType());
                    if (!returnType.contains("boolean")) continue;

                    try {
                        Method targetMethod = method.getMethodInstance(classLoader);
                        DexKitCache.saveMethod("AdBlocker", targetMethod);
                        XposedBridge.hookMethod(targetMethod, hook);

                        ModuleLog.line("(Instar | AdBlocker): ✅ Hooked (dynamic check, marker='" + marker + "'): " +
                                method.getClassName() + "." + method.getName());
                        FeatureStatusTracker.setHooked("AdBlocker");
                        return; // Stop after first successful hook

                    } catch (Throwable hookEx) {
                        ModuleLog.line("(Instar | AdBlocker): ❌ Failed to hook: " +
                                method.getName() + " → " + hookEx.getMessage());
                    }
                }
            }

            ModuleLog.line("(Instar | AdBlocker): ❌ No valid methods hooked (all markers exhausted).");

        } catch (Throwable t) {
            ModuleLog.line("(Instar | AdBlocker): ❌ Exception: " + t.getMessage());
        }
    }
}
