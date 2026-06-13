# CUID Card Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make MCV recover Vault data from only User Password and any Threshold-sized set of MIFARE Classic 1K compatible CUID Cards.

**Architecture:** Rust becomes the source of truth for self-contained Card Payloads: each payload carries an encrypted key Share and a Data Fragment that can reconstruct the Vault Blob with Threshold cards. Android stops treating Room and Device Secret as recovery material, keeps Room metadata only, and adds a MIFARE Classic 1K NFC backend.

**Tech Stack:** Rust, UniFFI, Kotlin, Jetpack Compose, Room, Android `MifareClassic`, JUnit.

---

### Task 1: Rust Format Supports Card Data Fragments

**Files:**
- Modify: `crates/mcv-format/src/lib.rs`
- Modify: `docs/card-format-v1.md`

- [x] Add `data_fragment: Vec<u8>` to `CardPayloadV1`.
- [x] Include `data_fragment` in fixed-order CBOR payload encoding.
- [x] Exclude `data_fragment` from Card Payload AAD for Share encryption only if the recovered Vault Blob has its own authenticated header; otherwise include it in AAD to bind Share and fragment.
- [x] Update `card_payload_roundtrips_as_cbor` to assert the fragment round-trips.
- [x] Run `cargo test -p mcv-format`.

### Task 2: Rust Core Removes Device Secret From Recovery

**Files:**
- Modify: `crates/mcv-core/src/lib.rs`
- Modify: `crates/mcv-crypto/src/lib.rs`

- [x] Change `derive_final_key` call sites so protocol derivation uses `master_secret + password_key` only.
- [x] Remove `device_secret` from `CreateVaultRequest`, `UnlockVaultRequest`, and `UpdateVaultRequest`.
- [x] During create, encrypt `VaultBlobV1`, split encoded Vault Blob bytes into Shamir Data Fragments, and attach matching fragments to Card Payloads.
- [x] During unlock, recover Vault Blob bytes from Threshold Data Fragments before decrypting.
- [x] During update, return replacement Card Payloads instead of only a replacement Vault Blob.
- [x] Add tests proving unlock succeeds with no Vault Blob argument and no Device Secret.
- [x] Add tests proving fewer than Threshold Data Fragments cannot recover.
- [x] Run `cargo test -p mcv-core`.

### Task 3: UniFFI And Kotlin Core API Migration

**Files:**
- Modify: `crates/mcv-uniffi/src/lib.rs`
- Regenerate: `bindings/kotlin/app/multicardvault/uniffi/mcv_uniffi.kt`
- Modify: `android/app/src/main/java/app/multicardvault/core/RustMcvCore.kt`
- Modify: `android/app/src/test/java/app/multicardvault/core/RustMcvCoreTest.kt`

- [x] Remove `device_secret` and `vault_blob` request fields from unlock/update binding records.
- [x] Remove `vaultBlob` from Kotlin create/unlock/update call signatures where it is recoverable from cards.
- [x] Add `cardPayloads` to update response so Android can drive all-card rewrite.
- [x] Regenerate UniFFI bindings.
- [x] Update Kotlin core tests for create, unlock, and update.
- [x] Run `cargo build -p mcv-uniffi`.
- [x] Run `./gradlew -p android testDebugUnitTest`.

### Task 4: Android Persistence Becomes Metadata Only

**Files:**
- Modify: `android/app/src/main/java/app/multicardvault/data/VaultEntities.kt`
- Modify: `android/app/src/main/java/app/multicardvault/data/VaultDao.kt`
- Modify: `android/app/src/main/java/app/multicardvault/data/RoomVaultRepository.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultUseCase.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/unlock/UnlockVaultUseCase.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/vault/UpdateVaultUseCase.kt`

- [x] Remove persisted `vaultBlob` recovery dependency from repository models.
- [x] Stop injecting `DeviceSecretRepository` into create, unlock, and update use cases.
- [x] Create stores only metadata after Rust returns card payloads.
- [x] Unlock calls Rust with Password and scanned Card Payloads only.
- [x] Update returns new Card Payloads for all-card rewrite instead of updating local Vault Blob.
- [x] Update unit tests to assert DeviceSecretRepository is not used.
- [x] Run `./gradlew -p android testDebugUnitTest`.

### Task 5: MIFARE Classic 1K NFC Backend

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/nfc/MifareClassicCardLayout.kt`
- Create: `android/app/src/main/java/app/multicardvault/nfc/MifareClassicNfcRepository.kt`
- Modify: `android/app/src/main/java/app/multicardvault/nfc/NfcRepository.kt`
- Modify: `android/app/src/main/java/app/multicardvault/MainActivity.kt`

- [x] Add a fixed card layout for MIFARE Classic 1K data blocks, skipping sector trailers.
- [x] Add small application framing with magic, version, payload length, and payload bytes.
- [x] Authenticate sectors with default keys only.
- [x] Reject unsupported cards, insufficient capacity, and malformed app framing.
- [x] Prefer MIFARE Classic repository in `MainActivity`.
- [x] Keep NDEF repository only if needed as a fallback; otherwise stop wiring it into the main flow.
- [x] Run Android unit tests.

### Task 6: Update UI Flow Requires All-Card Rewrite

**Files:**
- Modify: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultViewModel.kt`
- Modify: `android/app/src/main/java/app/multicardvault/MainActivity.kt`
- Modify: `android/app/src/test/java/app/multicardvault/features/create/CreateVaultViewModelTest.kt`

- [x] After saving entries, move to a rewrite state instead of claiming save is complete.
- [x] Drive writing all updated Card Payloads.
- [x] Only return to Unlocked after all Total Cards are rewritten.
- [x] Update status text from NDEF/Vault Blob language to CUID Card/Data Fragment language.
- [x] Run ViewModel tests.

### Task 7: Documentation And Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/crypto-design.md`
- Modify: `docs/card-format-v1.md`
- Modify: `docs/vault-format-v1.md`
- Modify: `docs/recovery-model.md`
- Modify: `docs/android-architecture.md`
- Modify: `docs/known-issues.md`

- [x] Update docs to state recoverable data exists on CUID Cards.
- [x] Remove current-device Device Secret as a recovery requirement.
- [x] Document MIFARE Classic 1K capacity limits and default-sector-key policy.
- [x] Run `cargo fmt --check`.
- [x] Run `cargo clippy --workspace --all-targets -- -D warnings`.
- [x] Run `cargo test --workspace`.
- [x] Run `./gradlew -p android ktlintCheck`.
- [x] Run `./gradlew -p android testDebugUnitTest`.
