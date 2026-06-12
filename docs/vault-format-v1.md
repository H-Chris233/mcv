# Vault Blob v1

Status: v0.1 MVP implemented in Rust.

Vault Blob v1 is fixed-order CBOR array data. It stores:

```text
[
  magic = "MCVB",
  version = 1,
  vault_id: bstr(16),
  scheme_id: bstr(16),
  kdf_id: u8,
  aead_id: u8,
  sss_id: u8,
  threshold: u8,
  total: u8,
  kdf_params,
  vault_salt: bstr(16),
  vault_nonce: bstr(24),
  ciphertext: bstr,
  created_at: i64?,
  updated_at: i64?
]
```

`scheme_id` is included so `unlock_vault` can validate cards using only the vault blob and card payloads. The authenticated vault header is the same array without `ciphertext`.

Vault Plaintext v1 is fixed-order CBOR:

```text
[
  magic = "MCVP",
  version = 1,
  entries: [
    [id: bstr(16), title: string, content: string, created_at: i64, updated_at: i64]
  ]
]
```

## Validation

- `magic` must be `MCVB`.
- `version` must be `1`.
- `vault_id` must match the selected local Vault record.
- `scheme_id` must match scanned Card Payloads.
- `kdf_id` must be Argon2id v1.
- `aead_id` must be XChaCha20-Poly1305 v1.
- `sss_id` must be Shamir GF(256) v1.
- `threshold` and `total` must be non-zero and `threshold <= total`.
- `vault_salt` must be 16 bytes.
- `vault_nonce` must be 24 bytes.
- `ciphertext` must decrypt with the Vault Blob header as AAD.
- Vault Plaintext v1 entries must contain 16-byte IDs and UTF-8 title/content strings.

## Storage

Android stores the encoded Vault Blob in Room. Room must not store Vault Plaintext, user passwords, unencrypted shares, `master_secret`, `password_key`, `final_key`, or raw `device_secret`.
