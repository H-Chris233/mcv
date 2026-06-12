# Test Vectors

These vectors are public deterministic fixtures for the experimental MVP protocol.
They are not production secrets.

## Regeneration

```bash
cargo run -p mcv-core --example mvp_vectors
cargo run -p mcv-core --example m1_vector
```

`mvp_vectors` covers low-level Shamir, HKDF, and AEAD fixtures.
`m1_vector` covers end-to-end CardPayload and VaultBlob fixtures.
