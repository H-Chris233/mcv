# Vault Blob v1

Status: M1 implemented in Rust.

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
