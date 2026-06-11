#![forbid(unsafe_code)]
#![doc = "Protocol format boundaries for Multi-Card Vault."]

/// Card payload magic for version 1 records.
pub const CARD_MAGIC_V1: [u8; 4] = *b"MCV1";

/// Vault blob magic for version 1 records.
pub const VAULT_MAGIC_V1: [u8; 4] = *b"MCVB";

/// First supported protocol format version.
pub const FORMAT_VERSION_V1: u8 = 1;

/// Stable 16-byte vault identifier.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct VaultId([u8; 16]);

impl VaultId {
    /// Creates a vault identifier from raw bytes.
    #[must_use]
    pub const fn new(bytes: [u8; 16]) -> Self {
        Self(bytes)
    }

    /// Returns the raw vault identifier bytes.
    #[must_use]
    pub const fn as_bytes(&self) -> &[u8; 16] {
        &self.0
    }
}

/// Stable 16-byte threshold scheme identifier.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct SchemeId([u8; 16]);

impl SchemeId {
    /// Creates a scheme identifier from raw bytes.
    #[must_use]
    pub const fn new(bytes: [u8; 16]) -> Self {
        Self(bytes)
    }

    /// Returns the raw scheme identifier bytes.
    #[must_use]
    pub const fn as_bytes(&self) -> &[u8; 16] {
        &self.0
    }
}

/// Threshold configuration validation error.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ThresholdError {
    /// A threshold of zero cannot recover anything.
    ZeroThreshold,
    /// A total of zero cannot describe a card set.
    ZeroTotal,
    /// The threshold cannot exceed the total card count.
    ThresholdExceedsTotal,
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn protocol_magic_values_are_stable() {
        assert_eq!(CARD_MAGIC_V1, [b'M', b'C', b'V', b'1']);
        assert_eq!(VAULT_MAGIC_V1, [b'M', b'C', b'V', b'B']);
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
    fn threshold_scheme_accepts_three_of_five() {
        assert_eq!(
            ThresholdScheme::new(3, 5),
            Ok(ThresholdScheme {
                threshold: 3,
                total: 5
            })
        );
    }
}
