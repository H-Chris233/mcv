# Card Payload v1

Status: M1 implemented in Rust.

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

The authenticated card header is the same array without `encrypted_share`. The encrypted share plaintext is the serialized GF(256) Shamir share from the `sharks` crate, whose first byte is the share index.
