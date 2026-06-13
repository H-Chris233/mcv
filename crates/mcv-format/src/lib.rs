#![forbid(unsafe_code)]
#![doc = "Protocol format boundaries for Multi-Card Vault."]

use std::io::Cursor;

use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Card payload magic for version 1 records.
pub const CARD_MAGIC_V1: [u8; 4] = *b"MCV1";

/// Vault blob magic for version 1 records.
pub const VAULT_MAGIC_V1: [u8; 4] = *b"MCVB";

/// Vault plaintext magic for version 1 records.
pub const PLAINTEXT_MAGIC_V1: [u8; 4] = *b"MCVP";

/// First supported protocol format version.
pub const FORMAT_VERSION_V1: u8 = 1;

/// Expected identifier length for vault and scheme IDs.
pub const ID_LEN: usize = 16;

/// Expected salt length for KDF inputs.
pub const SALT_LEN: usize = 16;

/// Expected XChaCha20-Poly1305 nonce length.
pub const XCHACHA20_NONCE_LEN: usize = 24;

/// Default upper bound for encoded NFC card payloads.
pub const DEFAULT_CARD_PAYLOAD_BUDGET: usize = 500;

/// Stable 16-byte vault identifier.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct VaultId([u8; ID_LEN]);

impl VaultId {
    /// Creates a vault identifier from raw bytes.
    #[must_use]
    pub const fn new(bytes: [u8; ID_LEN]) -> Self {
        Self(bytes)
    }

    /// Returns the raw vault identifier bytes.
    #[must_use]
    pub const fn as_bytes(&self) -> &[u8; ID_LEN] {
        &self.0
    }
}

/// Stable 16-byte threshold scheme identifier.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct SchemeId([u8; ID_LEN]);

impl SchemeId {
    /// Creates a scheme identifier from raw bytes.
    #[must_use]
    pub const fn new(bytes: [u8; ID_LEN]) -> Self {
        Self(bytes)
    }

    /// Returns the raw scheme identifier bytes.
    #[must_use]
    pub const fn as_bytes(&self) -> &[u8; ID_LEN] {
        &self.0
    }
}

/// Threshold configuration validation error.
#[derive(Clone, Copy, Debug, Eq, Error, PartialEq)]
pub enum ThresholdError {
    /// A threshold of zero cannot recover anything.
    #[error("threshold must be greater than zero")]
    ZeroThreshold,
    /// A total of zero cannot describe a card set.
    #[error("total must be greater than zero")]
    ZeroTotal,
    /// The threshold cannot exceed the total card count.
    #[error("threshold cannot exceed total")]
    ThresholdExceedsTotal,
    /// The total card count must fit GF(256) share indexes.
    #[error("total cannot exceed 255")]
    TotalTooLarge,
}

/// Validated threshold/total pair.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ThresholdScheme {
    threshold: u8,
    total: u8,
}

impl ThresholdScheme {
    /// Creates a validated threshold scheme.
    pub const fn new(threshold: u8, total: u8) -> Result<Self, ThresholdError> {
        if threshold == 0 {
            return Err(ThresholdError::ZeroThreshold);
        }

        if total == 0 {
            return Err(ThresholdError::ZeroTotal);
        }

        if threshold > total {
            return Err(ThresholdError::ThresholdExceedsTotal);
        }

        Ok(Self { threshold, total })
    }

    /// Returns the number of shares required to recover.
    #[must_use]
    pub const fn threshold(&self) -> u8 {
        self.threshold
    }

    /// Returns the total number of shares in the scheme.
    #[must_use]
    pub const fn total(&self) -> u8 {
        self.total
    }
}

/// Argon2id parameter set stored in v1 payloads.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct KdfParams {
    /// Memory cost in KiB.
    pub memory_cost_kib: u32,
    /// Number of iterations.
    pub time_cost: u32,
    /// Parallel lanes.
    pub parallelism: u32,
}

impl KdfParams {
    /// Production default for Android MVP.
    pub const DEFAULT: Self = Self {
        memory_cost_kib: 32 * 1024,
        time_cost: 3,
        parallelism: 1,
    };

