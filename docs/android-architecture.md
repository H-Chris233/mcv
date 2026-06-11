# Android Architecture

Status: M3 implements the first Android vertical slice.

The Android app uses Compose UI, ViewModel and StateFlow state, repository interfaces for NFC, Keystore, and vault storage, and UniFFI calls into Rust for protocol behavior.

## Implemented In M3

- `RustMcvCore` wraps generated UniFFI calls for create and unlock.
- `RoomVaultRepository` persists Vault metadata and Vault Blob bytes.
- `KeystoreDeviceSecretRepository` wraps per-Vault Device Secret bytes with Android Keystore AES-GCM.
- `NdefNfcRepository` reads and writes project-specific NDEF MIME records.
- `MainActivity` owns Android `NfcAdapter` reader mode and passes typed NFC results to the ViewModel.
- `CreateVaultViewModel` owns create, write-card, read-card, and unlock session state without directly touching Android NFC APIs.

## Current Boundaries

- Card Payloads returned during create are held only in the current ViewModel session for write-card flow.
- M3 does not provide app-restart recovery for an interrupted write-card flow.
- M3 verifies unlock capability but does not display or edit Vault Plaintext entries yet.
- NFC errors are mapped to user-safe text and payload bytes are not logged.
