# Overview

Multi-Card Vault is an Android-only, local-first encrypted vault experiment. The intended unlock model is:

```text
enough valid CUID Cards + user password = final decrypt capability
```

The v0.1 MVP implements this model with a Kotlin/Jetpack Compose Android client and a Rust protocol core exposed through UniFFI. CUID Cards are used as portable containers for encrypted Shamir Shares and Vault Blob Data Fragments. They do not store plaintext vault data, the user password, or the complete Master Secret.

## MVP Scope

- Create a default 3-of-5 Vault.
- Store only non-recovery Vault metadata locally with Room.
- Generate and wrap five Shamir shares in Rust.
- Split the encoded Vault Blob into card Data Fragments in Rust.
- Write Card Payload v1 bytes to MIFARE Classic 1K compatible CUID Cards.
- Read any three valid card payloads and recover the Vault.
- Derive encryption keys with Argon2id and HKDF-SHA256.
- Encrypt Card Payload shares and Vault Blob data with XChaCha20-Poly1305.
- Persist lightweight settings in DataStore.
- Edit simple text entries after unlock and rewrite all cards with updated payloads.
- Provide test vectors, CI checks, and release-readiness documentation.

## Explicit Non-Goals

- No cloud sync, accounts, or multi-user collaboration.
- No iOS client.
- No recovery from fewer than the configured threshold cards.
- No card cloning, access-control bypass, or unauthorized third-party card workflows.
- No production security claim. The project remains experimental and unaudited.

## Main Components

- `android/`: Compose UI, Room metadata, DataStore, MIFARE Classic card I/O, and Android NFC API integration.
- `crates/mcv-core`: High-level create, unlock, and update orchestration.
- `crates/mcv-crypto`: Argon2id, HKDF-SHA256, and XChaCha20-Poly1305 boundaries.
- `crates/mcv-format`: Card Payload, Vault Blob, and Vault Plaintext CBOR formats.
- `crates/mcv-shamir`: Replaceable Shamir Secret Sharing boundary.
- `crates/mcv-uniffi`: Kotlin-callable Rust API.
