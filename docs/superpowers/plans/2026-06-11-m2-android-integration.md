# M2 Android Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android app call the Rust protocol core through UniFFI and persist the first locally created Vault.

**Architecture:** Keep Rust responsible for protocol bytes and cryptographic validation. Android owns UI, Room persistence, Android Keystore wrapping, and orchestration through small repository/use-case interfaces.

**Tech Stack:** Rust 2021, UniFFI 0.29, Kotlin 2.0, Jetpack Compose, ViewModel + StateFlow, Room, Android Keystore, Gradle Kotlin DSL, cargo-ndk.

---

### Task 1: Rust Binding Helper

**Files:**
- Modify: `crates/mcv-core/src/lib.rs`
- Modify: `crates/mcv-uniffi/src/lib.rs`
- Update: `bindings/kotlin/app/multicardvault/uniffi/mcv_uniffi.kt`

- [ ] Add a high-level Rust helper that returns encoded empty `VaultPlaintextV1` bytes.
- [ ] Expose the helper through UniFFI so Kotlin does not implement protocol serialization.
- [ ] Add Rust tests for the helper and existing create/unlock flow using it.
- [ ] Regenerate Kotlin bindings with `cargo run -p mcv-bindgen`.

### Task 2: Android Build Integration

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`

- [ ] Add generated UniFFI Kotlin source directory to the Android app source set.
- [ ] Add JNA, lifecycle, coroutines, Room, and KSP dependencies.
- [ ] Add Gradle tasks to build host Rust library for JVM unit tests.
- [ ] Add Gradle tasks to build Android ABI libraries with `cargo ndk` for APK packaging.
- [ ] Update Android CI and README with required Rust target/cargo-ndk steps.

### Task 3: Android Data And Platform Layer

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/data/VaultEntities.kt`
- Create: `android/app/src/main/java/app/multicardvault/data/VaultDao.kt`
- Create: `android/app/src/main/java/app/multicardvault/data/McvDatabase.kt`
- Create: `android/app/src/main/java/app/multicardvault/data/RoomVaultRepository.kt`
- Create: `android/app/src/main/java/app/multicardvault/security/DeviceSecretRepository.kt`
- Create: `android/app/src/main/java/app/multicardvault/security/KeystoreDeviceSecretRepository.kt`

- [ ] Model `vaults` and `device_secret_refs` Room tables without plaintext secrets.
- [ ] Implement repository interfaces around Room.
- [ ] Implement AES-GCM wrapping of 32-byte device secrets using Android Keystore.
- [ ] Keep secret values out of Bundle, SavedStateHandle, and logs.

### Task 4: Android Use Case And UI

**Files:**
- Create: `android/app/src/main/java/app/multicardvault/core/RustMcvCore.kt`
- Create: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultUseCase.kt`
- Create: `android/app/src/main/java/app/multicardvault/features/create/CreateVaultViewModel.kt`
- Modify: `android/app/src/main/java/app/multicardvault/MainActivity.kt`

- [ ] Wrap UniFFI calls behind a small Kotlin interface.
- [ ] Implement `CreateVaultUseCase` that generates a device secret, calls Rust, and stores the Vault Blob plus wrapped Device Secret.
- [ ] Implement `CreateVaultViewModel` with sealed UI state and coroutine execution.
- [ ] Replace the placeholder screen with a minimal create-vault Compose flow.

### Task 5: Tests And Verification

**Files:**
- Modify: `android/app/src/test/java/app/multicardvault/McvAppTest.kt`
- Create: `android/app/src/test/java/app/multicardvault/core/RustMcvCoreTest.kt`
- Create: `android/app/src/test/java/app/multicardvault/features/create/CreateVaultUseCaseTest.kt`

- [ ] Unit-test app identity and Rust binding calls using the host Rust library.
- [ ] Unit-test create use case with fake repositories and fake device-secret storage.
- [ ] Run `cargo fmt --check`.
- [ ] Run `cargo clippy --workspace --all-targets -- -D warnings`.
- [ ] Run `cargo test --workspace`.
- [ ] Run `./gradlew -p android test`.
- [ ] Run `./gradlew -p android assembleDebug`.

### Task 6: GitHub Delivery

- [ ] Commit M2 implementation.
- [ ] Push `m2-android-integration`.
- [ ] Open a public GitHub PR against `main`.
