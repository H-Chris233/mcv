# Card Payload v1

Status: v0.1 MVP implemented in Rust.

Card Payload v1 is fixed-order CBOR array data. It stores:

```text
[
  magic = "MCV1",
  version = 1,
  vault_id: bstr(16),
  scheme_id: bstr(16),
  threshold: u8,
  total: u8,
  share_index: u8,
  kdf_id: u8,
  aead_id: u8,
  kdf_params,
  card_salt: bstr(16),
  card_nonce: bstr(24),
  encrypted_share: bstr
]
```

The authenticated card header is the same array without `encrypted_share`. The encrypted share plaintext is the serialized GF(256) Shamir share from the `blahaj` crate, whose first byte is the share index.

## Validation

- `magic` must be `MCV1`.
- `version` must be `1`.
- `vault_id` and `scheme_id` must match the selected Vault Blob.
- `threshold` and `total` must be non-zero and `threshold <= total`.
- `share_index` must be in `1..=total`.
- `kdf_id` must be Argon2id v1.
- `aead_id` must be XChaCha20-Poly1305 v1.
- `card_salt` must be 16 bytes.
- `card_nonce` must be 24 bytes.
- `encrypted_share` must decrypt with the Card Payload header as AAD.
- Repeated `share_index` values must not count twice.

## Capacity

The MVP targets NTAG216-compatible NDEF tags. Payload size should remain below the default budget tested by Rust core tests.
