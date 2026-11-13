# Validation

2026-11-13, Linux x86_64 container, Temurin JDK 21.0.13, Gradle 9.2.1, Kotlin 2.2.21:

- Core: 10 JUnit tests passed, 0 failed/skipped. Sizes 0, 1, 2, 10, 100, 500, 1,000, 2,000, 5,000 and 10,000; repeated video IDs, traversal, snapshots, unavailable entries, deterministic and random-seed distribution checks. The distribution checks detect obvious bias; they do not prove cryptographic randomness.
- Pagination: 8 JUnit fixture tests passed, 0 failed/skipped. Includes 10,000 entries, repeated video IDs, continuation loops, changing revisions/counts, limits, retry and interruption. It has no YouTube source adapter.
- Both suites also passed using the checked-in wrapper with `--rerun-tasks`; it pins the Gradle distribution checksum. With JDK 21, normal environments can run `./core/gradlew -p core test` and `./core/gradlew -p pagination test`. This cloud environment required explicit Java proxy and trust-store arguments.
- The workflow YAML was checked locally; no GitHub workflow run has occurred.

The manual `./core/gradlew -p core benchmarkShuffle` benchmark on an AMD EPYC 9V74 container with three visible CPUs measured a 0.534 ms median and 3.974 ms p95 for 10,000 preconstructed entries over 200 samples after 50 warmups. Thread allocation averaged 983,896 bytes per run. This measures JVM session creation with seeded `java.util.Random`, including copying and index construction; it is not peak retained memory and excludes network, `SecureRandom` setup, Android and playback.

Not performed: supported APK inspection, bytecode fingerprint or patch application, Android build, signature or integrity checks, settings integration, real playlist retrieval, playback, emulator or device testing, and release. A source-backed snapshot identifier and reliable terminal-page signal are unproven for YouTube. Neither fixture tests nor the standalone permutation tests establish the requested end-to-end behavior.
