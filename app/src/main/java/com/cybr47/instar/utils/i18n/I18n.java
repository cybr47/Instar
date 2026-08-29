package com.cybr47.instar.utils.i18n;

import android.content.Context;

import androidx.annotation.StringRes;

import com.cybr47.instar.utils.core.CommonUtils;

/**
 * Loads string resources from the Instar module APK while running inside
 * the host app (Instagram) process.
 *
 * createPackageContext already inherits the device's current locale/configuration,
 * so Android's resource system automatically picks the correct values-xx folder.
 * No manual locale override needed.
 */
public final class I18n {

    private I18n() {}

    public static String t(Context hostContext, @StringRes int resId, Object... args) {
        try {
            Context moduleContext = hostContext.createPackageContext(
                    CommonUtils.MY_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
            return args.length == 0
                    ? moduleContext.getString(resId)
                    : moduleContext.getString(resId, args);
        } catch (Exception e) {
            return "";
        }
    }
}
