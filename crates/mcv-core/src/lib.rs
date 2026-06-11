#![forbid(unsafe_code)]
#![doc = "Core orchestration crate for Multi-Card Vault."]

use std::collections::HashSet;

use rand::rngs::OsRng;
use rand::Rng;
use thiserror::Error;
use zeroize::Zeroizing;

use mcv_crypto::{
    aead_decrypt, aead_encrypt, derive_final_key, derive_password_key, random_nonce, random_salt,
    random_secret, CryptoError, AEAD_XCHACHA20_POLY1305_V1, KDF_ARGON2ID_V1, SECRET_LEN,
};
use mcv_format::{
    CardPayloadV1, FormatError, KdfParams, VaultBlobV1, VaultPlaintextV1, FORMAT_VERSION_V1, ID_LEN,
};
use mcv_shamir::{SecretSharing, ShamirError, Share, SharksSecretSharing, SSS_SHAMIR_GF256_V1};

/// Project display name used across bindings and diagnostics.
pub const PROJECT_NAME: &str = "Multi-Card Vault";

/// Project status exposed to clients until the implementation is audited.
pub const PROJECT_STATUS: &str = "experimental and unaudited";

/// Static protocol identity exposed by the Rust core.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ProjectIdentity {
    /// Human-readable project name.
    pub name: &'static str,
    /// Current security status.
    pub status: &'static str,
    /// Format version.
    pub format_version: u8,
    /// Reserved KDF algorithm ID.
    pub kdf_id: u8,
    /// Reserved AEAD algorithm ID.
    pub aead_id: u8,
    /// Reserved Shamir algorithm ID.
    pub sss_id: u8,
}

/// Request to create a new vault and card payload set.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CreateVaultRequest {
    /// User password.
    pub password: String,
    /// Shares required to recover.
    pub threshold: u8,
    /// Total card payloads to generate.
    pub total: u8,
    /// Android device secret bytes.
    pub device_secret: Vec<u8>,
    /// Encoded `VaultPlaintextV1`.
    pub initial_plaintext: Vec<u8>,
}

/// Created vault and card payloads.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CreateVaultResponse {
    /// Vault ID bytes.
    pub vault_id: Vec<u8>,
    /// Scheme ID bytes.
    pub scheme_id: Vec<u8>,
    /// Encoded `VaultBlobV1`.
    pub vault_blob: Vec<u8>,
    /// Encoded `CardPayloadV1` values.
    pub card_payloads: Vec<Vec<u8>>,
}

/// Request to unlock an existing vault.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UnlockVaultRequest {
    /// User password.
    pub password: String,
    /// Android device secret bytes.
    pub device_secret: Vec<u8>,
    /// Encoded `VaultBlobV1`.
    pub vault_blob: Vec<u8>,
    /// Encoded `CardPayloadV1` values.
    pub card_payloads: Vec<Vec<u8>>,
}

/// Decrypted vault plaintext.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UnlockVaultResponse {
    /// Encoded `VaultPlaintextV1`.
    pub plaintext: Vec<u8>,
}

/// Request to update an existing vault blob.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UpdateVaultRequest {
    /// User password.
    pub password: String,
    /// Android device secret bytes.
    pub device_secret: Vec<u8>,
    /// Encoded `VaultBlobV1`.
    pub vault_blob: Vec<u8>,
    /// Encoded `CardPayloadV1` values.
    pub card_payloads: Vec<Vec<u8>>,
    /// Encoded replacement `VaultPlaintextV1`.
    pub new_plaintext: Vec<u8>,
}

/// Updated vault blob.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UpdateVaultResponse {
    /// Encoded replacement `VaultBlobV1`.
    pub new_vault_blob: Vec<u8>,
}

