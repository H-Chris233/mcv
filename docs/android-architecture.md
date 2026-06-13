# Android Architecture

Status: v0.1 MVP Android architecture.

The Android app uses Compose UI, ViewModel and StateFlow state, repository interfaces for NFC and vault metadata, and UniFFI calls into Rust for protocol behavior.

## Implemented In v0.1 MVP

- `RustMcvCore` wraps generated UniFFI calls for create, unlock, update, and Vault Plaintext encode/decode.
- `RoomVaultRepository` persists non-recovery Vault metadata only.
- `MifareClassicNfcRepository` reads and writes project-specific frames on MIFARE Classic 1K compatible CUID Cards.
- `MainActivity` owns Android `NfcAdapter` reader mode and passes typed NFC results to the ViewModel.
- `CreateVaultViewModel` owns create, write-card, read-card, unlock, and in-session entry editing state without directly touching Android NFC APIs.
- `ListVaultsUseCase` maps locally persisted Vault records into a minimal saved Vault list for restart unlock.
- `UnlockVaultUseCase` decodes Rust-returned `VaultPlaintextV1` bytes into simple UI entries and persists recovered non-secret metadata when a Vault is unlocked from cards on a machine with no existing Room record.
- `UpdateVaultUseCase` encodes replacement entries, calls Rust `update_vault`, and returns replacement Card Payloads for all-card rewrite.
- `DataStoreAppSettingsRepository` persists lightweight settings such as onboarding completion, default threshold values, experimental NFC flag, and diagnostics flag.
- The settings/diagnostics UI displays only non-sensitive status: project audit state, protocol versions, NFC availability, and user-controlled non-secret toggles.

## Current Boundaries

- Card Payloads returned during create or update are held only in the current ViewModel session for write-card flow.
- v0.1 supports app-restart unlock for completed Vault records, but not recovery for an interrupted write-card flow.
- v0.1 supports card-only recovery on a compatible Android device by reading CUID Cards until Rust has enough Card Payloads to unlock.
- v0.1 implements only simple text entry CRUD; it is not a full password-manager schema.
- NFC errors are mapped to user-safe text and payload bytes are not logged.
