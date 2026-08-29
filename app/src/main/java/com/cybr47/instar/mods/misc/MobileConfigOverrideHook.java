package com.cybr47.instar.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.log.ModuleLog;

/**
 * Runtime equivalent of Piko's MobileConfig boolean override bridge.
 *
 * Instagram 435 exposes native flags for hiding Reel like counts and reshare
 * counts. Comment/share/save counters do not have matching flags in the 435
 * mapping, so HideReelsUfiCountsHook supplies a UI-scoped fallback for them.
 */
public final class MobileConfigOverrideHook {

    private static final String MOBILE_CONFIG_CLASS =
            "com.facebook.mobileconfig.factory.MobileConfigUnsafeContext";
    private static final String UNIVERSAL_ID_HELPER_CLASS = "X.0B3D";

    // Instagram 435 MetaConfig IDs from Piko's generated mapping.
    private static final String HIDE_REELS_LIKE_COUNTS = "47643::3";
    private static final String HIDE_RESHARE_COUNTS_PRODUCTION = "75216::1";
    private static final String HIDE_RESHARE_COUNTS_CONSUMPTION = "75216::2";
    private static final String EMPLOYEE_OPTIONS_ENABLED = "28538::0";

    private static final String NO_OVERRIDE = "-";
    private static final Map<Long, String> SPECIFIER_CACHE = new ConcurrentHashMap<>();

    private Method universalIdMethod;

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            universalIdMethod = resolveUniversalIdMethod(classLoader);
            if (universalIdMethod == null) {
                ModuleLog.line("(Instar | MobileConfig): Universal ID helper was not found");
                return;
            }

            List<MethodData> getters = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(MOBILE_CONFIG_CLASS)
                            .returnType("boolean")
                            .paramTypes("long")));

            int hooked = 0;
            for (MethodData getter : getters) {
                try {
                    Method method = getter.getMethodInstance(classLoader);
                    method.setAccessible(true);
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args.length == 0) return;
                            Object raw = param.args[0];
                            if (!(raw instanceof Long)) return;

                            String configId = configId((Long) raw);
                            if (FeatureFlags.isDevEnabled && EMPLOYEE_OPTIONS_ENABLED.equals(configId)) {
                                param.setResult(true);
                                FeatureStatusTracker.setHooked("DevOptions");
                            } else if (FeatureFlags.hideReelsUfiCounts
                                    && (HIDE_REELS_LIKE_COUNTS.equals(configId)
                                    || HIDE_RESHARE_COUNTS_PRODUCTION.equals(configId)
                                    || HIDE_RESHARE_COUNTS_CONSUMPTION.equals(configId))) {
                                param.setResult(true);
                                FeatureStatusTracker.setHooked("HideReelsUfiCounts");
                            }
                        }
                    });
                    hooked++;
                } catch (Throwable error) {
                    ModuleLog.line("(Instar | MobileConfig): Getter hook failed: " + error.getMessage());
                }
            }

            ModuleLog.line("(Instar | MobileConfig): Hooked " + hooked + " boolean getter(s)");
        } catch (Throwable error) {
            ModuleLog.line("(Instar | MobileConfig): Install failed: " + error.getMessage());
        }
    }

    private Method resolveUniversalIdMethod(ClassLoader classLoader) {
        try {
            Class<?> helper = classLoader.loadClass(UNIVERSAL_ID_HELPER_CLASS);
            for (Method method : helper.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == int.class
                        && params.length == 1
                        && params[0] == long.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Throwable error) {
            ModuleLog.line("(Instar | MobileConfig): ID helper lookup failed: " + error.getMessage());
        }
        return null;
    }

    private String configId(long specifier) {
        String cached = SPECIFIER_CACHE.get(specifier);
        if (cached != null) return cached;

        String value = NO_OVERRIDE;
        try {
            int universalId = (Integer) universalIdMethod.invoke(null, specifier);
            long shifted = specifier >>> 16;
            boolean wideParam = ((specifier >>> 62) & 1L) == 1L;
            long paramId = wideParam ? (shifted & 0xffffL) : (shifted & 0xfffL);
            String candidate = universalId + "::" + paramId;
            if (HIDE_REELS_LIKE_COUNTS.equals(candidate)
                    || HIDE_RESHARE_COUNTS_PRODUCTION.equals(candidate)
                    || HIDE_RESHARE_COUNTS_CONSUMPTION.equals(candidate)
                    || EMPLOYEE_OPTIONS_ENABLED.equals(candidate)) {
                value = candidate;
            }
        } catch (Throwable ignored) {
        }

        SPECIFIER_CACHE.put(specifier, value);
        return value;
    }
}
