# Android Architecture

Status: v0.2 Card Lifecycle Foundation Android architecture.

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

## Implemented In Card Lifecycle Foundation

- `RustMcvCore` exposes Card Payload inspection metadata without returning keys, shares, encrypted fragments, or the full Card Payload to local storage.
- New Vault creation is limited to safe threshold presets. The supported presets are `2-of-3` and `3-of-5`; invalid persisted defaults are migrated back to `3-of-5`.
- `RoomCardInventoryRepository` persists local Card Inventory metadata only: Vault ID, Scheme ID, share index, threshold, total, label, status, and timestamps.
- `InspectCardUseCase`, `VerifyCardSetUseCase`, `StartCardSetReissueUseCase`, and `RecoverInterruptedReissueUseCase` isolate card lifecycle behavior from Compose UI state.
- Card verification classifies scanned cards as current, old scheme, duplicate, wrong Vault, or unreadable, and only persists non-recovery inventory metadata for readable cards that belong to the target Vault.
- Card Set Reissue always generates a complete replacement Card Set through Rust `update_vault`; the UI then rewrites every card.
- Interrupted Reissue Recovery scans physical cards, groups them by Vault ID and Scheme ID, and unlocks when any group reaches its threshold. It does not resume from cached Card Payloads.

## Current Boundaries

- Card Payloads returned during create or update are held only in the current ViewModel session for write-card flow.
- The app supports app-restart unlock for completed Vault records and card-only recovery on a compatible Android device.
- The app supports interrupted Card Set Reissue recovery by scanning enough old or new cards, but it does not cache full Card Payloads locally for resume.
- Single-card patching is intentionally not implemented; replacement is modeled as whole Card Set Reissue.
- The app implements only simple text entry CRUD; it is not a full password-manager schema.
- NFC errors are mapped to user-safe text and payload bytes are not logged.
