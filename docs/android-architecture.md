# Android Architecture

Status: M4 extends the Android vertical slice with minimal Vault Plaintext entry editing.

The Android app uses Compose UI, ViewModel and StateFlow state, repository interfaces for NFC, Keystore, and vault storage, and UniFFI calls into Rust for protocol behavior.

## Implemented Through M4

- `RustMcvCore` wraps generated UniFFI calls for create, unlock, update, and Vault Plaintext encode/decode.
- `RoomVaultRepository` persists Vault metadata and Vault Blob bytes.
- `KeystoreDeviceSecretRepository` wraps per-Vault Device Secret bytes with Android Keystore AES-GCM.
- `NdefNfcRepository` reads and writes project-specific NDEF MIME records.
- `MainActivity` owns Android `NfcAdapter` reader mode and passes typed NFC results to the ViewModel.
- `CreateVaultViewModel` owns create, write-card, read-card, unlock, and in-session entry editing state without directly touching Android NFC APIs.
- `UnlockVaultUseCase` decodes Rust-returned `VaultPlaintextV1` bytes into simple UI entries.
- `UpdateVaultUseCase` encodes replacement entries, calls Rust `update_vault`, and replaces the stored Vault Blob only after Rust returns a new encrypted blob.

## Current Boundaries

- Card Payloads returned during create are held only in the current ViewModel session for write-card flow.
- M4 does not provide app-restart recovery for an interrupted write-card flow.
- M4 implements only simple text entry CRUD; it is not a full password-manager schema.
- NFC errors are mapped to user-safe text and payload bytes are not logged.
