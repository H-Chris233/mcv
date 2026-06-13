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
use mcv_shamir::{BlahajSecretSharing, SecretSharing, ShamirError, Share, SSS_SHAMIR_GF256_V1};

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
    /// Encoded `CardPayloadV1` values.
    pub card_payloads: Vec<Vec<u8>>,
}

/// Request to unlock an existing vault.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UnlockVaultRequest {
    /// User password.
    pub password: String,
    /// Encoded `CardPayloadV1` values.
    pub card_payloads: Vec<Vec<u8>>,
}

/// Decrypted vault plaintext.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UnlockVaultResponse {
    /// Vault ID bytes recovered from card Data Fragments.
    pub vault_id: Vec<u8>,
    /// Scheme ID bytes recovered from card Data Fragments.
    pub scheme_id: Vec<u8>,
    /// Shares required to recover.
    pub threshold: u8,
    /// Total card payloads in the recovered scheme.
    pub total: u8,
    /// Encoded `VaultPlaintextV1`.
    pub plaintext: Vec<u8>,
}

/// Request to update an existing vault blob.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UpdateVaultRequest {
    /// User password.
    pub password: String,
    /// Encoded `CardPayloadV1` values.
    pub card_payloads: Vec<Vec<u8>>,
    /// Encoded replacement `VaultPlaintextV1`.
    pub new_plaintext: Vec<u8>,
}

/// Updated card payload set.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UpdateVaultResponse {
    /// Encoded replacement `CardPayloadV1` values.
    pub card_payloads: Vec<Vec<u8>>,
}

/// Non-sensitive card payload metadata for local inventory and verification.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CardPayloadInspection {
    /// Vault ID bytes.
    pub vault_id: Vec<u8>,
    /// Scheme ID bytes.
    pub scheme_id: Vec<u8>,
    /// Shares required to recover.
    pub threshold: u8,
    /// Total card payloads in the card set.
    pub total: u8,
    /// Current share index.
    pub share_index: u8,
    /// KDF algorithm identifier.
    pub kdf_id: u8,
    /// AEAD algorithm identifier.
    pub aead_id: u8,
    /// Card payload format version.
    pub format_version: u8,
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

/// Returns an encoded empty `VaultPlaintextV1`.
pub fn empty_vault_plaintext() -> Result<Vec<u8>, McvError> {
    VaultPlaintextV1 {
        entries: Vec::new(),
    }
    .encode()
    .map_err(|_error| McvError::InvalidVaultPlaintext)
}

/// Inspects non-sensitive card payload metadata without decrypting card material.
pub fn inspect_card_payload(bytes: &[u8]) -> Result<CardPayloadInspection, McvError> {
    let card = CardPayloadV1::decode(bytes).map_err(map_card_format_error)?;
    validate_algorithm_ids(card.kdf_id, card.aead_id, SSS_SHAMIR_GF256_V1)?;
    Ok(CardPayloadInspection {
        vault_id: card.vault_id,
        scheme_id: card.scheme_id,
        threshold: card.threshold,
        total: card.total,
        share_index: card.share_index,
        kdf_id: card.kdf_id,
        aead_id: card.aead_id,
        format_version: FORMAT_VERSION_V1,
    })
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
    let shares = BlahajSecretSharing::split(
        master_secret.as_slice(),
        request.threshold,
        request.total,
        rng,
    )
    .map_err(map_shamir_error)?;

    let vault_blob = encrypt_vault_blob(
        &request.password,
        master_secret.as_slice(),
        &context,
        &request.initial_plaintext,
        rng,
    )?;
    let card_payloads = build_card_payloads(
        &request.password,
        &context,
        shares.as_slice(),
        vault_blob.as_slice(),
        rng,
    )?;

    Ok(CreateVaultResponse {
        vault_id,
        scheme_id,
        card_payloads,
    })
}

