#![forbid(unsafe_code)]
#![doc = "UniFFI binding boundary crate for Multi-Card Vault."]

use thiserror::Error;

/// UniFFI request to create a vault.
#[derive(Clone, Debug, Eq, PartialEq, uniffi::Record)]
pub struct CreateVaultRequest {
    /// User password.
    pub password: String,
    /// Shares required to recover.
    pub threshold: u8,
    /// Total cards to generate.
    pub total: u8,
    /// Android device secret bytes.
    pub device_secret: Vec<u8>,
    /// Encoded `VaultPlaintextV1`.
    pub initial_plaintext: Vec<u8>,
}

/// UniFFI response for vault creation.
#[derive(Clone, Debug, Eq, PartialEq, uniffi::Record)]
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

/// UniFFI request to unlock a vault.
#[derive(Clone, Debug, Eq, PartialEq, uniffi::Record)]
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

/// UniFFI response for vault unlock.
#[derive(Clone, Debug, Eq, PartialEq, uniffi::Record)]
pub struct UnlockVaultResponse {
    /// Encoded `VaultPlaintextV1`.
    pub plaintext: Vec<u8>,
}

/// UniFFI request to update a vault.
#[derive(Clone, Debug, Eq, PartialEq, uniffi::Record)]
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

/// UniFFI response for vault update.
#[derive(Clone, Debug, Eq, PartialEq, uniffi::Record)]
pub struct UpdateVaultResponse {
    /// Encoded replacement `VaultBlobV1`.
    pub new_vault_blob: Vec<u8>,
}

/// UniFFI-safe error surface.
#[derive(Debug, Error, uniffi::Error)]
pub enum McvFfiError {
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

/// Returns the project name through the binding boundary.
#[uniffi::export]
pub fn mcv_project_name() -> String {
    mcv_core::project_identity().name.to_owned()
}

/// Returns the project status through the binding boundary.
#[uniffi::export]
pub fn mcv_project_status() -> String {
    mcv_core::project_identity().status.to_owned()
}

/// Creates a vault through the binding boundary.
#[uniffi::export]
pub fn create_vault(request: CreateVaultRequest) -> Result<CreateVaultResponse, McvFfiError> {
    let response = mcv_core::create_vault(request.into()).map_err(McvFfiError::from)?;
    Ok(response.into())
}

/// Unlocks a vault through the binding boundary.
#[uniffi::export]
pub fn unlock_vault(request: UnlockVaultRequest) -> Result<UnlockVaultResponse, McvFfiError> {
    let response = mcv_core::unlock_vault(request.into()).map_err(McvFfiError::from)?;
    Ok(response.into())
}

/// Updates a vault through the binding boundary.
#[uniffi::export]
pub fn update_vault(request: UpdateVaultRequest) -> Result<UpdateVaultResponse, McvFfiError> {
    let response = mcv_core::update_vault(request.into()).map_err(McvFfiError::from)?;
    Ok(response.into())
}

impl From<CreateVaultRequest> for mcv_core::CreateVaultRequest {
    fn from(value: CreateVaultRequest) -> Self {
        Self {
            password: value.password,
            threshold: value.threshold,
            total: value.total,
            device_secret: value.device_secret,
            initial_plaintext: value.initial_plaintext,
        }
    }
}

impl From<mcv_core::CreateVaultResponse> for CreateVaultResponse {
    fn from(value: mcv_core::CreateVaultResponse) -> Self {
        Self {
            vault_id: value.vault_id,
            scheme_id: value.scheme_id,
            vault_blob: value.vault_blob,
            card_payloads: value.card_payloads,
        }
    }
}

impl From<UnlockVaultRequest> for mcv_core::UnlockVaultRequest {
    fn from(value: UnlockVaultRequest) -> Self {
        Self {
            password: value.password,
            device_secret: value.device_secret,
            vault_blob: value.vault_blob,
            card_payloads: value.card_payloads,
        }
    }
}

impl From<mcv_core::UnlockVaultResponse> for UnlockVaultResponse {
    fn from(value: mcv_core::UnlockVaultResponse) -> Self {
        Self {
            plaintext: value.plaintext,
        }
    }
}

impl From<UpdateVaultRequest> for mcv_core::UpdateVaultRequest {
    fn from(value: UpdateVaultRequest) -> Self {
        Self {
            password: value.password,
            device_secret: value.device_secret,
            vault_blob: value.vault_blob,
            card_payloads: value.card_payloads,
            new_plaintext: value.new_plaintext,
        }
    }
}

impl From<mcv_core::UpdateVaultResponse> for UpdateVaultResponse {
    fn from(value: mcv_core::UpdateVaultResponse) -> Self {
        Self {
            new_vault_blob: value.new_vault_blob,
        }
    }
}

impl From<mcv_core::McvError> for McvFfiError {
    fn from(value: mcv_core::McvError) -> Self {
        match value {
            mcv_core::McvError::UnsupportedVersion => Self::UnsupportedVersion,
            mcv_core::McvError::InvalidMagic => Self::InvalidMagic,
            mcv_core::McvError::InvalidVaultId => Self::InvalidVaultId,
            mcv_core::McvError::InvalidSchemeId => Self::InvalidSchemeId,
            mcv_core::McvError::DuplicateShareIndex => Self::DuplicateShareIndex,
            mcv_core::McvError::NotEnoughShares => Self::NotEnoughShares,
            mcv_core::McvError::InvalidThreshold => Self::InvalidThreshold,
            mcv_core::McvError::InvalidDeviceSecret => Self::InvalidDeviceSecret,
            mcv_core::McvError::InvalidCardPayload => Self::InvalidCardPayload,
            mcv_core::McvError::InvalidVaultBlob => Self::InvalidVaultBlob,
            mcv_core::McvError::InvalidVaultPlaintext => Self::InvalidVaultPlaintext,
            mcv_core::McvError::CardAuthenticationFailed => Self::CardAuthenticationFailed,
            mcv_core::McvError::VaultAuthenticationFailed => Self::VaultAuthenticationFailed,
            mcv_core::McvError::CryptoError => Self::CryptoError,
            mcv_core::McvError::ShamirError => Self::ShamirError,
        }
    }
}

uniffi::setup_scaffolding!();

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn binding_boundary_exposes_project_identity() {
        assert_eq!(mcv_project_name(), "Multi-Card Vault");
        assert_eq!(mcv_project_status(), "experimental and unaudited");
    }
}
