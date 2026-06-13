#![forbid(unsafe_code)]
#![doc = "Cryptographic boundary crate for Multi-Card Vault."]

use argon2::{Algorithm, Argon2, Params, Version};
use chacha20poly1305::aead::{Aead, Payload};
use chacha20poly1305::{KeyInit, XChaCha20Poly1305, XNonce};
use hkdf::Hkdf;
use rand::RngCore;
use sha2::Sha256;
use thiserror::Error;
use zeroize::Zeroizing;

use mcv_format::{KdfParams, SALT_LEN, XCHACHA20_NONCE_LEN};

/// Argon2id algorithm identifier reserved for protocol version 1.
pub const KDF_ARGON2ID_V1: u8 = 1;

/// XChaCha20-Poly1305 algorithm identifier reserved for protocol version 1.
pub const AEAD_XCHACHA20_POLY1305_V1: u8 = 1;

/// HKDF-SHA256 info label for vault key derivation.
pub const VAULT_HKDF_INFO_V1: &[u8] = b"mcv-vault-v1";

/// Fixed 32-byte secret length used by the intended MVP protocol.
pub const SECRET_LEN: usize = 32;

/// Cryptographic operation failure.
#[derive(Debug, Error, PartialEq)]
pub enum CryptoError {
    /// Argon2 parameter conversion failed.
    #[error("invalid Argon2id parameters")]
    InvalidKdfParams,
    /// Argon2id derivation failed.
    #[error("password key derivation failed")]
    KdfFailed,
    /// HKDF expansion failed.
    #[error("HKDF expansion failed")]
    HkdfFailed,
    /// AEAD key had an invalid length.
    #[error("invalid AEAD key length")]
    InvalidKeyLength,
    /// AEAD nonce had an invalid length.
    #[error("invalid AEAD nonce length")]
    InvalidNonceLength,
    /// AEAD encryption failed.
    #[error("AEAD encryption failed")]
    EncryptFailed,
    /// AEAD authentication or decryption failed.
    #[error("AEAD authentication failed")]
    DecryptFailed,
}

/// Generates random bytes using the caller-provided CSPRNG.
pub fn random_bytes<const N: usize>(rng: &mut impl RngCore) -> [u8; N] {
    let mut bytes = [0_u8; N];
    rng.fill_bytes(&mut bytes);
    bytes
}

/// Generates a v1 salt.
pub fn random_salt(rng: &mut impl RngCore) -> [u8; SALT_LEN] {
    random_bytes(rng)
}

/// Generates a v1 XChaCha20-Poly1305 nonce.
pub fn random_nonce(rng: &mut impl RngCore) -> [u8; XCHACHA20_NONCE_LEN] {
    random_bytes(rng)
}

/// Generates a 32-byte secret.
pub fn random_secret(rng: &mut impl RngCore) -> [u8; SECRET_LEN] {
    random_bytes(rng)
}

/// Derives a 32-byte password key using Argon2id.
pub fn derive_password_key(
    password: &str,
    salt: &[u8],
    params: KdfParams,
) -> Result<Zeroizing<[u8; SECRET_LEN]>, CryptoError> {
    let argon_params = Params::new(
        params.memory_cost_kib,
        params.time_cost,
        params.parallelism,
        Some(SECRET_LEN),
    )
    .map_err(|_error| CryptoError::InvalidKdfParams)?;

    let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, argon_params);
    let mut output = Zeroizing::new([0_u8; SECRET_LEN]);
    argon2
        .hash_password_into(password.as_bytes(), salt, output.as_mut())
        .map_err(|_error| CryptoError::KdfFailed)?;
    Ok(output)
}

/// Derives the final vault key from card and password factors.
pub fn derive_final_key(
    master_secret: &[u8],
    password_key: &[u8],
    salt: &[u8],
) -> Result<Zeroizing<[u8; SECRET_LEN]>, CryptoError> {
    let mut ikm = Zeroizing::new(Vec::with_capacity(master_secret.len() + password_key.len()));
    ikm.extend_from_slice(master_secret);
    ikm.extend_from_slice(password_key);

    let hk = Hkdf::<Sha256>::new(Some(salt), ikm.as_slice());
    let mut output = Zeroizing::new([0_u8; SECRET_LEN]);
    hk.expand(VAULT_HKDF_INFO_V1, output.as_mut())
        .map_err(|_error| CryptoError::HkdfFailed)?;
    Ok(output)
}