    /// Low-cost parameter set for tests and deterministic vectors only.
    pub const TEST: Self = Self {
        memory_cost_kib: 64,
        time_cost: 1,
        parallelism: 1,
    };
}

/// Format parsing and validation failures.
#[derive(Debug, Error, PartialEq)]
pub enum FormatError {
    /// CBOR serialization failed.
    #[error("failed to encode CBOR")]
    Encode,
    /// CBOR deserialization failed.
    #[error("failed to decode CBOR")]
    Decode,
    /// Unsupported magic bytes.
    #[error("invalid magic")]
    InvalidMagic,
    /// Unsupported format version.
    #[error("unsupported version")]
    UnsupportedVersion,
    /// A fixed-width field had the wrong length.
    #[error("invalid field length for {field}")]
    InvalidLength { field: &'static str },
    /// Threshold configuration is invalid.
    #[error(transparent)]
    InvalidThreshold(#[from] ThresholdError),
    /// Share index is outside the selected threshold scheme.
    #[error("invalid share index")]
    InvalidShareIndex,
    /// Timestamp or entry field is invalid.
    #[error("invalid plaintext entry")]
    InvalidPlaintextEntry,
}

/// Card payload v1 model.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CardPayloadV1 {
    /// Vault this card belongs to.
    pub vault_id: Vec<u8>,
    /// Threshold scheme this card belongs to.
    pub scheme_id: Vec<u8>,
    /// Shares required to recover.
    pub threshold: u8,
    /// Total shares generated.
    pub total: u8,
    /// Current share index.
    pub share_index: u8,
    /// KDF algorithm identifier.
    pub kdf_id: u8,
    /// AEAD algorithm identifier.
    pub aead_id: u8,
    /// KDF parameters used for wrapping this share.
    pub kdf_params: KdfParams,
    /// Single-card KDF salt.
    pub card_salt: Vec<u8>,
    /// Single-card AEAD nonce.
    pub card_nonce: Vec<u8>,
    /// AEAD ciphertext containing the serialized Shamir share.
    pub encrypted_share: Vec<u8>,
    /// Serialized Shamir data fragment for reconstructing the encoded Vault Blob.
    pub data_fragment: Vec<u8>,
}

impl CardPayloadV1 {
    /// Validates field lengths and threshold metadata.
    pub fn validate(&self) -> Result<(), FormatError> {
        validate_len("vault_id", &self.vault_id, ID_LEN)?;
        validate_len("scheme_id", &self.scheme_id, ID_LEN)?;
        validate_len("card_salt", &self.card_salt, SALT_LEN)?;
        validate_len("card_nonce", &self.card_nonce, XCHACHA20_NONCE_LEN)?;

        let scheme = ThresholdScheme::new(self.threshold, self.total)?;
        if self.share_index == 0 || self.share_index > scheme.total() {
            return Err(FormatError::InvalidShareIndex);
        }

        Ok(())
    }

    /// Encodes this payload as fixed-order CBOR.
    pub fn encode(&self) -> Result<Vec<u8>, FormatError> {
        self.validate()?;
        encode_cbor(&CardPayloadWire(
            CARD_MAGIC_V1.to_vec(),
            FORMAT_VERSION_V1,
            self.vault_id.clone(),
            self.scheme_id.clone(),
            self.threshold,
            self.total,
            self.share_index,
            self.kdf_id,
            self.aead_id,
            self.kdf_params,
            self.card_salt.clone(),
            self.card_nonce.clone(),
            self.encrypted_share.clone(),
            self.data_fragment.clone(),
        ))
    }

    /// Decodes and validates a fixed-order CBOR card payload.
    pub fn decode(bytes: &[u8]) -> Result<Self, FormatError> {
        let wire: CardPayloadWire = decode_cbor(bytes)?;
        if wire.0.as_slice() != CARD_MAGIC_V1 {
            return Err(FormatError::InvalidMagic);
        }
        if wire.1 != FORMAT_VERSION_V1 {
            return Err(FormatError::UnsupportedVersion);
        }

        let payload = Self {
            vault_id: wire.2,
            scheme_id: wire.3,
            threshold: wire.4,
            total: wire.5,
            share_index: wire.6,
            kdf_id: wire.7,
            aead_id: wire.8,
            kdf_params: wire.9,
            card_salt: wire.10,
            card_nonce: wire.11,
            encrypted_share: wire.12,
            data_fragment: wire.13,
        };
        payload.validate()?;
        Ok(payload)
    }

