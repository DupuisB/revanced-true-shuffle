# ReVanced True Shuffle

This is an **unfinished research and implementation project**, not an installable APK or integrated ReVanced patch. It contains a standalone Kotlin playlist permutation/traversal core and a fixture-tested pagination coordinator. Neither is connected to YouTube. See [STATUS.md](STATUS.md), [DECISIONS.md](DECISIONS.md), and [VALIDATION.md](VALIDATION.md) for evidence and blockers.

The target is regular YouTube for Android, with the official default ReVanced patches and non-root support. YouTube 20.40.45 is a candidate based on upstream declarations, not a validated compatibility claim. No signing key, installation instructions, release, or supported-device claim can be supplied until a real build and Android tests exist.

With JDK 21, run the isolated tests from the repository root:

```sh
./core/gradlew -p core test
./core/gradlew -p pagination test
```

The pagination source contract requires a trustworthy immutable snapshot and a recognized terminal page. A YouTube adapter must establish those properties rather than invent them. The modules are licensed under GPL-3.0; see [LICENSE](LICENSE).