/// Encrypts plaintext with XChaCha20-Poly1305 and AAD.
pub fn aead_encrypt(
    key: &[u8],
    nonce: &[u8],
    plaintext: &[u8],
    aad: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    if key.len() != SECRET_LEN {
        return Err(CryptoError::InvalidKeyLength);
    }
    if nonce.len() != XCHACHA20_NONCE_LEN {
        return Err(CryptoError::InvalidNonceLength);
    }

    let cipher =
        XChaCha20Poly1305::new_from_slice(key).map_err(|_error| CryptoError::InvalidKeyLength)?;
    cipher
        .encrypt(
            XNonce::from_slice(nonce),
            Payload {
                msg: plaintext,
                aad,
            },
        )
        .map_err(|_error| CryptoError::EncryptFailed)
}

/// Decrypts ciphertext with XChaCha20-Poly1305 and AAD.
pub fn aead_decrypt(
    key: &[u8],
    nonce: &[u8],
    ciphertext: &[u8],
    aad: &[u8],
) -> Result<Zeroizing<Vec<u8>>, CryptoError> {
    if key.len() != SECRET_LEN {
        return Err(CryptoError::InvalidKeyLength);
    }
    if nonce.len() != XCHACHA20_NONCE_LEN {
        return Err(CryptoError::InvalidNonceLength);
    }

    let cipher =
        XChaCha20Poly1305::new_from_slice(key).map_err(|_error| CryptoError::InvalidKeyLength)?;
    let plaintext = cipher
        .decrypt(
            XNonce::from_slice(nonce),
            Payload {
                msg: ciphertext,
                aad,
            },
        )
        .map_err(|_error| CryptoError::DecryptFailed)?;
    Ok(Zeroizing::new(plaintext))
}

#[cfg(test)]
mod tests {
    use rand::SeedableRng;
    use rand_chacha::ChaCha20Rng;

    use super::*;

    #[test]
    fn algorithm_ids_are_reserved_for_v1() {
        assert_eq!(KDF_ARGON2ID_V1, 1);
        assert_eq!(AEAD_XCHACHA20_POLY1305_V1, 1);
        assert_eq!(VAULT_HKDF_INFO_V1, b"mcv-vault-v1");
        assert_eq!(SECRET_LEN, 32);
    }

    #[test]
    fn aead_roundtrip_authenticates_aad() -> Result<(), CryptoError> {
        let key = [7_u8; SECRET_LEN];
        let nonce = [8_u8; XCHACHA20_NONCE_LEN];
        let plaintext = b"vault plaintext";
        let aad = b"header";

        let ciphertext = aead_encrypt(&key, &nonce, plaintext, aad)?;
        let decrypted = aead_decrypt(&key, &nonce, &ciphertext, aad)?;

        assert_eq!(decrypted.as_slice(), plaintext);
        assert_eq!(
            aead_decrypt(&key, &nonce, &ciphertext, b"tampered"),
            Err(CryptoError::DecryptFailed)
        );
        Ok(())
    }

    #[test]
    fn kdf_and_hkdf_are_deterministic_for_same_inputs() -> Result<(), CryptoError> {
        let first = derive_password_key("correct horse", &[1_u8; SALT_LEN], KdfParams::TEST)?;
        let second = derive_password_key("correct horse", &[1_u8; SALT_LEN], KdfParams::TEST)?;
        let final_key = derive_final_key(&[2_u8; SECRET_LEN], first.as_slice(), &[4_u8; SALT_LEN])?;

        assert_eq!(first.as_slice(), second.as_slice());
        assert_eq!(final_key.len(), SECRET_LEN);
        Ok(())
    }

    #[test]
    fn random_helpers_fill_expected_lengths() {
        let mut rng = ChaCha20Rng::from_seed([9_u8; 32]);

        assert_eq!(random_salt(&mut rng).len(), SALT_LEN);
        assert_eq!(random_nonce(&mut rng).len(), XCHACHA20_NONCE_LEN);
        assert_eq!(random_secret(&mut rng).len(), SECRET_LEN);
    }
}