    /// Encodes the authenticated card header without encrypted share bytes.
    pub fn aad(&self) -> Result<Vec<u8>, FormatError> {
        self.validate()?;
        encode_cbor(&CardHeaderWire(
            CARD_MAGIC_V1.to_vec(),
            FORMAT_VERSION_V1,
            self.vault_id.clone(),
            self.scheme_id.clone(),
            self.threshold,
            self.total,
            self.share_index,
            self.kdf_id,
            self.aead_id,
            self.kdf_params,
            self.card_salt.clone(),
            self.card_nonce.clone(),
            self.data_fragment.clone(),
        ))
    }
}

/// Vault blob v1 model.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VaultBlobV1 {
    /// Vault identifier.
    pub vault_id: Vec<u8>,
    /// Threshold scheme identifier required to match cards.
    pub scheme_id: Vec<u8>,
    /// KDF algorithm identifier.
    pub kdf_id: u8,
    /// AEAD algorithm identifier.
    pub aead_id: u8,
    /// Shamir algorithm identifier.
    pub sss_id: u8,
    /// Shares required to recover.
    pub threshold: u8,
    /// Total shares generated.
    pub total: u8,
    /// Vault password KDF parameters.
    pub kdf_params: KdfParams,
    /// Vault KDF salt.
    pub vault_salt: Vec<u8>,
    /// Vault AEAD nonce.
    pub vault_nonce: Vec<u8>,
    /// AEAD ciphertext containing `VaultPlaintextV1`.
    pub ciphertext: Vec<u8>,
    /// Creation timestamp, if known.
    pub created_at: Option<i64>,
    /// Last update timestamp, if known.
    pub updated_at: Option<i64>,
}

impl VaultBlobV1 {
    /// Validates field lengths and threshold metadata.
    pub fn validate(&self) -> Result<(), FormatError> {
        validate_len("vault_id", &self.vault_id, ID_LEN)?;
        validate_len("scheme_id", &self.scheme_id, ID_LEN)?;
        validate_len("vault_salt", &self.vault_salt, SALT_LEN)?;
        validate_len("vault_nonce", &self.vault_nonce, XCHACHA20_NONCE_LEN)?;
        ThresholdScheme::new(self.threshold, self.total)?;
        Ok(())
    }

    /// Encodes this blob as fixed-order CBOR.
    pub fn encode(&self) -> Result<Vec<u8>, FormatError> {
        self.validate()?;
        encode_cbor(&VaultBlobWire(
            VAULT_MAGIC_V1.to_vec(),
            FORMAT_VERSION_V1,
            self.vault_id.clone(),
            self.scheme_id.clone(),
            self.kdf_id,
            self.aead_id,
            self.sss_id,
            self.threshold,
            self.total,
            self.kdf_params,
            self.vault_salt.clone(),
            self.vault_nonce.clone(),
            self.ciphertext.clone(),
            self.created_at,
            self.updated_at,
        ))
    }

    /// Decodes and validates a fixed-order CBOR vault blob.
    pub fn decode(bytes: &[u8]) -> Result<Self, FormatError> {
        let wire: VaultBlobWire = decode_cbor(bytes)?;
        if wire.0.as_slice() != VAULT_MAGIC_V1 {
            return Err(FormatError::InvalidMagic);
        }
        if wire.1 != FORMAT_VERSION_V1 {
            return Err(FormatError::UnsupportedVersion);
        }

        let blob = Self {
            vault_id: wire.2,
            scheme_id: wire.3,
            kdf_id: wire.4,
            aead_id: wire.5,
            sss_id: wire.6,
            threshold: wire.7,
            total: wire.8,
            kdf_params: wire.9,
            vault_salt: wire.10,
            vault_nonce: wire.11,
            ciphertext: wire.12,
            created_at: wire.13,
            updated_at: wire.14,
        };
        blob.validate()?;
        Ok(blob)
    }