/// Multi-Card Vault core errors.
#[derive(Debug, Error, PartialEq)]
pub enum McvError {
    /// Unsupported format or algorithm version.
    #[error("unsupported version")]
    UnsupportedVersion,
    /// Invalid magic bytes.
    #[error("invalid magic")]
    InvalidMagic,
    /// Card belongs to a different vault.
    #[error("invalid vault id")]
    InvalidVaultId,
    /// Card belongs to a different threshold scheme.
    #[error("invalid scheme id")]
    InvalidSchemeId,
    /// Same share index was presented more than once.
    #[error("duplicate share index")]
    DuplicateShareIndex,
    /// Too few valid shares were provided.
    #[error("not enough shares")]
    NotEnoughShares,
    /// Threshold or total is invalid.
    #[error("invalid threshold scheme")]
    InvalidThreshold,
    /// Device secret is invalid.
    #[error("invalid device secret")]
    InvalidDeviceSecret,
    /// Card payload is malformed.
    #[error("invalid card payload")]
    InvalidCardPayload,
    /// Vault blob is malformed.
    #[error("invalid vault blob")]
    InvalidVaultBlob,
    /// Vault plaintext is malformed.
    #[error("invalid vault plaintext")]
    InvalidVaultPlaintext,
    /// Card AEAD authentication failed.
    #[error("card authentication failed")]
    CardAuthenticationFailed,
    /// Vault AEAD authentication failed.
    #[error("vault authentication failed")]
    VaultAuthenticationFailed,
    /// Cryptographic primitive failed.
    #[error("crypto error")]
    CryptoError,
    /// Shamir sharing failed.
    #[error("shamir error")]
    ShamirError,
}

/// Returns the current static project identity.
#[must_use]
pub const fn project_identity() -> ProjectIdentity {
    ProjectIdentity {
        name: PROJECT_NAME,
        status: PROJECT_STATUS,
        format_version: FORMAT_VERSION_V1,
        kdf_id: KDF_ARGON2ID_V1,
        aead_id: AEAD_XCHACHA20_POLY1305_V1,
        sss_id: SSS_SHAMIR_GF256_V1,
    }
}

/// Creates a vault using production randomness and default KDF parameters.
pub fn create_vault(request: CreateVaultRequest) -> Result<CreateVaultResponse, McvError> {
    let mut rng = OsRng;
    create_vault_with_rng(request, KdfParams::DEFAULT, &mut rng)
}

/// Creates a vault using caller-provided randomness and KDF parameters.
pub fn create_vault_with_rng(
    request: CreateVaultRequest,
    kdf_params: KdfParams,
    rng: &mut impl Rng,
) -> Result<CreateVaultResponse, McvError> {
    validate_device_secret(&request.device_secret)?;
    validate_plaintext(&request.initial_plaintext)?;
    validate_threshold(request.threshold, request.total)?;

    let vault_id = random_secret(rng)[..ID_LEN].to_vec();
    let scheme_id = random_secret(rng)[..ID_LEN].to_vec();
    let context = VaultCreateContext {
        vault_id: &vault_id,
        scheme_id: &scheme_id,
        threshold: request.threshold,
        total: request.total,
        kdf_params,
    };
    let master_secret = Zeroizing::new(random_secret(rng).to_vec());
    let shares = SharksSecretSharing::split(
        master_secret.as_slice(),
        request.threshold,
        request.total,
        rng,
    )
    .map_err(map_shamir_error)?;

    let card_payloads = shares
        .iter()
        .map(|share| wrap_card_share(&request.password, &context, share, rng))
        .collect::<Result<Vec<_>, _>>()?;

    let vault_blob = encrypt_vault_blob(
        &request.password,
        &request.device_secret,
        master_secret.as_slice(),
        &context,
        &request.initial_plaintext,
        rng,
    )?;

    Ok(CreateVaultResponse {
        vault_id,
        scheme_id,
        vault_blob,
        card_payloads,
    })
}

/// Unlocks a vault and returns encoded `VaultPlaintextV1` bytes.
pub fn unlock_vault(request: UnlockVaultRequest) -> Result<UnlockVaultResponse, McvError> {
    let context = recover_unlock_context(
        &request.password,
        &request.device_secret,
        &request.vault_blob,
        &request.card_payloads,
    )?;
    let plaintext = aead_decrypt(
        context.final_key.as_slice(),
        &context.vault_blob.vault_nonce,
        &context.vault_blob.ciphertext,
        &context.vault_blob.aad().map_err(map_vault_format_error)?,
    )
    .map_err(map_vault_decrypt_error)?;
    validate_plaintext(plaintext.as_slice())?;

    Ok(UnlockVaultResponse {
        plaintext: plaintext.to_vec(),
    })
}

/// Updates a vault by re-encrypting replacement plaintext with a fresh nonce.
pub fn update_vault(request: UpdateVaultRequest) -> Result<UpdateVaultResponse, McvError> {
    let mut rng = OsRng;
    update_vault_with_rng(request, &mut rng)
}

