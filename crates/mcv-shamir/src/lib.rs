#![forbid(unsafe_code)]
#![doc = "Shamir Secret Sharing boundary crate for Multi-Card Vault."]

/// Shamir GF(256) algorithm identifier reserved for protocol version 1.
pub const SSS_SHAMIR_GF256_V1: u8 = 1;

/// A single indexed secret share.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Share {
    index: u8,
    value: Vec<u8>,
}

impl Share {
    /// Creates an indexed share.
    #[must_use]
    pub fn new(index: u8, value: Vec<u8>) -> Self {
        Self { index, value }
    }

    /// Returns the share index.
    #[must_use]
    pub const fn index(&self) -> u8 {
        self.index
    }

    /// Returns the share payload bytes.
    #[must_use]
    pub fn value(&self) -> &[u8] {
        &self.value
    }
}

/// Trait boundary for replaceable Shamir implementations.
pub trait SecretSharing {
    /// Implementation-specific error.
    type Error;

    /// Splits a secret into `total` shares requiring `threshold` shares to recover.
    fn split(secret: &[u8], threshold: u8, total: u8) -> Result<Vec<Share>, Self::Error>;

    /// Recovers a secret from shares.
    fn recover(shares: &[Share]) -> Result<Vec<u8>, Self::Error>;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn share_preserves_index_and_value() {
        let share = Share::new(2, vec![1, 2, 3]);

        assert_eq!(share.index(), 2);
        assert_eq!(share.value(), &[1, 2, 3]);
    }

    #[test]
    fn algorithm_id_is_reserved_for_v1() {
        assert_eq!(SSS_SHAMIR_GF256_V1, 1);
    }
}
