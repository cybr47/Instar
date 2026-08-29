package com.cybr47.instar.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String CONFIG_WRAPPER_ANCHOR = "__fbt_null__";

    // Instagram 435 MetaConfig IDs from Piko's generated mapping.
    private static final String HIDE_REELS_LIKE_COUNTS = "47643::3";
    private static final String HIDE_RESHARE_COUNTS_PRODUCTION = "75216::1";
    private static final String HIDE_RESHARE_COUNTS_CONSUMPTION = "75216::2";
    private static final String EMPLOYEE_OPTIONS_ENABLED = "28538::0";

    private static final String NO_OVERRIDE = "-";
    private static final Map<Long, String> SPECIFIER_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> HOOKED_METHODS = ConcurrentHashMap.newKeySet();

    private Method universalIdMethod;

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            universalIdMethod = resolveUniversalIdMethod(classLoader);
            if (universalIdMethod == null) {
                ModuleLog.line("(Instar | MobileConfig): Universal ID helper was not found");
                return;
            }

            int hooked = hookPikoAccessorPath(bridge, classLoader);

            // Keep the raw MobileConfigUnsafeContext lookup as a compatibility fallback.
            List<MethodData> getters = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .declaredClass(MOBILE_CONFIG_CLASS)
                            .returnType("boolean")
                            .paramTypes("long")));

            for (MethodData getter : getters) {
                try {
                    Method method = getter.getMethodInstance(classLoader);
                    if (hookBooleanAccessor(method)) hooked++;
                } catch (Throwable error) {
                    ModuleLog.line("(Instar | MobileConfig): Getter hook failed: " + error.getMessage());
                }
            }

            ModuleLog.line("(Instar | MobileConfig): Hooked " + hooked
                    + " boolean accessor(s), including Piko wrapper path");
        } catch (Throwable error) {
            ModuleLog.line("(Instar | MobileConfig): Install failed: " + error.getMessage());
        }
    }

    /**
     * Instagram 435 reads most flags through an obfuscated three-argument wrapper rather than
     * calling MobileConfigUnsafeContext directly. Piko identifies its owner through the stable
     * __fbt_null__ string; mirroring that path makes both Developer Options and native UFI flags
     * work under LSPosed as well as LSPatch.
     */
    private int hookPikoAccessorPath(DexKitBridge bridge, ClassLoader classLoader) {
        int hooked = 0;
        try {
            List<MethodData> anchors = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings(CONFIG_WRAPPER_ANCHOR)));
            for (MethodData anchor : anchors) {
                try {
                    Method anchorMethod = anchor.getMethodInstance(classLoader);
                    Class<?>[] anchorParams = anchorMethod.getParameterTypes();
                    if (anchorParams.length == 0) continue;
                    Class<?> wrapperType = anchorParams[0];

                    for (Method candidate : anchorMethod.getDeclaringClass().getDeclaredMethods()) {
                        Class<?>[] params = candidate.getParameterTypes();
                        if (candidate.getReturnType() != boolean.class
                                || params.length != 3 || params[0] != wrapperType
                                || !containsLong(params)) {
                            continue;
                        }
                        if (hookBooleanAccessor(candidate)) hooked++;
                    }
                } catch (Throwable error) {
                    ModuleLog.line("(Instar | MobileConfig): Wrapper candidate failed: "
                            + error.getMessage());
                }
            }
        } catch (Throwable error) {
            ModuleLog.line("(Instar | MobileConfig): Wrapper discovery failed: "
                    + error.getMessage());
        }
        return hooked;
    }

    private boolean hookBooleanAccessor(Method method) {
        String signature = method.toGenericString();
        if (!HOOKED_METHODS.add(signature)) return false;
        try {
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    for (Object argument : param.args) {
                        if (!(argument instanceof Long)) continue;
                        if (applyOverride(param, (Long) argument)) return;
                    }
                }
            });
            return true;
        } catch (Throwable error) {
            HOOKED_METHODS.remove(signature);
            ModuleLog.line("(Instar | MobileConfig): Accessor hook failed: "
                    + error.getMessage());
            return false;
        }
    }

    private boolean applyOverride(XC_MethodHook.MethodHookParam param, long specifier) {
        String configId = configId(specifier);
        if (FeatureFlags.isDevEnabled && EMPLOYEE_OPTIONS_ENABLED.equals(configId)) {
            param.setResult(true);
            FeatureStatusTracker.setHooked("DevOptions");
            return true;
        } else if (FeatureFlags.hideReelsUfiCounts
                && (HIDE_REELS_LIKE_COUNTS.equals(configId)
                || HIDE_RESHARE_COUNTS_PRODUCTION.equals(configId)
                || HIDE_RESHARE_COUNTS_CONSUMPTION.equals(configId))) {
            param.setResult(true);
            FeatureStatusTracker.setHooked("HideReelsUfiCounts");
            return true;
        }
        return false;
    }

    private static boolean containsLong(Class<?>[] parameterTypes) {
        for (Class<?> parameterType : parameterTypes) {
            if (parameterType == long.class || parameterType == Long.class) return true;
        }
        return false;
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