/// Updates a vault using caller-provided randomness.
pub fn update_vault_with_rng(
    request: UpdateVaultRequest,
    rng: &mut impl Rng,
) -> Result<UpdateVaultResponse, McvError> {
    validate_plaintext(&request.new_plaintext)?;
    let context = recover_unlock_context(
        &request.password,
        &request.device_secret,
        &request.vault_blob,
        &request.card_payloads,
    )?;

    let mut next_blob = context.vault_blob;
    next_blob.vault_nonce = random_nonce(rng).to_vec();
    next_blob.ciphertext.clear();
    let aad = next_blob.aad().map_err(map_vault_format_error)?;
    next_blob.ciphertext = aead_encrypt(
        context.final_key.as_slice(),
        &next_blob.vault_nonce,
        &request.new_plaintext,
        &aad,
    )
    .map_err(map_crypto_error)?;

    Ok(UpdateVaultResponse {
        new_vault_blob: next_blob.encode().map_err(map_vault_format_error)?,
    })
}

struct UnlockContext {
    vault_blob: VaultBlobV1,
    final_key: Zeroizing<[u8; SECRET_LEN]>,
}

struct VaultCreateContext<'a> {
    vault_id: &'a [u8],
    scheme_id: &'a [u8],
    threshold: u8,
    total: u8,
    kdf_params: KdfParams,
}

fn recover_unlock_context(
    password: &str,
    device_secret: &[u8],
    vault_blob_bytes: &[u8],
    card_payload_bytes: &[Vec<u8>],
) -> Result<UnlockContext, McvError> {
    validate_device_secret(device_secret)?;
    let vault_blob = VaultBlobV1::decode(vault_blob_bytes).map_err(map_vault_format_error)?;
    validate_algorithm_ids(vault_blob.kdf_id, vault_blob.aead_id, vault_blob.sss_id)?;

    if card_payload_bytes.len() < usize::from(vault_blob.threshold) {
        return Err(McvError::NotEnoughShares);
    }

    let password_key = derive_password_key(password, &vault_blob.vault_salt, vault_blob.kdf_params)
        .map_err(map_crypto_error)?;
    let mut seen = HashSet::new();
    let mut shares = Vec::with_capacity(usize::from(vault_blob.threshold));

    for encoded in card_payload_bytes {
        let card = CardPayloadV1::decode(encoded).map_err(map_card_format_error)?;
        validate_card_matches_vault(&card, &vault_blob)?;
        if !seen.insert(card.share_index) {
            return Err(McvError::DuplicateShareIndex);
        }

        let card_key = derive_password_key(password, &card.card_salt, card.kdf_params)
            .map_err(map_crypto_error)?;
        let share_bytes = aead_decrypt(
            card_key.as_slice(),
            &card.card_nonce,
            &card.encrypted_share,
            &card.aad().map_err(map_card_format_error)?,
        )
        .map_err(map_card_decrypt_error)?;
        let share = Share::new(card.share_index, share_bytes.to_vec())
            .map_err(|_error| McvError::InvalidCardPayload)?;
        shares.push(share);

        if shares.len() == usize::from(vault_blob.threshold) {
            break;
        }
    }

    if shares.len() < usize::from(vault_blob.threshold) {
        return Err(McvError::NotEnoughShares);
    }

    let master_secret = Zeroizing::new(
        SharksSecretSharing::recover(vault_blob.threshold, &shares).map_err(map_shamir_error)?,
    );
    if master_secret.len() != SECRET_LEN {
        return Err(McvError::ShamirError);
    }

    let final_key = derive_final_key(
        master_secret.as_slice(),
        password_key.as_slice(),
        device_secret,
        &vault_blob.vault_salt,
    )
    .map_err(map_crypto_error)?;

    Ok(UnlockContext {
        vault_blob,
        final_key,
    })
}

