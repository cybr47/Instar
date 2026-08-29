# Instar

Instar is an LSPosed module for the official Instagram Android app. It combines the broad feature set and in-app interface of [InstaEclipse](https://github.com/ReSo7200/InstaEclipse) with selected, runtime-portable ideas from [Piko](https://github.com/crimera/piko).

This repository is under active development. The first alpha deliberately targets one known Instagram baseline instead of claiming broad compatibility.

## Compatibility

- Target: official `com.instagram.android`
- Maximum supported version: `435.0.0.37.76`
- ARM64 reference version code: `384109456`
- Injection method: LSPosed

Instar fails closed: it registers no functional Instagram hooks when the installed version is newer than the ceiling. Raise the constants in `InstagramCompatibility` only after checking all version-sensitive hooks against a newer APK.

## Instar additions

- `Hide Reel UFI counts`: keeps the Like, Comment, Repost, Share, and Save controls while hiding their numeric labels.
- `Hide repost button`: hides Instagram's repost/reshare control and retains InstaEclipse's network-side repost block.
- `Hide Notes tray` and `Hide Stories tray`.
- `Disable Reels scrolling`.
- Instagram Experimental Settings unlock, exposed through Instagram's native long-press Home gesture when Developer Options is enabled. Instar combines the structural InstaEclipse employee-gate hook with Instagram 435's `28538::0` employee-options flag.
- Piko-compatible MetaConfig mapping import and version-matched automatic mapping repair under Developer Options.
- Instar settings remain available by long-pressing Search.

The Reel UFI implementation is hybrid. Instagram 435 has native MobileConfig flags for hiding Reel like and repost counts; it does not expose equivalent mapped flags for comment, share, or save counts. Instar enables the native flags through Piko's wrapper-accessor path and applies a narrowly scoped UI fallback anchored to `clips_ufi_component` or `like_button`.

If Instagram's `files/mobileconfig/id_name_mapping.json` is missing or is an empty stub, Instar downloads the mapping that exactly matches the installed Instagram version from Piko's mapping repository. Developer Options also provides manual Import and Download actions. Imported files are validated before an atomic replacement.

Instar does not inject Piko Settings or More Profile Options buttons into the profile page. A Miscellaneous `Profile quick actions` toggle adds Copy username to a long-press on the profile picture and can share that gesture with InstaEclipse's profile-picture downloader. Full name, bio, and internal user ID actions are tracked for later validation.

## Existing feature families

The InstaEclipse base also provides ghost/privacy controls, media downloading, ad and analytics blocking, clean-feed controls, distraction-free controls, Reel quality selection, location spoofing, themes, copy actions, backup/restore, and an in-app hook log.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs the same test-and-build sequence on every push to `main`.

## Installation

1. Install Instagram `435.0.0.37.76` or an older compatible build of the official package.
2. Install the Instar APK.
3. Enable Instar for Instagram in LSPosed.
4. Force-stop Instagram, then start it again.
5. Long-press Search for Instar settings. Enable Developer Options, restart Instagram, then long-press Home for Instagram Experimental Settings.

## Porting policy

Piko patches that can be expressed as narrowly scoped runtime hooks are candidates for Instar. Whole-method replacements, profile-page launcher buttons, and patches whose 435 fingerprints cannot be validated are intentionally excluded from the initial alpha.

See [PORTING.md](PORTING.md) for the current matrix and device-validation checklist.

## Credits and license

Instar is based on InstaEclipse by ReSo7200 and contributors, and contains functionality derived from Piko by crimera and contributors. See [NOTICE](NOTICE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Instar is licensed under GPL-3.0. Piko's attribution requirement is preserved in `NOTICE`.
