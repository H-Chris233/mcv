# Crypto Design

Status: M1 implemented in Rust.

The Rust core uses Shamir Secret Sharing over GF(256), Argon2id, HKDF-SHA256, XChaCha20-Poly1305, fixed-order CBOR serialization, and caller-provided CSPRNG randomness.

## Key Derivation

`password_key = Argon2id(user_password, vault_salt, vault_kdf_params)`

`final_key = HKDF-SHA256(master_secret || password_key || device_secret, vault_salt, "mcv-vault-v1")`

Each card share is separately wrapped with:

`card_wrap_key = Argon2id(user_password, card_salt, card_kdf_params)`

## M1 Defaults

Production default Argon2id params are:

```text
memory_cost_kib = 32768
time_cost = 3
parallelism = 1
```

Tests and deterministic vectors use lower explicit params so CI remains fast. These test params are not production defaults.

## Error Surface

Rust keeps distinct errors for malformed payloads, wrong vault ID, wrong scheme ID, duplicate shares, card AEAD failure, and vault AEAD failure. UI code should still avoid exposing unnecessary cryptographic detail to ordinary users.
