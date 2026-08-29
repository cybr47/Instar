# Piko port matrix

Baseline: Instagram `435.0.0.37.76`.

| Feature | Alpha status | Implementation |
|---|---|---|
| Hide repost button | Included | Runtime equivalent of Piko's media-notes gate plus InstaEclipse's request block |
| Hide Reel like count | Included | Native MobileConfig `47643::3` |
| Hide Reel repost count | Included | Native MobileConfig `75216::1` and `75216::2` |
| Hide Reel comment/share/save counts | Included, needs device validation | Numeric-label fallback scoped to `clips_ufi_component` |
| Hide Notes tray | Included, needs device validation | Resource anchor `cf_hub_recycler_view` |
| Hide Stories tray | Included, needs device validation | Resource anchors `litho_main_feed_stories_tray` and `floating_tray_spacer` |
| Disable Reels scrolling | Included, needs device validation | DexKit anchor plus ViewPager2 input disable |
| Experimental Settings via Home | Included, needs device validation | Structural employee gate plus MobileConfig `28538::0` |
| Piko Settings profile button | Excluded | Instar uses long-press Search |
| More Profile Options button | Excluded | No profile-page launcher UI is injected |
| Copy profile username | Included | Miscellaneous quick-action menu on profile-picture long press |
| Copy full name/bio/user ID | Backlog | Requires validated 435 profile-object bindings |
| Create-group share-sheet patch | Backlog | Current Piko patch injects mid-method; unsafe to port as a broad runtime replacement |
| Other version-fingerprint patches | Backlog | Require independent 435 hook validation before inclusion |

## Device validation checklist

- Confirm Instar settings open from a long-press on Search.
- Enable Developer Options, restart Instagram, and confirm long-press Home opens Instagram Experimental Settings.
- Confirm the five Reel action icons remain clickable and their counts are absent.
- Confirm the repost button is absent in feed and Reels, and repost requests remain blocked.
- Toggle Notes/Stories tray hiding independently.
- Confirm Reels scrolling is disabled without breaking pull-to-refresh outside Reels.
- Install a version newer than `435.0.0.37.76` and confirm the compatibility log says `BLOCKED` and no hooks are installed.

Device validation is mandatory before an alpha release because these hooks depend on Instagram's private UI and obfuscated implementation.
