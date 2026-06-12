# Release Process

Status: v0.1 MVP release checklist.

MVP releases must include an APK, checksums, changelog, known issues, manual NFC test results, and explicit experimental/unaudited warnings.

## Required Checks

Run from a clean checkout:

```bash
cargo fmt --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
cargo audit
cargo deny check advisories bans sources
./gradlew -p android ktlintCheck
./gradlew -p android detekt
./gradlew -p android lintDebug
./gradlew -p android test
./gradlew -p android assembleDebug
```

## Release Artifacts

- `app-debug.apk` or a signed release APK.
- `SHA256SUMS`.
- `docs/release-notes-v0.1.0.md`.
- `docs/known-issues.md`.
- Completed `docs/manual-nfc-test-record.md`.

## Release Rules

- Keep the release labelled experimental and unaudited.
- Do not publish until real NTAG216 manual tests are complete.
- Do not include plaintext Vault data, card payload dumps from private tags, passwords, or Device Secret bytes in release artifacts.
