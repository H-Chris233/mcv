# Contributing

Multi-Card Vault is experimental and unaudited. Treat every change as security-sensitive until proven otherwise.

## Development Principles

- Keep changes small and testable.
- Do not add cryptographic behavior without tests and documentation.
- Do not log passwords, keys, shares, plaintext vault data, or complete payloads.
- Do not add support for cloning, bypassing, or interacting with unauthorized third-party cards.
- Keep Android platform code out of Rust protocol crates.

## Local Checks

Run Rust checks before submitting Rust changes:

```bash
cargo fmt --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
```

Run Android checks before submitting Android changes:

```bash
./gradlew -p android ktlintCheck
./gradlew -p android detekt
./gradlew -p android lintDebug
./gradlew -p android test
./gradlew -p android assembleDebug
```

## Documentation

Protocol or recovery-model changes must update the relevant document under `docs/` and add an ADR when the decision is hard to reverse or surprising without context.