    /// Encodes the authenticated vault header without ciphertext bytes.
    pub fn aad(&self) -> Result<Vec<u8>, FormatError> {
        self.validate()?;
        encode_cbor(&VaultHeaderWire(
            VAULT_MAGIC_V1.to_vec(),
            FORMAT_VERSION_V1,
            self.vault_id.clone(),
            self.scheme_id.clone(),
            self.kdf_id,
            self.aead_id,
            self.sss_id,
            self.threshold,
            self.total,
            self.kdf_params,
            self.vault_salt.clone(),
            self.vault_nonce.clone(),
            self.created_at,
            self.updated_at,
        ))
    }
}

/// One plaintext entry inside a vault.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VaultEntryV1 {
    /// Stable entry ID.
    pub id: Vec<u8>,
    /// User-visible entry title.
    pub title: String,
    /// User-visible entry content.
    pub content: String,
    /// Creation timestamp.
    pub created_at: i64,
    /// Last update timestamp.
    pub updated_at: i64,
}

/// Plaintext vault contents.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct VaultPlaintextV1 {
    /// Entries stored in this vault.
    pub entries: Vec<VaultEntryV1>,
}

impl VaultPlaintextV1 {
    /// Encodes plaintext contents as fixed-order CBOR.
    pub fn encode(&self) -> Result<Vec<u8>, FormatError> {
        let entries: Result<Vec<_>, _> = self
            .entries
            .iter()
            .map(|entry| {
                validate_len("entry.id", &entry.id, ID_LEN)?;
                if entry.updated_at < entry.created_at {
                    return Err(FormatError::InvalidPlaintextEntry);
                }
                Ok(VaultEntryWire(
                    entry.id.clone(),
                    entry.title.clone(),
                    entry.content.clone(),
                    entry.created_at,
                    entry.updated_at,
                ))
            })
            .collect();

        encode_cbor(&VaultPlaintextWire(
            PLAINTEXT_MAGIC_V1.to_vec(),
            FORMAT_VERSION_V1,
            entries?,
        ))
    }

    /// Decodes plaintext contents from fixed-order CBOR.
    pub fn decode(bytes: &[u8]) -> Result<Self, FormatError> {
        let wire: VaultPlaintextWire = decode_cbor(bytes)?;
        if wire.0.as_slice() != PLAINTEXT_MAGIC_V1 {
            return Err(FormatError::InvalidMagic);
        }
        if wire.1 != FORMAT_VERSION_V1 {
            return Err(FormatError::UnsupportedVersion);
        }

        let entries: Result<Vec<_>, _> = wire
            .2
            .into_iter()
            .map(|entry| {
                validate_len("entry.id", &entry.0, ID_LEN)?;
                if entry.4 < entry.3 {
                    return Err(FormatError::InvalidPlaintextEntry);
                }
                Ok(VaultEntryV1 {
                    id: entry.0,
                    title: entry.1,
                    content: entry.2,
                    created_at: entry.3,
                    updated_at: entry.4,
                })
            })
            .collect();

        Ok(Self { entries: entries? })
    }
}

#[derive(Debug, Serialize, Deserialize)]
struct CardPayloadWire(
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    u8,
    u8,
    u8,
    u8,
    KdfParams,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
);

#[derive(Debug, Serialize, Deserialize)]
struct CardHeaderWire(
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    u8,
    u8,
    u8,
    u8,
    KdfParams,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
);

#[derive(Debug, Serialize, Deserialize)]
struct VaultBlobWire(
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    u8,
    u8,
    u8,
    u8,
    KdfParams,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    Option<i64>,
    Option<i64>,
);

#[derive(Debug, Serialize, Deserialize)]
struct VaultHeaderWire(
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    u8,
    u8,
    u8,
    u8,
    KdfParams,
    #[serde(with = "serde_bytes")] Vec<u8>,
    #[serde(with = "serde_bytes")] Vec<u8>,
    Option<i64>,
    Option<i64>,
);

#[derive(Debug, Serialize, Deserialize)]
struct VaultPlaintextWire(
    #[serde(with = "serde_bytes")] Vec<u8>,
    u8,
    Vec<VaultEntryWire>,
);

