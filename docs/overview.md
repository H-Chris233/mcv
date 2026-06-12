# Overview

Multi-Card Vault is an Android-only, local-first encrypted vault experiment. The intended unlock model is:

```text
enough valid cards + user password + Android device secret = final decrypt capability
```

The v0.1 MVP implements this model with a Kotlin/Jetpack Compose Android client and a Rust protocol core exposed through UniFFI. NFC cards are used as portable containers for encrypted Shamir shares. They do not store plaintext vault data, the user password, the Android Device Secret, or the complete Master Secret.

## MVP Scope

- Create a default 3-of-5 Vault.
- Store encrypted Vault Blob records locally with Room.
- Generate and wrap five Shamir shares in Rust.
- Write Card Payload v1 bytes to NDEF NFC tags.
- Read any three valid card payloads and unlock the local Vault.
- Keep the Device Secret wrapped by Android Keystore.
- Derive encryption keys with Argon2id and HKDF-SHA256.
- Encrypt Card Payload shares and Vault Blob data with XChaCha20-Poly1305.
- Persist lightweight settings in DataStore.
- Edit simple text entries after unlock and re-encrypt the Vault Blob.
- Provide test vectors, CI checks, and release-readiness documentation.

## Explicit Non-Goals

- No cloud sync, accounts, or multi-user collaboration.
- No iOS client.
- No cross-device recovery in the MVP.
- No card cloning, access-control bypass, or unauthorized third-party card workflows.
- No production security claim. The project remains experimental and unaudited.

## Main Components

- `android/`: Compose UI, Room, DataStore, Keystore wrapping, and Android NFC API integration.
- `crates/mcv-core`: High-level create, unlock, and update orchestration.
- `crates/mcv-crypto`: Argon2id, HKDF-SHA256, and XChaCha20-Poly1305 boundaries.
- `crates/mcv-format`: Card Payload, Vault Blob, and Vault Plaintext CBOR formats.
- `crates/mcv-shamir`: Replaceable Shamir Secret Sharing boundary.
- `crates/mcv-uniffi`: Kotlin-callable Rust API.