fn wrap_card_share(
    password: &str,
    context: &VaultCreateContext<'_>,
    share: &Share,
    rng: &mut impl Rng,
) -> Result<Vec<u8>, McvError> {
    let card_salt = random_salt(rng).to_vec();
    let card_nonce = random_nonce(rng).to_vec();
    let card_key =
        derive_password_key(password, &card_salt, context.kdf_params).map_err(map_crypto_error)?;
    let mut payload = CardPayloadV1 {
        vault_id: context.vault_id.to_vec(),
        scheme_id: context.scheme_id.to_vec(),
        threshold: context.threshold,
        total: context.total,
        share_index: share.index(),
        kdf_id: KDF_ARGON2ID_V1,
        aead_id: AEAD_XCHACHA20_POLY1305_V1,
        kdf_params: context.kdf_params,
        card_salt,
        card_nonce,
        encrypted_share: Vec::new(),
    };
    let aad = payload.aad().map_err(map_card_format_error)?;
    payload.encrypted_share = aead_encrypt(
        card_key.as_slice(),
        &payload.card_nonce,
        share.value(),
        &aad,
    )
    .map_err(map_crypto_error)?;
    payload.encode().map_err(map_card_format_error)
}

fn encrypt_vault_blob(
    password: &str,
    device_secret: &[u8],
    master_secret: &[u8],
    context: &VaultCreateContext<'_>,
    plaintext: &[u8],
    rng: &mut impl Rng,
) -> Result<Vec<u8>, McvError> {
    let vault_salt = random_salt(rng).to_vec();
    let vault_nonce = random_nonce(rng).to_vec();
    let password_key =
        derive_password_key(password, &vault_salt, context.kdf_params).map_err(map_crypto_error)?;
    let final_key = derive_final_key(
        master_secret,
        password_key.as_slice(),
        device_secret,
        &vault_salt,
    )
    .map_err(map_crypto_error)?;

    let mut blob = VaultBlobV1 {
        vault_id: context.vault_id.to_vec(),
        scheme_id: context.scheme_id.to_vec(),
        kdf_id: KDF_ARGON2ID_V1,
        aead_id: AEAD_XCHACHA20_POLY1305_V1,
        sss_id: SSS_SHAMIR_GF256_V1,
        threshold: context.threshold,
        total: context.total,
        kdf_params: context.kdf_params,
        vault_salt,
        vault_nonce,
        ciphertext: Vec::new(),
        created_at: None,
        updated_at: None,
    };
    let aad = blob.aad().map_err(map_vault_format_error)?;
    blob.ciphertext = aead_encrypt(final_key.as_slice(), &blob.vault_nonce, plaintext, &aad)
        .map_err(map_crypto_error)?;
    blob.encode().map_err(map_vault_format_error)
}

fn validate_plaintext(bytes: &[u8]) -> Result<(), McvError> {
    VaultPlaintextV1::decode(bytes)
        .map(|_plaintext| ())
        .map_err(|_error| McvError::InvalidVaultPlaintext)
}

fn validate_threshold(threshold: u8, total: u8) -> Result<(), McvError> {
    mcv_format::ThresholdScheme::new(threshold, total)
        .map(|_scheme| ())
        .map_err(|_error| McvError::InvalidThreshold)
}

fn validate_device_secret(device_secret: &[u8]) -> Result<(), McvError> {
    if device_secret.len() == SECRET_LEN {
        Ok(())
    } else {
        Err(McvError::InvalidDeviceSecret)
    }
}

fn validate_algorithm_ids(kdf_id: u8, aead_id: u8, sss_id: u8) -> Result<(), McvError> {
    if kdf_id != KDF_ARGON2ID_V1 || aead_id != AEAD_XCHACHA20_POLY1305_V1 {
        return Err(McvError::UnsupportedVersion);
    }
    if sss_id != SSS_SHAMIR_GF256_V1 {
        return Err(McvError::UnsupportedVersion);
    }
    Ok(())
}

fn validate_card_matches_vault(card: &CardPayloadV1, vault: &VaultBlobV1) -> Result<(), McvError> {
    validate_algorithm_ids(card.kdf_id, card.aead_id, vault.sss_id)?;
    if card.vault_id != vault.vault_id {
        return Err(McvError::InvalidVaultId);
    }
    if card.scheme_id != vault.scheme_id {
        return Err(McvError::InvalidSchemeId);
    }
    if card.threshold != vault.threshold || card.total != vault.total {
        return Err(McvError::InvalidThreshold);
    }
    Ok(())
}

