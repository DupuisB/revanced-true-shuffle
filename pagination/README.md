# Pagination coordinator (experimental, isolated)

This module does not connect to YouTube. It defines a fail-closed coordinator for a future source adapter. It follows continuations sequentially, bounds work and retained identifier characters, preserves repeated video IDs when occurrence IDs differ, rejects duplicate occurrence IDs, repeated tokens, inconsistent counts and changed revisions, and retries only adapter-classified transient failures. Cancellation uses thread interruption and requires the adapter to cancel its own network requests; it cannot interrupt a source that ignores interruption. The source must also bound response size while decoding, before this collector sees a page. Call it off the UI thread.

The adapter must establish a source-backed immutable snapshot/revision and must explicitly distinguish a valid terminal page from a malformed or missing continuation. If the source does not offer those guarantees, the coordinator cannot establish complete enumeration; a fabricated snapshot ID would violate its contract. It cannot detect changes the source fails to report, and it does not itself establish which unavailable entries are accessible. There is currently no verified YouTube adapter or playback integration.

Run `gradle -p pagination test` with JDK 21 and a compatible Gradle installation.
