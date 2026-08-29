package com.cybr47.instar.mods.media;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import com.cybr47.instar.R;
import com.cybr47.instar.utils.feature.FeatureFlags;
import com.cybr47.instar.utils.feature.FeatureStatusTracker;
import com.cybr47.instar.utils.i18n.I18n;
import com.cybr47.instar.utils.log.ModuleLog;

/**
 * Profile Picture Downloader
 *
 * Strategy:
 *   Hook View.onAttachedToWindow() globally, filter for "expanded_profile_pic" by resource name
 *   (cached as an int ID after first resolution). When found, attach a long-press listener that
 *   reads the ImageUrl field (getUrl()) from IgImageView and downloads via FeedVideoDownloadHook helpers.
 *
 * Gated by FeatureFlags.enableProfileDownload.
 */
public class ProfilePicDownloadHook {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Cached resource ID for "expanded_profile_pic"; 0 = not yet resolved. */
    private static volatile int expandedPicViewId = 0;

    // ── Install ───────────────────────────────────────────────────────────────

    public static void install() {
        // Mark status before hook setup so the toast shows correctly
        if (FeatureFlags.enableProfileDownload) {
            FeatureStatusTracker.setEnabled("ProfileDownload", R.string.ig_dialog_downloader_profiles);
            FeatureStatusTracker.setHooked("ProfileDownload");
        }

        // Hook View.onAttachedToWindow — fires once per view attachment, works for any
        // window type (Activity, Dialog, BottomSheet) without relying on layout listeners.
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.enableProfileDownload && !FeatureFlags.enableProfileQuickActions) return;
                View v = (View) param.thisObject;
                int vid = v.getId();
                if (vid == View.NO_ID) return;

                // Fast path: cached int comparison (only resolves resource name once)
                if (expandedPicViewId != 0) {
                    if (vid != expandedPicViewId) return;
                } else {
                    try {
                        String name = v.getResources().getResourceEntryName(vid);
                        if (!"expanded_profile_pic".equals(name)) return;
                        expandedPicViewId = vid;
                    } catch (Throwable ignored) { return; }
                }