fn map_card_format_error(error: FormatError) -> McvError {
    match error {
        FormatError::InvalidMagic => McvError::InvalidMagic,
        FormatError::UnsupportedVersion => McvError::UnsupportedVersion,
        FormatError::InvalidThreshold(_) => McvError::InvalidThreshold,
        _other => McvError::InvalidCardPayload,
    }
}

fn map_vault_format_error(error: FormatError) -> McvError {
    match error {
        FormatError::InvalidMagic => McvError::InvalidMagic,
        FormatError::UnsupportedVersion => McvError::UnsupportedVersion,
        FormatError::InvalidThreshold(_) => McvError::InvalidThreshold,
        _other => McvError::InvalidVaultBlob,
    }
}

fn map_crypto_error(error: CryptoError) -> McvError {
    match error {
        CryptoError::DecryptFailed => McvError::CryptoError,
        _other => McvError::CryptoError,
    }
}

fn map_card_decrypt_error(error: CryptoError) -> McvError {
    match error {
        CryptoError::DecryptFailed => McvError::CardAuthenticationFailed,
        _other => McvError::CryptoError,
    }
}

fn map_vault_decrypt_error(error: CryptoError) -> McvError {
    match error {
        CryptoError::DecryptFailed => McvError::VaultAuthenticationFailed,
        _other => McvError::CryptoError,
    }
}

fn map_shamir_error(error: ShamirError) -> McvError {
    match error {
        ShamirError::InvalidThreshold => McvError::InvalidThreshold,
        ShamirError::NotEnoughShares => McvError::NotEnoughShares,
        _other => McvError::ShamirError,
    }
}

#[cfg(test)]
mod tests {
    use rand::SeedableRng;
    use rand_chacha::ChaCha20Rng;

    use mcv_format::{VaultEntryV1, DEFAULT_CARD_PAYLOAD_BUDGET};

    use super::*;

    fn plaintext() -> Result<Vec<u8>, FormatError> {
        VaultPlaintextV1 {
            entries: vec![VaultEntryV1 {
                id: vec![9; ID_LEN],
                title: "entry".to_owned(),
                content: "content".to_owned(),
                created_at: 1,
                updated_at: 1,
            }],
        }
        .encode()
    }

    fn request() -> Result<CreateVaultRequest, FormatError> {
        Ok(CreateVaultRequest {
            password: "correct horse battery staple".to_owned(),
            threshold: 3,
            total: 5,
            device_secret: vec![7; SECRET_LEN],
            initial_plaintext: plaintext()?,
        })
    }

    fn create() -> Result<CreateVaultResponse, McvError> {
        let mut rng = ChaCha20Rng::from_seed([1_u8; 32]);
        let req = request().map_err(|_error| McvError::InvalidVaultPlaintext)?;
        create_vault_with_rng(req, KdfParams::TEST, &mut rng)
    }

    #[test]
    fn project_identity_wires_workspace_crates() {
        let identity = project_identity();

        assert_eq!(identity.name, "Multi-Card Vault");
        assert_eq!(identity.status, "experimental and unaudited");
        assert_eq!(identity.format_version, 1);
        assert_eq!(identity.kdf_id, 1);
        assert_eq!(identity.aead_id, 1);
        assert_eq!(identity.sss_id, 1);
    }