/// Unlocks a vault and returns encoded `VaultPlaintextV1` bytes.
pub fn unlock_vault(request: UnlockVaultRequest) -> Result<UnlockVaultResponse, McvError> {
    let context = recover_unlock_context(&request.password, &request.card_payloads)?;
    let plaintext = aead_decrypt(
        context.final_key.as_slice(),
        &context.vault_blob.vault_nonce,
        &context.vault_blob.ciphertext,
        &context.vault_blob.aad().map_err(map_vault_format_error)?,
    )
    .map_err(map_vault_decrypt_error)?;
    validate_plaintext(plaintext.as_slice())?;

    Ok(UnlockVaultResponse {
        vault_id: context.vault_blob.vault_id,
        scheme_id: context.vault_blob.scheme_id,
        threshold: context.vault_blob.threshold,
        total: context.vault_blob.total,
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
    let context = recover_unlock_context(&request.password, &request.card_payloads)?;
    let scheme_id = random_secret(rng)[..ID_LEN].to_vec();
    let create_context = VaultCreateContext {
        vault_id: &context.vault_blob.vault_id,
        scheme_id: &scheme_id,
        threshold: context.vault_blob.threshold,
        total: context.vault_blob.total,
        kdf_params: context.vault_blob.kdf_params,
    };
    let master_secret = Zeroizing::new(random_secret(rng).to_vec());
    let shares = BlahajSecretSharing::split(
        master_secret.as_slice(),
        context.vault_blob.threshold,
        context.vault_blob.total,
        rng,
    )
    .map_err(map_shamir_error)?;
    let vault_blob = encrypt_vault_blob(
        &request.password,
        master_secret.as_slice(),
        &create_context,
        &request.new_plaintext,
        rng,
    )?;
    let card_payloads = build_card_payloads(
        &request.password,
        &create_context,
        shares.as_slice(),
        vault_blob.as_slice(),
        rng,
    )?;

    Ok(UpdateVaultResponse { card_payloads })
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
    card_payload_bytes: &[Vec<u8>],
) -> Result<UnlockContext, McvError> {
    let first_bytes = card_payload_bytes
        .first()
        .ok_or(McvError::NotEnoughShares)?;
    let first_card = CardPayloadV1::decode(first_bytes).map_err(map_card_format_error)?;
    let threshold = first_card.threshold;
    if card_payload_bytes.len() < usize::from(threshold) {
        return Err(McvError::NotEnoughShares);
    }

    let mut seen = HashSet::new();
    let mut cards = Vec::with_capacity(usize::from(threshold));
    let mut data_fragments = Vec::with_capacity(usize::from(threshold));

    for encoded in card_payload_bytes {
        let card = CardPayloadV1::decode(encoded).map_err(map_card_format_error)?;
        validate_card_matches_card(&card, &first_card)?;
        if !seen.insert(card.share_index) {
            return Err(McvError::DuplicateShareIndex);
        }
        let data_fragment = Share::new(card.share_index, card.data_fragment.clone())
            .map_err(|_error| McvError::InvalidCardPayload)?;
        data_fragments.push(data_fragment);
        cards.push(card);

        if cards.len() == usize::from(threshold) {
            break;
        }
    }

    if data_fragments.len() < usize::from(threshold) {
        return Err(McvError::NotEnoughShares);
    }

    let vault_blob_bytes =
        BlahajSecretSharing::recover(threshold, &data_fragments).map_err(map_shamir_error)?;
    let vault_blob = VaultBlobV1::decode(&vault_blob_bytes).map_err(map_vault_format_error)?;
    validate_algorithm_ids(vault_blob.kdf_id, vault_blob.aead_id, vault_blob.sss_id)?;

    for card in &cards {
        validate_card_matches_vault(card, &vault_blob)?;
    }

    let password_key = derive_password_key(password, &vault_blob.vault_salt, vault_blob.kdf_params)
        .map_err(map_crypto_error)?;
    let mut shares = Vec::with_capacity(usize::from(vault_blob.threshold));

    for card in &cards {
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
    }

    if shares.len() < usize::from(vault_blob.threshold) {
        return Err(McvError::NotEnoughShares);
    }

    let master_secret = Zeroizing::new(
        BlahajSecretSharing::recover(vault_blob.threshold, &shares).map_err(map_shamir_error)?,
    );
    if master_secret.len() != SECRET_LEN {
        return Err(McvError::ShamirError);
    }

    let final_key = derive_final_key(
        master_secret.as_slice(),
        password_key.as_slice(),
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
    data_fragment: &Share,
    rng: &mut impl Rng,
) -> Result<Vec<u8>, McvError> {
    if share.index() != data_fragment.index() {
        return Err(McvError::ShamirError);
    }
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
        data_fragment: data_fragment.value().to_vec(),
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
    master_secret: &[u8],
    context: &VaultCreateContext<'_>,
    plaintext: &[u8],
    rng: &mut impl Rng,
) -> Result<Vec<u8>, McvError> {
    let vault_salt = random_salt(rng).to_vec();
    let vault_nonce = random_nonce(rng).to_vec();
    let password_key =
        derive_password_key(password, &vault_salt, context.kdf_params).map_err(map_crypto_error)?;
    let final_key = derive_final_key(master_secret, password_key.as_slice(), &vault_salt)
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

fn validate_algorithm_ids(kdf_id: u8, aead_id: u8, sss_id: u8) -> Result<(), McvError> {
    if kdf_id != KDF_ARGON2ID_V1 || aead_id != AEAD_XCHACHA20_POLY1305_V1 {
        return Err(McvError::UnsupportedVersion);
    }
    if sss_id != SSS_SHAMIR_GF256_V1 {
        return Err(McvError::UnsupportedVersion);
    }
    Ok(())
}

fn build_card_payloads(
    password: &str,
    context: &VaultCreateContext<'_>,
    shares: &[Share],
    vault_blob: &[u8],
    rng: &mut impl Rng,
) -> Result<Vec<Vec<u8>>, McvError> {
    let data_fragments =
        BlahajSecretSharing::split(vault_blob, context.threshold, context.total, rng)
            .map_err(map_shamir_error)?;
    shares
        .iter()
        .zip(data_fragments.iter())
        .map(|(share, data_fragment)| wrap_card_share(password, context, share, data_fragment, rng))
        .collect()
}

fn validate_card_matches_card(
    card: &CardPayloadV1,
    expected: &CardPayloadV1,
) -> Result<(), McvError> {
    if card.kdf_id != expected.kdf_id || card.aead_id != expected.aead_id {
        return Err(McvError::UnsupportedVersion);
    }
    if card.vault_id != expected.vault_id {
        return Err(McvError::InvalidVaultId);
    }
    if card.scheme_id != expected.scheme_id {
        return Err(McvError::InvalidSchemeId);
    }
    if card.threshold != expected.threshold || card.total != expected.total {
        return Err(McvError::InvalidThreshold);
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

    use mcv_format::{CardPayloadV1, VaultEntryV1, DEFAULT_CARD_PAYLOAD_BUDGET};

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
    fn empty_vault_plaintext_returns_valid_encoded_plaintext() -> Result<(), McvError> {
        let encoded = empty_vault_plaintext()?;
        let decoded =
            VaultPlaintextV1::decode(&encoded).map_err(|_error| McvError::InvalidVaultPlaintext)?;

        assert!(decoded.entries.is_empty());
        Ok(())
    }

    #[test]
    fn create_and_unlock_any_three_of_five() -> Result<(), McvError> {
        let created = create()?;
        let unlocked = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
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
    fn inspect_card_payload_returns_only_header_metadata() -> Result<(), McvError> {
        let mut rng = ChaCha20Rng::seed_from_u64(42);
        let created = create_vault_with_rng(
            CreateVaultRequest {
                password: "passphrase".to_owned(),
                threshold: 2,
                total: 3,
                initial_plaintext: empty_vault_plaintext()?,
            },
            KdfParams::TEST,
            &mut rng,
        )?;

        let inspection = inspect_card_payload(&created.card_payloads[0])?;

        assert_eq!(inspection.vault_id, created.vault_id);
        assert_eq!(inspection.scheme_id, created.scheme_id);
        assert_eq!(inspection.threshold, 2);
        assert_eq!(inspection.total, 3);
        assert_eq!(inspection.share_index, 1);
        assert_eq!(inspection.kdf_id, KDF_ARGON2ID_V1);
        assert_eq!(inspection.aead_id, AEAD_XCHACHA20_POLY1305_V1);
        assert_eq!(inspection.format_version, mcv_format::FORMAT_VERSION_V1);
        Ok(())
    }

    #[test]
    fn inspect_card_payload_rejects_malformed_payload() {
        assert_eq!(
            inspect_card_payload(b"not a card"),
            Err(McvError::InvalidCardPayload)
        );
    }

    #[test]
    fn wrong_password_fails_card_authentication() -> Result<(), McvError> {
        let created = create()?;
        let result = unlock_vault(UnlockVaultRequest {
            password: "wrong".to_owned(),
            card_payloads: created.card_payloads[..3].to_vec(),
        });

        assert_eq!(result, Err(McvError::CardAuthenticationFailed));
        Ok(())
    }

    #[test]
    fn tampered_card_payload_fails() -> Result<(), McvError> {
        let created = create()?;
        let mut cards = created.card_payloads[..3].to_vec();
        let mut decoded = CardPayloadV1::decode(&cards[0]).map_err(map_card_format_error)?;
        let last = decoded.encrypted_share.len() - 1;
        decoded.encrypted_share[last] ^= 0x55;
        cards[0] = decoded.encode().map_err(map_card_format_error)?;

        let result = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            card_payloads: cards,
        });

        assert!(matches!(result, Err(McvError::CardAuthenticationFailed)));
        Ok(())
    }

    #[test]
    fn tampered_data_fragment_fails() -> Result<(), McvError> {
        let created = create()?;
        let mut cards = created.card_payloads[..3].to_vec();
        let last = cards[0].len() - 1;
        cards[0][last] ^= 0x55;

        let result = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            card_payloads: cards,
        });

        assert!(matches!(
            result,
            Err(McvError::InvalidCardPayload
                | McvError::InvalidVaultBlob
                | McvError::VaultAuthenticationFailed
                | McvError::ShamirError)
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
            card_payloads: vec![
                first.card_payloads[0].clone(),
                first.card_payloads[1].clone(),
                second.card_payloads[2].clone(),
            ],
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
                card_payloads: created.card_payloads[..3].to_vec(),
                new_plaintext: new_plaintext.clone(),
            },
            &mut rng,
        )?;
        let unlocked = unlock_vault(UnlockVaultRequest {
            password: "correct horse battery staple".to_owned(),
            card_payloads: updated.card_payloads[..3].to_vec(),
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
            let shares = BlahajSecretSharing::split(&secret, 3, 5, &mut rng)
                .map_err(|_error| McvError::ShamirError)?;
            let recovered = BlahajSecretSharing::recover(3, &shares[0..3])
                .map_err(|_error| McvError::ShamirError)?;
            assert_eq!(recovered, secret);
        }
        Ok(())
    }
}
