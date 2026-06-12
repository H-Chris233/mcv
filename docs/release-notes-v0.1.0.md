# v0.1.0 MVP Release Notes

Status: draft.

## Summary

Multi-Card Vault v0.1.0 is an Android-only, local-first, experimental, unaudited MVP.
It demonstrates a 3-of-5 NFC card threshold unlock flow backed by Rust cryptographic protocol code.

## Included

- Rust protocol core for Vault creation, unlock, update, CardPayload encoding, VaultBlob encoding, and VaultPlaintext encoding.
- Shamir Secret Sharing over GF(256), Argon2id, HKDF-SHA256, and XChaCha20-Poly1305.
- Kotlin/Compose Android app with Room persistence and Android Keystore Device Secret wrapping.
- NDEF NFC read/write support for MVP tags such as NTAG216.
- Saved Vault list and app-restart unlock for completed Vault records.
- Simple text Vault entries with add, edit, and delete support.
- Test vectors under `test-vectors/`.
- GitHub Actions for Rust checks, Android checks, and security scanning.

## Not Included

- Independent security audit.
- Cross-device recovery.
- Cloud sync or accounts.
- Biometric-gated Device Secret access.
- Card replacement flow.
- Encrypted backup import/export.
- MIFARE Classic, CUID, DESFire, or JavaCard support.

## Verification Before Release

Run:

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

Then complete `docs/manual-nfc-test-record.md` with real NTAG216 tags.

## Warning

This project is experimental and unaudited.
Do not use it as the sole storage for irreplaceable secrets.
This project is not designed for cloning access cards, bypassing access control systems, or interacting with third-party cards without authorization.
