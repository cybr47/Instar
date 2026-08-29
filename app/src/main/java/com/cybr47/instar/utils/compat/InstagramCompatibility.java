package com.cybr47.instar.utils.compat;

import android.content.Context;
import android.content.pm.PackageInfo;

import com.cybr47.instar.utils.core.CommonUtils;
import com.cybr47.instar.utils.log.ModuleLog;

/**
 * Central compatibility gate for every Instagram hook installed by Instar.
 *
 * Piko's current Instagram fingerprints target 435.0.0.37.76 (ARM64,
 * versionCode 384109456). Instar may continue to work on older versions through
 * DexKit discovery, but it deliberately refuses to hook a newer build until the
 * ceiling is reviewed and raised.
 */
public final class InstagramCompatibility {

    public static final String MAX_VERSION_NAME = "435.0.0.37.76";
    public static final long MAX_VERSION_CODE = 384109456L;

    private InstagramCompatibility() {}

    public static boolean shouldHook(Context context, String packageName) {
        if (!CommonUtils.IG_PACKAGE_NAME.equals(packageName)) {
            ModuleLog.line("(Instar | Compatibility): Skipping unsupported package " + packageName);
            return false;
        }

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            long installedCode = info.getLongVersionCode();
            if (isAboveCeiling(info.versionName, installedCode)) {
                ModuleLog.line("(Instar | Compatibility): BLOCKED Instagram "
                        + info.versionName + " (" + installedCode + ") — maximum is "
                        + MAX_VERSION_NAME + " (" + MAX_VERSION_CODE + ")");
                return false;
            }

            ModuleLog.line("(Instar | Compatibility): Accepted Instagram "
                    + info.versionName + " (" + installedCode + ")");
            return true;
        } catch (Throwable error) {
            // Failing closed is intentional: an unreadable version must never bypass
            // the ceiling and receive version-sensitive Piko-derived hooks.
            ModuleLog.line("(Instar | Compatibility): BLOCKED — unable to read Instagram version: "
                    + error.getMessage());
            return false;
        }
    }

    public static boolean isAboveCeiling(String versionName, long versionCode) {
        int versionComparison = compareNumericVersions(versionName, MAX_VERSION_NAME);
        if (versionComparison != Integer.MIN_VALUE) return versionComparison > 0;
        return versionCode > MAX_VERSION_CODE;
    }

    /** Returns Integer.MIN_VALUE when either version is not a dotted numeric version. */
    private static int compareNumericVersions(String left, String right) {
        if (left == null || right == null) return Integer.MIN_VALUE;
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int length = Math.max(a.length, b.length);
        try {
            for (int i = 0; i < length; i++) {
                long av = i < a.length ? Long.parseLong(a[i]) : 0L;
                long bv = i < b.length ? Long.parseLong(b[i]) : 0L;
                if (av != bv) return Long.compare(av, bv);
            }
            return 0;
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }
}
