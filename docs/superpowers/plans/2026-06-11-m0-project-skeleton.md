# M0 Project Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible M0 repository skeleton for Multi-Card Vault.

**Architecture:** Rust is a multi-crate Cargo workspace. Android is a minimal Compose application under `android/`. Documentation and CI are created upfront so future implementation has clear safety and verification boundaries.

**Tech Stack:** Rust 2021, Cargo workspace, Kotlin, Gradle Kotlin DSL, Jetpack Compose, GitHub Actions.

---

### Task 1: Repository Metadata And Documentation

**Files:**
- Create: `.gitignore`
- Create: `README.md`
- Create: `CONTRIBUTING.md`
- Create: `SECURITY.md`
- Create: `LICENSE-MIT`
- Create: `LICENSE-APACHE`
- Create: `docs/overview.md`
- Create: `docs/threat-model.md`
- Create: `docs/crypto-design.md`
- Create: `docs/card-format-v1.md`
- Create: `docs/vault-format-v1.md`
- Create: `docs/recovery-model.md`
- Create: `docs/android-architecture.md`
- Create: `docs/release-process.md`

- [ ] Add public experimental/unaudited warnings to README.
- [ ] Add concise project overview and M0 status.
- [ ] Add dual license files.
- [ ] Add security reporting and scope disclaimer.
- [ ] Add documentation placeholders that point back to the handoff boundaries.

### Task 2: Rust Workspace Skeleton

**Files:**
- Create: `Cargo.toml`
- Create: `crates/mcv-format/Cargo.toml`
- Create: `crates/mcv-format/src/lib.rs`
- Create: `crates/mcv-crypto/Cargo.toml`
- Create: `crates/mcv-crypto/src/lib.rs`
- Create: `crates/mcv-shamir/Cargo.toml`
- Create: `crates/mcv-shamir/src/lib.rs`
- Create: `crates/mcv-core/Cargo.toml`
- Create: `crates/mcv-core/src/lib.rs`
- Create: `crates/mcv-uniffi/Cargo.toml`
- Create: `crates/mcv-uniffi/src/lib.rs`

- [ ] Create workspace with five crates.
- [ ] Add minimal constants/types that compile without crypto behavior.
- [ ] Add crate-level tests for workspace wiring.
- [ ] Run `cargo fmt --check`.
- [ ] Run `cargo test --workspace`.
- [ ] Run `cargo clippy --workspace --all-targets -- -D warnings`.

### Task 3: Android Compose Skeleton

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/app/multicardvault/MainActivity.kt`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/test/java/app/multicardvault/McvAppTest.kt`

- [ ] Create Gradle Kotlin DSL Android app.
- [ ] Add minimal Compose screen with experimental/unaudited text.
- [ ] Add a JVM unit test that validates project identity text.
- [ ] Run `./gradlew test`.
- [ ] Run `./gradlew assembleDebug`.

### Task 4: CI Workflows

**Files:**
- Create: `.github/workflows/rust.yml`
- Create: `.github/workflows/android.yml`

- [ ] Add Rust CI for fmt, clippy, and tests.
- [ ] Add Android CI for test and assembleDebug.
- [ ] Keep workflows minimal and aligned with M0 commands.

### Task 5: Final Verification

- [ ] Run `find "/home/chris233/mcv" -maxdepth 4 -type f -print | sort`.
- [ ] Run all available local verification commands.
- [ ] Record any command that cannot run due missing network, Gradle wrapper, SDK, or sandbox limits.