#[derive(Debug, Serialize, Deserialize)]
struct VaultEntryWire(
    #[serde(with = "serde_bytes")] Vec<u8>,
    String,
    String,
    i64,
    i64,
);

fn validate_len(field: &'static str, bytes: &[u8], expected: usize) -> Result<(), FormatError> {
    if bytes.len() == expected {
        Ok(())
    } else {
        Err(FormatError::InvalidLength { field })
    }
}

fn encode_cbor<T: Serialize>(value: &T) -> Result<Vec<u8>, FormatError> {
    let mut bytes = Vec::new();
    ciborium::ser::into_writer(value, &mut bytes).map_err(|_error| FormatError::Encode)?;
    Ok(bytes)
}

fn decode_cbor<T: for<'de> Deserialize<'de>>(bytes: &[u8]) -> Result<T, FormatError> {
    ciborium::de::from_reader(Cursor::new(bytes)).map_err(|_error| FormatError::Decode)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn id(byte: u8) -> Vec<u8> {
        vec![byte; ID_LEN]
    }

    #[test]
    fn protocol_magic_values_are_stable() {
        assert_eq!(CARD_MAGIC_V1, [b'M', b'C', b'V', b'1']);
        assert_eq!(VAULT_MAGIC_V1, [b'M', b'C', b'V', b'B']);
        assert_eq!(PLAINTEXT_MAGIC_V1, [b'M', b'C', b'V', b'P']);
        assert_eq!(FORMAT_VERSION_V1, 1);
    }

    #[test]
    fn threshold_scheme_rejects_invalid_values() {
        assert_eq!(
            ThresholdScheme::new(0, 5),
            Err(ThresholdError::ZeroThreshold)
        );
        assert_eq!(ThresholdScheme::new(3, 0), Err(ThresholdError::ZeroTotal));
        assert_eq!(
            ThresholdScheme::new(4, 3),
            Err(ThresholdError::ThresholdExceedsTotal)
        );
    }

    #[test]
    fn card_payload_roundtrips_as_cbor() -> Result<(), FormatError> {
        let payload = CardPayloadV1 {
            vault_id: id(1),
            scheme_id: id(2),
            threshold: 3,
            total: 5,
            share_index: 1,
            kdf_id: 1,
            aead_id: 1,
            kdf_params: KdfParams::TEST,
            card_salt: vec![3; SALT_LEN],
            card_nonce: vec![4; XCHACHA20_NONCE_LEN],
            encrypted_share: vec![5; 48],
            data_fragment: vec![1, 6, 7, 8],
        };

        let encoded = payload.encode()?;
        assert!(encoded.len() <= DEFAULT_CARD_PAYLOAD_BUDGET);
        assert_eq!(CardPayloadV1::decode(&encoded)?, payload);
        assert!(payload.aad()?.len() < encoded.len());
        Ok(())
    }

    #[test]
    fn vault_blob_roundtrips_as_cbor() -> Result<(), FormatError> {
        let blob = VaultBlobV1 {
            vault_id: id(1),
            scheme_id: id(2),
            kdf_id: 1,
            aead_id: 1,
            sss_id: 1,
            threshold: 3,
            total: 5,
            kdf_params: KdfParams::TEST,
            vault_salt: vec![3; SALT_LEN],
            vault_nonce: vec![4; XCHACHA20_NONCE_LEN],
            ciphertext: vec![5; 64],
            created_at: Some(10),
            updated_at: Some(11),
        };

        let encoded = blob.encode()?;
        assert_eq!(VaultBlobV1::decode(&encoded)?, blob);
        assert!(blob.aad()?.len() < encoded.len());
        Ok(())
    }

    #[test]
    fn vault_plaintext_roundtrips_as_cbor() -> Result<(), FormatError> {
        let plaintext = VaultPlaintextV1 {
            entries: vec![VaultEntryV1 {
                id: id(9),
                title: "alpha".to_owned(),
                content: "secret note".to_owned(),
                created_at: 10,
                updated_at: 11,
            }],
        };

        let encoded = plaintext.encode()?;
        assert_eq!(VaultPlaintextV1::decode(&encoded)?, plaintext);
        Ok(())
    }
}
