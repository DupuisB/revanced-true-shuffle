# Architecture evidence and open decisions

Investigated 2026-11-13. This file records evidence, not a claim that a
playback integration works. The target APK has not yet been inspected or
patched, and none of the queue strategies below has been validated on it.

## Upstream baseline

- ReVanced directs users to its [temporary GitLab patches repository](https://gitlab.com/ReVanced/revanced-patches) while its [GitHub repository is unavailable](https://github.com/ReVanced/where-is-revanced-patches). The GitHub patches URL returned HTTP 451 during investigation.
- The inspected stable `main` commit is `015fe10a23ea5b919a3fda6e7da2c176517ae75d` (2026-05-31). Its [GmsCore patch declaration](https://gitlab.com/ReVanced/revanced-patches/-/blob/015fe10a23ea5b919a3fda6e7da2c176517ae75d/patches/src/main/kotlin/app/revanced/patches/youtube/misc/gms/GmsCoreSupportPatch.kt) lists YouTube versions 20.14.43, 20.21.37, 20.26.46, 20.31.42, 20.37.48 and 20.40.45. Other patches must also be checked; a declaration alone is not proof of successful patching or playback. The inspected `dev` head `5590017e457db37e5eddd69c8f8efc465da87155` (2026-11-03) declares the same versions for this patch. The newest declared version is a candidate, not a validated target.
- The [CLI documentation](https://github.com/ReVanced/revanced-cli/blob/main/docs/1_usage.md) supports multiple patch bundles and default patch selection. This makes a separate bundle worth investigating. It does **not** establish that separate bundles can safely share every internal settings or extension object, or that patch ordering will be correct. Those must be tested against a pinned upstream build. CLI verifies signatures and provenance by default; the documented provenance provider is GitHub. Trust and verification for locally built or GitLab artifacts require an explicit solution.
- The inspected source uses `settingsPatch`, `PreferenceScreen.PLAYER.addPreferences`, and `SwitchPreference` in [an existing patch](https://gitlab.com/ReVanced/revanced-patches/-/blob/015fe10a23ea5b919a3fda6e7da2c176517ae75d/patches/src/main/kotlin/app/revanced/patches/youtube/interaction/doubletap/DisableChapterSkipDoubleTapPatch.kt). Runtime settings are represented in [Settings.java](https://gitlab.com/ReVanced/revanced-patches/-/blob/015fe10a23ea5b919a3fda6e7da2c176517ae75d/extensions/youtube/src/main/java/app/revanced/extension/youtube/settings/Settings.java). Actual external bundle linkage, resources, persistence and lifecycle behavior remain unverified.
- The inspected upstream declares ReVanced Patcher 22.0.0 in its [version catalog](https://gitlab.com/ReVanced/revanced-patches/-/blob/015fe10a23ea5b919a3fda6e7da2c176517ae75d/gradle/libs.versions.toml) and is [GPL-3.0](https://gitlab.com/ReVanced/revanced-patches/-/blob/015fe10a23ea5b919a3fda6e7da2c176517ae75d/LICENSE). Non-root GmsCore support changes the app package to `app.revanced.android.youtube` at the inspected revision; compatibility and companion installation must be verified in the actual output.

## Candidate playback integration

All approaches depend on a complete, authenticated and stable enumeration of accessible **playlist entries**, not merely unique video IDs. Continuation termination alone does not establish snapshot consistency under concurrent edits. Pagination, duplicate entries, unavailable videos, repeat, queue edits, account access and failure fallback need explicit evidence.

| Approach | Potential advantage | Evidence needed before choosing |
| --- | --- | --- |
| A: replace or reorder native queue | Native player could retain navigation, background and lifecycle semantics. | Whether the target APK exposes a complete editable queue, preserves duplicate entry identity, accepts thousands of entries, and maintains autoplay and order. |
| B: intercept navigation | Core traversal can be independent of the queue implementation. | Hooks for manual and automatic transitions, Previous, selection, background playback and repeat; avoiding conflicts with player state, history and stale sessions. |
| C: integrate at playlist/playback boundary | May let YouTube construct a native queue from a shuffled snapshot. | A verified boundary, its response format and semantics, robust fingerprint, and evidence that all continuation pages and ordering are respected. |

No approach is selected yet: source inspection did not establish the target
YouTube queue or pagination behavior. A search of the inspected upstream
YouTube patch and extension source found playlist-related UI filtering but no
verified existing whole-playlist shuffle integration. APK reverse engineering,
hook application, and live playback tests are required before claiming that any
approach satisfies the specification. If enumeration completeness cannot be
established, a feature must fail safely and explain that native shuffle remains
available; an incomplete subset must never be presented as true shuffle.
