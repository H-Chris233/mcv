# M3 NFC Read Write Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first Android NFC NDEF vertical slice: write generated Card Payloads to tags, read threshold tags, and unlock the just-created Vault through Rust.

**Architecture:** Android owns NFC hardware access through a repository around `NfcAdapter` reader mode and `Ndef`/`NdefFormatable`. ViewModel owns UI/session state but does not touch Android NFC APIs directly. Rust remains the only layer that parses encrypted protocol bytes and performs unlock.

**Tech Stack:** Kotlin, Jetpack Compose, Android NFC API, ViewModel + StateFlow, Kotlin Coroutines, Room, Android Keystore, Rust UniFFI.

---

### Task 1: Core Adapter And Use Cases

**Files:**
- Modify: `android/app/src/main/java/app/multicardvault/core/RustMcvCore.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultUseCase.kt`
- Create: `android/app/src/main/java/app/multicardvault/features/unlock/UnlockVaultUseCase.kt`
- Modify: `android/app/src/test/java/app/multicardvault/core/RustMcvCoreTest.kt`
- Modify: `android/app/src/test/java/app/multicardvault/features/create/CreateVaultUseCaseTest.kt`

- [ ] Add `unlockVault` to the Kotlin `McvCore` adapter.
- [ ] Return in-memory Card Payloads from create use case so the write-card flow has data to write.
- [ ] Add `UnlockVaultUseCase` that loads Vault Blob and Device Secret, then calls Rust with scanned card payloads.
- [ ] Unit-test create result carries payloads without logging or persisting payload bytes.
- [ ] Unit-test Rust create/unlock roundtrip through UniFFI.

### Task 2: NFC Repository

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/nfc/NfcCardResult.kt`
- Create: `android/app/src/main/java/app/multicardvault/nfc/NfcRepository.kt`
- Create: `android/app/src/main/java/app/multicardvault/nfc/NdefNfcRepository.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/build.gradle.kts`

- [ ] Add NFC permission and optional NFC hardware feature.
- [ ] Implement `readPayload(tag)` using `Ndef` and a project-specific MIME record.
- [ ] Implement `writePayload(tag, payload)` using `Ndef`; format blank tags with `NdefFormatable` when available.
- [ ] Return typed result states for unsupported, empty, invalid payload, capacity, IO, and success.
- [ ] Keep payload bytes out of log and user-visible error text.

### Task 3: Reader Mode And UI State

**Files:**
- Modify: `android/app/src/main/java/app/multicardvault/MainActivity.kt`
- Modify: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultViewModel.kt`

- [ ] Move NFC hardware interaction into `MainActivity` reader-mode callback.
- [ ] Have ViewModel expose `NfcCommand.Write(payload)` or `NfcCommand.Read`.
- [ ] On write success, advance the next pending Card Payload.
- [ ] On read success, de-duplicate exact repeated payloads and track threshold progress.
- [ ] Trigger unlock once enough payloads have been read.
- [ ] Show user-safe status text for unsupported tag, empty tag, duplicate tag, invalid payload, IO failure, and unlock failure.

### Task 4: Minimal Compose Flow

**Files:**
- Modify: `android/app/src/main/java/app/multicardvault/MainActivity.kt`

- [ ] Keep the existing create form.
- [ ] After create, show write-card progress `written / total`.
- [ ] After all cards are written, show read/unlock progress `read / threshold`.
- [ ] Show successful unlock without displaying Vault Plaintext bytes.
- [ ] Keep warnings visible: experimental, unaudited, no third-party card use.

### Task 5: Tests And Verification

**Files:**
- Create: `android/app/src/test/java/app/multicardvault/features/create/CreateVaultViewModelTest.kt`
- Modify: `android/app/src/test/java/app/multicardvault/features/create/CreateVaultUseCaseTest.kt`
- Modify: `android/app/src/test/java/app/multicardvault/core/RustMcvCoreTest.kt`

- [ ] Unit-test write-card progress advances on NFC success.
- [ ] Unit-test duplicate exact payload does not count twice during unlock.
- [ ] Unit-test unlock triggers when threshold is reached.
- [ ] Run `cargo fmt --check`.
- [ ] Run `cargo clippy --workspace --all-targets -- -D warnings`.
- [ ] Run `cargo test --workspace`.
- [ ] Run `git diff --check`.
- [ ] Run `./gradlew -p android test`.
- [ ] Run `./gradlew -p android assembleDebug`.

### Task 6: GitHub Delivery

- [ ] Commit M3 implementation.
- [ ] Push `m3-nfc-read-write`.
- [ ] Open a public GitHub PR against `main`.
