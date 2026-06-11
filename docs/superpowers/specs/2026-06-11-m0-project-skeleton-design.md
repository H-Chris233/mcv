# M0 Project Skeleton Design

## Goal

Create the initial Multi-Card Vault repository skeleton: Rust multi-crate workspace, minimal Android Compose app, CI workflows, open-source metadata, and protocol documentation placeholders.

## Scope

M0 delivers buildable infrastructure only. It does not implement cryptography, Shamir sharing, NFC read/write, Android persistence, Keystore access, or real vault creation/unlock flows.

## Architecture

The Rust side uses a Cargo workspace with separate crates for format, crypto, Shamir, core orchestration, and UniFFI bindings, matching ADR-0001. Each crate starts with a minimal public surface and tests that prove the workspace compiles.

The Android side uses a minimal Gradle Kotlin DSL project under `android/` with a single Compose activity. The app identifies itself as experimental and unaudited, but contains no business UI beyond a launchable placeholder.

The documentation side establishes the public safety boundary immediately: README warnings, dual license files, SECURITY.md, CONTRIBUTING.md, and format/design placeholders.

## Components

- `Cargo.toml`: workspace membership and shared package metadata.
- `crates/mcv-format`: protocol constants and basic type placeholders.
- `crates/mcv-crypto`: cryptographic boundary crate placeholder.
- `crates/mcv-shamir`: Shamir trait boundary placeholder.
- `crates/mcv-core`: orchestration crate depending on lower-level crates.
- `crates/mcv-uniffi`: binding crate placeholder depending on `mcv-core`.
- `android/`: minimal Android app that can run `assembleDebug`.
- `.github/workflows/`: Rust and Android CI workflows.
- `docs/`: required project documentation placeholders.

## Data Flow

There is no real vault data flow in M0. Rust crates expose version/identity constants and compile-time boundaries only. Android displays a static entry screen and does not call Rust yet.

## Error Handling

M0 avoids placeholder runtime behavior that could be mistaken for security functionality. Unsupported or unimplemented operations are not exposed as user flows.

## Testing

- `cargo fmt --check`
- `cargo clippy --workspace --all-targets -- -D warnings`
- `cargo test --workspace`
- `./gradlew test`
- `./gradlew assembleDebug`

Android verification may depend on Gradle wrapper availability and installed Android SDK.