    #[test]
    fn create_and_unlock_any_three_of_five() -> Result<(), McvError> {
        let created = create()?;
        let unlocked = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: created.vault_blob,
            card_payloads: vec![
                created.card_payloads[0].clone(),
                created.card_payloads[2].clone(),
                created.card_payloads[4].clone(),
            ],
        })?;

        assert_eq!(
            VaultPlaintextV1::decode(&unlocked.plaintext)
                .map_err(|_error| McvError::InvalidVaultPlaintext)?
                .entries[0]
                .content,
            "content"
        );
        Ok(())
    }

    #[test]
    fn fewer_than_threshold_cards_cannot_unlock() -> Result<(), McvError> {
        let created = create()?;
        let result = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: created.vault_blob,
            card_payloads: created.card_payloads[..2].to_vec(),
        });

        assert_eq!(result, Err(McvError::NotEnoughShares));
        Ok(())
    }

    #[test]
    fn duplicate_card_cannot_count_twice() -> Result<(), McvError> {
        let created = create()?;
        let repeated = created.card_payloads[0].clone();
        let result = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: created.vault_blob,
            card_payloads: vec![
                repeated.clone(),
                repeated,
                created.card_payloads[1].clone(),
                created.card_payloads[2].clone(),
            ],
        });

        assert_eq!(result, Err(McvError::DuplicateShareIndex));
        Ok(())
    }

    #[test]
    fn wrong_password_fails_card_authentication() -> Result<(), McvError> {
        let created = create()?;
        let result = unlock_vault(UnlockVaultRequest {
            password: "wrong".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: created.vault_blob,
            card_payloads: created.card_payloads[..3].to_vec(),
        });

        assert_eq!(result, Err(McvError::CardAuthenticationFailed));
        Ok(())
    }

    #[test]
    fn tampered_card_payload_fails() -> Result<(), McvError> {
        let created = create()?;
        let mut cards = created.card_payloads[..3].to_vec();
        let last = cards[0].len() - 1;
        cards[0][last] ^= 0x55;

        let result = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: created.vault_blob,
            card_payloads: cards,
        });

        assert!(matches!(
            result,
            Err(McvError::InvalidCardPayload | McvError::CardAuthenticationFailed)
        ));
        Ok(())
    }

    #[test]
    fn tampered_vault_blob_fails() -> Result<(), McvError> {
        let created = create()?;
        let mut blob = created.vault_blob.clone();
        let last = blob.len() - 1;
        blob[last] ^= 0x55;

        let result = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: blob,
            card_payloads: created.card_payloads[..3].to_vec(),
        });

        assert!(matches!(
            result,
            Err(McvError::InvalidVaultBlob | McvError::VaultAuthenticationFailed)
        ));
        Ok(())
    }

    #[test]
    fn wrong_vault_card_fails_vault_id_check() -> Result<(), McvError> {
        let first = create()?;
        let mut rng = ChaCha20Rng::from_seed([2_u8; 32]);
        let second = create_vault_with_rng(
            request().map_err(|_error| McvError::InvalidVaultPlaintext)?,
            KdfParams::TEST,
            &mut rng,
        )?;

        let result = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: first.vault_blob,
            card_payloads: second.card_payloads[..3].to_vec(),
        });

        assert_eq!(result, Err(McvError::InvalidVaultId));
        Ok(())
    }

    #[test]
    fn update_vault_generates_new_blob() -> Result<(), McvError> {
        let created = create()?;
        let new_plaintext = VaultPlaintextV1 {
            entries: vec![VaultEntryV1 {
                id: vec![8; ID_LEN],
                title: "new".to_owned(),
                content: "new content".to_owned(),
                created_at: 2,
                updated_at: 2,
            }],
        }
        .encode()
        .map_err(|_error| McvError::InvalidVaultPlaintext)?;

        let mut rng = ChaCha20Rng::from_seed([3_u8; 32]);
        let updated = update_vault_with_rng(
            UpdateVaultRequest {
                password: "correct horse battery staple".to_owned(),
                device_secret: vec![7; SECRET_LEN],
                vault_blob: created.vault_blob,
                card_payloads: created.card_payloads[..3].to_vec(),
                new_plaintext: new_plaintext.clone(),
            },
            &mut rng,
        )?;
        let unlocked = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            device_secret: vec![7; SECRET_LEN],
            vault_blob: updated.new_vault_blob,
            card_payloads: created.card_payloads[..3].to_vec(),
        })?;

        assert_eq!(unlocked.plaintext, new_plaintext);
        Ok(())
    }

    #[test]
    fn card_payloads_stay_under_default_budget() -> Result<(), McvError> {
        let created = create()?;
        assert!(created
            .card_payloads
            .iter()
            .all(|payload| payload.len() <= DEFAULT_CARD_PAYLOAD_BUDGET));
        Ok(())
    }

    #[test]
    fn random_shamir_three_of_five_runs_1000_groups() -> Result<(), McvError> {
        let mut rng = ChaCha20Rng::from_seed([4_u8; 32]);
        for _case in 0..1000 {
            let secret = random_secret(&mut rng);
            let shares = SharksSecretSharing::split(&secret, 3, 5, &mut rng)
                .map_err(|_error| McvError::ShamirError)?;
            let recovered = SharksSecretSharing::recover(3, &shares[0..3])
                .map_err(|_error| McvError::ShamirError)?;
            assert_eq!(recovered, secret);
        }
        Ok(())
    }
}
