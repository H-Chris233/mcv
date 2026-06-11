#![forbid(unsafe_code)]
#![doc = "Cryptographic boundary crate for Multi-Card Vault."]

/// Argon2id algorithm identifier reserved for protocol version 1.
pub const KDF_ARGON2ID_V1: u8 = 1;

/// XChaCha20-Poly1305 algorithm identifier reserved for protocol version 1.
pub const AEAD_XCHACHA20_POLY1305_V1: u8 = 1;

/// HKDF-SHA256 info label for vault key derivation.
pub const VAULT_HKDF_INFO_V1: &[u8] = b"mcv-vault-v1";

/// Fixed 32-byte secret length used by the intended MVP protocol.
pub const SECRET_LEN: usize = 32;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn algorithm_ids_are_reserved_for_v1() {
        assert_eq!(KDF_ARGON2ID_V1, 1);
        assert_eq!(AEAD_XCHACHA20_POLY1305_V1, 1);
        assert_eq!(VAULT_HKDF_INFO_V1, b"mcv-vault-v1");
        assert_eq!(SECRET_LEN, 32);
    }
}
