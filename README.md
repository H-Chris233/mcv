# Multi-Card Vault

> This project is experimental and unaudited.
> Do not use it as the sole storage for irreplaceable secrets.
> This project is not designed for cloning access cards, bypassing access control systems, or interacting with third-party cards without authorization.

> 本项目处于实验阶段，尚未经过独立安全审计。
> 请勿将其作为不可替代敏感数据的唯一存储方案。
> 本项目不用于复制门禁卡、绕过门禁系统，或读取未授权的第三方卡片。

Multi-Card Vault (MCV) is an Android-only, local-first encrypted vault experiment. It stores recoverable Vault data on multiple MIFARE Classic 1K compatible CUID Cards and unlocks with a user password plus any threshold-sized card set.

## Current Status

This repository currently contains:

- Rust multi-crate workspace.
- Rust protocol core for create/unlock/update flows.
- Android Compose app with Rust UniFFI integration, Room metadata persistence, DataStore settings, MIFARE Classic 1K CUID card write/read/unlock/recovery, saved Vault metadata list, diagnostics, and a minimal vault entry editor.
- Protocol documentation, threat model, ADRs, test vectors, and CI workflows.

The v0.1 MVP can write generated encrypted Card Payloads to CUID Cards, read threshold cards back, recover the Vault Blob from card Data Fragments, unlock the Vault with or without pre-existing local metadata, display Vault Plaintext entries, add/edit/delete simple text entries through Rust `update_vault`, rewrite all cards after an update, keep non-recovery Vault metadata in Room, persist lightweight settings in DataStore, and produce release artifacts through CI. It does not implement full multi-vault navigation or app-restart recovery for an interrupted write-card flow yet.

## Repository Layout

```text
android/              Android client scaffold
crates/               Rust workspace crates
docs/                 Architecture, protocol, and process docs
docs/adr/             Architecture decision records
docs/agents/          Agent workflow configuration
docs/superpowers/     Design specs and implementation plans
```

## Verification

Rust:

```bash
cargo fmt --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
cargo audit
cargo deny check advisories bans sources
cargo build -p mcv-uniffi
cargo run -p mcv-bindgen -- target/debug/libmcv_uniffi.so bindings/kotlin
```

Android:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
sdkmanager "ndk;27.2.12479018"
./gradlew -p android ktlintCheck
./gradlew -p android detekt
./gradlew -p android lintDebug
./gradlew -p android test
./gradlew -p android assembleDebug
```

Android verification requires Gradle, Android SDK platform 35, NDK 27.2.12479018, Rust Android targets, `cargo-ndk`, and dependency resolution access. The Gradle build compiles the Rust UniFFI library instead of storing native `.so` files in git.

## License

Licensed under either of:

- MIT License ([LICENSE-MIT](LICENSE-MIT))
- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE))
