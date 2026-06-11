#![forbid(unsafe_code)]
#![doc = "Core orchestration crate for Multi-Card Vault."]

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
    /// Card magic for v1 payloads.
    pub card_magic: [u8; 4],
    /// Vault magic for v1 blobs.
    pub vault_magic: [u8; 4],
    /// Format version.
    pub format_version: u8,
    /// Reserved KDF algorithm ID.
    pub kdf_id: u8,
    /// Reserved AEAD algorithm ID.
    pub aead_id: u8,
    /// Reserved Shamir algorithm ID.
    pub sss_id: u8,
}

/// Returns the current static project identity.
#[must_use]
pub const fn project_identity() -> ProjectIdentity {
    ProjectIdentity {
        name: PROJECT_NAME,
        status: PROJECT_STATUS,
        card_magic: mcv_format::CARD_MAGIC_V1,
        vault_magic: mcv_format::VAULT_MAGIC_V1,
        format_version: mcv_format::FORMAT_VERSION_V1,
        kdf_id: mcv_crypto::KDF_ARGON2ID_V1,
        aead_id: mcv_crypto::AEAD_XCHACHA20_POLY1305_V1,
        sss_id: mcv_shamir::SSS_SHAMIR_GF256_V1,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn project_identity_wires_workspace_crates() {
        let identity = project_identity();

        assert_eq!(identity.name, "Multi-Card Vault");
        assert_eq!(identity.status, "experimental and unaudited");
        assert_eq!(identity.card_magic, [b'M', b'C', b'V', b'1']);
        assert_eq!(identity.vault_magic, [b'M', b'C', b'V', b'B']);
        assert_eq!(identity.format_version, 1);
        assert_eq!(identity.kdf_id, 1);
        assert_eq!(identity.aead_id, 1);
        assert_eq!(identity.sss_id, 1);
    }
}
