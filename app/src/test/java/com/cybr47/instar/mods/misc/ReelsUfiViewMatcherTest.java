package com.cybr47.instar.mods.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReelsUfiViewMatcherTest {

    @Test
    public void matchesObservedAndSmaliCountResourceNames() {
        assertTrue(ReelsUfiViewMatcher.isCountResourceName("ufi_text_component"));
        assertTrue(ReelsUfiViewMatcher.isCountResourceName("ufi_count"));
        assertTrue(ReelsUfiViewMatcher.isCountResourceName("comment_count"));
        assertTrue(ReelsUfiViewMatcher.isCountResourceName("repost_count"));
        assertTrue(ReelsUfiViewMatcher.isCountResourceName("save_count"));
        assertTrue(ReelsUfiViewMatcher.isCountResourceName("clips_share_count"));
    }

    @Test
    public void doesNotMatchIconsOrUnrequestedMetrics() {
        assertFalse(ReelsUfiViewMatcher.isCountResourceName("like_button"));
        assertFalse(ReelsUfiViewMatcher.isCountResourceName("comment_button"));
        assertFalse(ReelsUfiViewMatcher.isCountResourceName("repost_button"));
        assertFalse(ReelsUfiViewMatcher.isCountResourceName("share_button"));
        assertFalse(ReelsUfiViewMatcher.isCountResourceName("view_count"));
        assertFalse(ReelsUfiViewMatcher.isCountResourceName(null));
    }

    @Test
    public void recognizesInspectorReelsHierarchy() {
        assertTrue(ReelsUfiViewMatcher.isReelsHierarchyClassName(
                "instagram.features.clips.viewer.ui.ClipsSwipeRefreshLayout"));
        assertTrue(ReelsUfiViewMatcher.isReelsRootResourceName("clips_ufi_component"));
        assertFalse(ReelsUfiViewMatcher.isReelsHierarchyClassName(
                "com.facebook.litho.ComponentHost"));
        assertFalse(ReelsUfiViewMatcher.isReelsHierarchyClassName(
                "com.instagram.mainfeed.network.FeedCacheCoordinator"));
        assertTrue(ReelsUfiViewMatcher.isLithoComponentHostClassName(
                "com.facebook.litho.ComponentHost"));
        assertFalse(ReelsUfiViewMatcher.isLithoComponentHostClassName(
                "android.widget.TextView"));
    }
}
