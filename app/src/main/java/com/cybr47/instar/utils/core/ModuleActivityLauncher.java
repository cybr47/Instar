package com.cybr47.instar.utils.core;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import de.robv.android.xposed.XposedBridge;
import com.cybr47.instar.mods.ui.UIHookManager;
import com.cybr47.instar.utils.log.ModuleLog;

/**
 * Launches one of Instar's own Activities (e.g. LocationPickerActivity) from code
 * running inside Instagram's hooked process. Requires the target Activity to be exported,
 * since it's a separate app/uid from Instagram's.
 */
public final class ModuleActivityLauncher {

    private ModuleActivityLauncher() {}

    public static boolean launch(Context context, String activityClassName, Bundle extras) {
        Activity activity = UIHookManager.getCurrentActivity();
        if (activity == null || activity.isFinishing()) {
            ModuleLog.line("(Instar | Launcher): no active Instagram activity");
            return false;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(CommonUtils.MY_PACKAGE_NAME, activityClassName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (extras != null) intent.putExtras(extras);
            activity.startActivity(intent);
            return true;
        } catch (Throwable t) {
            ModuleLog.line("(Instar | Launcher): failed " + activityClassName + " -> " + t.getMessage());
            return false;
        }
    }
}