                injectLongPress(v);
                if (FeatureFlags.enableProfileQuickActions) {
                    FeatureStatusTracker.setHooked("ProfileQuickActions");
                }
            }
        });
    }

    // ── UI injection ──────────────────────────────────────────────────────────

    private static void injectLongPress(View view) {
        try {
            view.setOnLongClickListener(v -> {
                // Resolve activity lazily at tap time — context is valid at this point
                Context ctx = v.getContext();
                Activity activity = activityFromContext(ctx);

                String username = activity != null ? extractUsername(activity) : null;
                if (FeatureFlags.enableProfileQuickActions) {
                    showQuickActions(ctx, v, username);
                } else {
                    downloadProfilePicture(ctx, v, username);
                }
                return true;
            });

        } catch (Throwable t) {
            ModuleLog.line("(Instar | ProfileDL) ❌ injectLongPress: " + t.getMessage());
        }
    }

    private static void showQuickActions(Context context, View picture, String username) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();

        labels.add(I18n.t(context, R.string.ig_profile_action_copy_username));
        actions.add(() -> copyUsername(context, username));

        if (FeatureFlags.enableProfileDownload) {
            labels.add(I18n.t(context, R.string.ig_profile_action_download_picture));
            actions.add(() -> downloadProfilePicture(context, picture, username));
        }

        new AlertDialog.Builder(context)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private static void copyUsername(Context context, String username) {
        if (username == null) {
            Toast.makeText(context, I18n.t(context,
                    R.string.ig_profile_action_username_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Instagram username", username));
            Toast.makeText(context, I18n.t(context,
                    R.string.ig_profile_action_username_copied), Toast.LENGTH_SHORT).show();
        }
    }

    private static void downloadProfilePicture(Context context, View picture, String username) {
        String url = extractUrl(picture);
        if (url == null) {
            Toast.makeText(context, I18n.t(context,
                    R.string.ig_toast_profile_pic_url_not_found), Toast.LENGTH_SHORT).show();
            ModuleLog.line("(Instar | ProfileDL): URL extraction failed");
            return;
        }
        String filename = FeedVideoDownloadHook.buildFilename(username, "profile", null, false);
        Toast.makeText(context, I18n.t(context,
                R.string.ig_toast_downloading_profile_pic), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                boolean delegated = FeedVideoDownloadHook.downloadAndSave(
                        context, url, filename, false, username);
                if (!delegated) {
                    mainHandler.post(() -> Toast.makeText(context,
                            I18n.t(context, R.string.ig_toast_profile_pic_saved),
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable error) {
                ModuleLog.line("(Instar | ProfileDL): " + error.getMessage());
                mainHandler.post(() -> Toast.makeText(context,
                        I18n.t(context, R.string.ig_toast_download_failed, error.getMessage()),
                        Toast.LENGTH_SHORT).show());
            }
        }, "Instar-profile-download").start();
    }

    // ── URL extraction ────────────────────────────────────────────────────────

    /**
     * Extracts the image URL from the profile pic view (CircularImageView extends IgImageView).
     * Scans known ImageUrl-typed fields by name; tries multiple candidates in order.
     */
    private static String extractUrl(View view) {
        for (String fieldName : new String[]{"A0E", "A0D", "A0c"}) {
            try {
                String url = getUrlFromImageUrlField(view, fieldName);
                if (url != null) return url;
            } catch (Throwable ignored) {}
        }

        // Fallback: tag-based URI
        try {
            Object tag = view.getTag();
            if (tag instanceof Uri) return tag.toString();
            if (tag instanceof String s && s.startsWith("http")) return s;
        } catch (Throwable ignored) {}

        ModuleLog.line("(Instar | ProfileDL) ❌ all URL strategies failed for " + view.getClass().getName());
        return null;
    }

    /**
     * Walks the class hierarchy to find a field by name, reads it as an ImageUrl,
     * then calls getUrl() on it (ImageUrl is a non-obfuscated interface).
     */
    private static String getUrlFromImageUrlField(View view, String fieldName) throws Throwable {
        Class<?> cls = view.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object imageUrl = f.get(view);
                if (imageUrl == null) return null;
                java.lang.reflect.Method getUrl = imageUrl.getClass().getMethod("getUrl");
                Object result = getUrl.invoke(imageUrl);
                if (result instanceof String s && s.startsWith("http")) return s;
                return null;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    // ── Username extraction ───────────────────────────────────────────────────

    @SuppressLint("DiscouragedApi")
    private static String extractUsername(Activity activity) {
        try {
            android.app.ActionBar ab = activity.getActionBar();
            if (ab != null && ab.getTitle() != null) {
                String t = ab.getTitle().toString().trim();
                if (looksLikeUsername(t)) return t;
            }
        } catch (Throwable ignored) {}

        try {
            int titleId = activity.getResources()
                    .getIdentifier("action_bar_title", "id", activity.getPackageName());
            if (titleId != 0) {
                android.widget.TextView tv = activity.findViewById(titleId);
                if (tv != null) {
                    String t = tv.getText().toString().trim();
                    if (looksLikeUsername(t)) return t;
                }
            }
        } catch (Throwable ignored) {}

        try {
            CharSequence t = activity.getTitle();
            if (t != null && looksLikeUsername(t.toString().trim())) return t.toString().trim();
        } catch (Throwable ignored) {}

        return null;
    }

    private static boolean looksLikeUsername(String s) {
        return s != null && s.length() >= 1 && s.length() <= 30
                && s.matches("[a-zA-Z0-9._]+")
                && !s.matches("\\d+");
    }

    // ── Context → Activity ────────────────────────────────────────────────────

    private static Activity activityFromContext(Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private static void downloadToStream(String url, OutputStream out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
        conn.connect();
        try (InputStream in = conn.getInputStream()) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
    }
}
