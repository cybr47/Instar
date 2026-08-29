package com.cybr47.instar.mods.misc;

import java.util.Locale;
import java.util.Set;

/** Pure matching rules kept separate so they can be tested without an Instagram APK. */
final class ReelsUfiViewMatcher {

    private static final Set<String> EXACT_COUNT_RESOURCE_NAMES = Set.of(
            "ufi_text_component",
            "ufi_count",
            "like_count",
            "comment_count",
            "repost_count",
            "reshare_count",
            "share_count",
            "save_count"
    );

    private static final Set<String> REELS_ROOT_RESOURCE_NAMES = Set.of(
            "clips_ufi_component",
            "clips_viewer",
            "clips_viewer_root",
            "reels_viewer",
            "reels_viewer_root"
    );

    private ReelsUfiViewMatcher() {
    }

    static boolean isCountResourceName(String resourceName) {
        if (resourceName == null) return false;
        String name = resourceName.toLowerCase(Locale.ROOT);
        if (EXACT_COUNT_RESOURCE_NAMES.contains(name)) return true;

        // Some builds prefix the semantic ID while preserving its action suffix.
        boolean clipsScoped = name.startsWith("clips_") || name.startsWith("reels_");
        return clipsScoped && (name.endsWith("_like_count")
                || name.endsWith("_comment_count")
                || name.endsWith("_repost_count")
                || name.endsWith("_reshare_count")
                || name.endsWith("_share_count")
                || name.endsWith("_save_count"));
    }

    static boolean isReelsRootResourceName(String resourceName) {
        if (resourceName == null) return false;
        return REELS_ROOT_RESOURCE_NAMES.contains(resourceName.toLowerCase(Locale.ROOT));
    }

    static boolean isReelsHierarchyClassName(String className) {
        if (className == null) return false;
        String name = className.toLowerCase(Locale.ROOT);
        return name.contains("instagram.features.clips.viewer")
                || name.contains(".clips.viewer.")
                || name.endsWith("clipsswiperefreshlayout");
    }

    static boolean isLithoComponentHostClassName(String className) {
        if (className == null) return false;
        String name = className.toLowerCase(Locale.ROOT);
        return name.equals("com.facebook.litho.componenthost")
                || name.endsWith(".componenthost");
    }
}
