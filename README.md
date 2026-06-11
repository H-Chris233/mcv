# Multi-Card Vault

> This project is experimental and unaudited.
> Do not use it as the sole storage for irreplaceable secrets.
> This project is not designed for cloning access cards, bypassing access control systems, or interacting with third-party cards without authorization.

> 本项目处于实验阶段，尚未经过独立安全审计。
> 请勿将其作为不可替代敏感数据的唯一存储方案。
> 本项目不用于复制门禁卡、绕过门禁系统，或读取未授权的第三方卡片。

Multi-Card Vault (MCV) is an Android-only, local-first encrypted vault experiment. It uses multiple NFC cards as threshold key-share containers, combined with a user password and an Android device secret.

## M0 Status

This repository currently contains the project skeleton only:

- Rust multi-crate workspace.
- Minimal Android Compose app scaffold.
- Initial documentation, ADRs, and CI workflows.

M0 does not implement real cryptography, Shamir sharing, NFC read/write, Android Keystore access, Room persistence, or vault unlock flows.

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
cargo build -p mcv-uniffi
cargo run -p mcv-bindgen -- target/debug/libmcv_uniffi.so bindings/kotlin
```

Android:

```bash
./gradlew -p android test
./gradlew -p android assembleDebug
```

Android verification requires Gradle, Android SDK platform 35, and dependency resolution access.

## License

Licensed under either of:

- MIT License ([LICENSE-MIT](LICENSE-MIT))
- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE))
