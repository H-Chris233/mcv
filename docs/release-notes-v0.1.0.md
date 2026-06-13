# v0.1.0 MVP Release Notes

Status: draft.

## Summary

Multi-Card Vault v0.1.0 is an Android-only, local-first, experimental, unaudited MVP.
It demonstrates a 3-of-5 NFC card threshold unlock flow backed by Rust cryptographic protocol code.

## Included

- Rust protocol core for Vault creation, unlock, update, CardPayload encoding, VaultBlob encoding, and VaultPlaintext encoding.
- Shamir Secret Sharing over GF(256), Argon2id, HKDF-SHA256, and XChaCha20-Poly1305.
- Kotlin/Compose Android app with Room metadata persistence.
- MIFARE Classic 1K compatible CUID Card read/write support.
- Saved Vault list and app-restart unlock for completed Vault records.
- Simple text Vault entries with add, edit, and delete support.
- Test vectors under `test-vectors/`.
- GitHub Actions for Rust checks, Android checks, and security scanning.

## Not Included

- Independent security audit.
- Recovery from fewer than threshold cards.
- Cloud sync or accounts.
- Card replacement flow.
- Encrypted backup import/export.
- DESFire, JavaCard, or NDEF-only tag support.

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

Then complete `docs/manual-nfc-test-record.md` with real CUID Cards.

## Warning

This project is experimental and unaudited.
Do not use it as the sole storage for irreplaceable secrets.
This project is not designed for cloning access cards, bypassing access control systems, or interacting with third-party cards without authorization.
