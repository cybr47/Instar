package com.cybr47.instar.mods.misc;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.log.ModuleLog;

/** Runtime port of Piko's "Hide reshare button" patch for Instagram 435. */
public final class HideRepostButtonHook {

    private static final String NOTES_TAG = "enable_media_notes_production";
    private static final int NOTES_TAG_HASH = NOTES_TAG.hashCode();
    private static final String LIVE_TREE_CLASS = "com.instagram.pando.livetree.LiveTreeJNI";
    private static final String LIVE_TREE_METHOD = "getOptionalBooleanValueByHashCode";

    private final Set<String> hooked = new HashSet<>();

    public void install(DexKitBridge bridge, ClassLoader classLoader) {
        hookFeedParser(bridge, classLoader);
        hookLiveTree(classLoader);
    }

    private void hookFeedParser(DexKitBridge bridge, ClassLoader classLoader) {
        try {
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings(NOTES_TAG)
                            .usingNumbers(NOTES_TAG_HASH)
                            .returnType("java.lang.Boolean")));
            for (MethodData data : methods) hookFalse(data.getMethodInstance(classLoader));
            ModuleLog.line("(Instar | HideRepost): Parser candidates: " + methods.size());
        } catch (Throwable error) {
            ModuleLog.line("(Instar | HideRepost): Parser discovery failed: " + error.getMessage());
        }
    }

    private void hookLiveTree(ClassLoader classLoader) {
        try {
            Class<?> liveTree = classLoader.loadClass(LIVE_TREE_CLASS);
            for (Method method : liveTree.getDeclaredMethods()) {
                if (!LIVE_TREE_METHOD.equals(method.getName())) continue;
                String signature = method.toGenericString();
                if (!hooked.add(signature)) continue;
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!FeatureFlags.disableRepost) return;
                        for (Object arg : param.args) {
                            if (arg instanceof Integer && ((Integer) arg) == NOTES_TAG_HASH) {
                                param.setResult(Boolean.FALSE);
                                FeatureStatusTracker.setHooked("HideRepostButton");
                                return;
                            }
                        }
                    }
                });
            }
        } catch (Throwable error) {
            ModuleLog.line("(Instar | HideRepost): LiveTree hook failed: " + error.getMessage());
        }
    }

    private void hookFalse(Method method) {
        try {
            String signature = method.toGenericString();
            if (!hooked.add(signature)) return;
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (FeatureFlags.disableRepost) {
                        param.setResult(Boolean.FALSE);
                        FeatureStatusTracker.setHooked("HideRepostButton");
                    }
                }
            });
        } catch (Throwable error) {
            ModuleLog.line("(Instar | HideRepost): Hook failed: " + error.getMessage());
        }
    }
}
