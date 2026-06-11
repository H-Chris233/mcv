#![forbid(unsafe_code)]
#![doc = "Shamir Secret Sharing boundary crate for Multi-Card Vault."]

use std::convert::TryFrom;

use rand::Rng;
use sharks::{Share as SharksShare, Sharks};
use thiserror::Error;

/// Shamir GF(256) algorithm identifier reserved for protocol version 1.
pub const SSS_SHAMIR_GF256_V1: u8 = 1;

/// Shamir operation failure.
#[derive(Debug, Error, PartialEq)]
pub enum ShamirError {
    /// Threshold or total is invalid.
    #[error("invalid threshold scheme")]
    InvalidThreshold,
    /// Share bytes are not a valid sharks share.
    #[error("invalid share bytes")]
    InvalidShareBytes,
    /// There are not enough shares to recover a secret.
    #[error("not enough shares")]
    NotEnoughShares,
    /// Secret recovery failed.
    #[error("share recovery failed")]
    RecoverFailed,
}

/// A single indexed secret share.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Share {
    index: u8,
    value: Vec<u8>,
}

impl Share {
    /// Creates an indexed share.
    pub fn new(index: u8, value: Vec<u8>) -> Result<Self, ShamirError> {
        if index == 0 || value.len() < 2 || value.first().copied() != Some(index) {
            return Err(ShamirError::InvalidShareBytes);
        }
        Ok(Self { index, value })
    }

    /// Returns the share index.
    #[must_use]
    pub const fn index(&self) -> u8 {
        self.index
    }

    /// Returns the serialized share bytes.
    #[must_use]
    pub fn value(&self) -> &[u8] {
        &self.value
    }
}

/// Trait boundary for replaceable Shamir implementations.
pub trait SecretSharing {
    /// Splits a secret into `total` shares requiring `threshold` shares to recover.
    fn split(
        secret: &[u8],
        threshold: u8,
        total: u8,
        rng: &mut impl Rng,
    ) -> Result<Vec<Share>, ShamirError>;

    /// Recovers a secret from shares.
    fn recover(threshold: u8, shares: &[Share]) -> Result<Vec<u8>, ShamirError>;
}

/// GF(256) Shamir implementation backed by the `sharks` crate.
pub struct SharksSecretSharing;

impl SecretSharing for SharksSecretSharing {
    fn split(
        secret: &[u8],
        threshold: u8,
        total: u8,
        rng: &mut impl Rng,
    ) -> Result<Vec<Share>, ShamirError> {
        if threshold == 0 || total == 0 || threshold > total {
            return Err(ShamirError::InvalidThreshold);
        }

        let sharks = Sharks(threshold);
        let shares = sharks.dealer_rng(secret, rng).take(usize::from(total));
        shares
            .map(|share| {
                let bytes = Vec::from(&share);
                let index = bytes
                    .first()
                    .copied()
                    .ok_or(ShamirError::InvalidShareBytes)?;
                Share::new(index, bytes)
            })
            .collect()
    }

    fn recover(threshold: u8, shares: &[Share]) -> Result<Vec<u8>, ShamirError> {
        if threshold == 0 {
            return Err(ShamirError::InvalidThreshold);
        }
        if shares.len() < usize::from(threshold) {
            return Err(ShamirError::NotEnoughShares);
        }

        let parsed: Result<Vec<_>, _> = shares
            .iter()
            .take(usize::from(threshold))
            .map(|share| {
                SharksShare::try_from(share.value())
                    .map_err(|_error| ShamirError::InvalidShareBytes)
            })
            .collect();
        let parsed = parsed?;
        Sharks(threshold)
            .recover(&parsed)
            .map_err(|_error| ShamirError::RecoverFailed)
    }
}

#[cfg(test)]
mod tests {
    use rand::SeedableRng;
    use rand_chacha::ChaCha20Rng;

    use super::*;

    #[test]
    fn share_preserves_index_and_value() -> Result<(), ShamirError> {
        let share = Share::new(2, vec![2, 1, 2, 3])?;

        assert_eq!(share.index(), 2);
        assert_eq!(share.value(), &[2, 1, 2, 3]);
        Ok(())
    }

    #[test]
    fn algorithm_id_is_reserved_for_v1() {
        assert_eq!(SSS_SHAMIR_GF256_V1, 1);
    }

    #[test]
    fn sharks_split_and_recover_three_of_five() -> Result<(), ShamirError> {
        let mut rng = ChaCha20Rng::from_seed([1_u8; 32]);
        let secret = [42_u8; 32];
        let shares = SharksSecretSharing::split(&secret, 3, 5, &mut rng)?;
        let recovered = SharksSecretSharing::recover(3, &shares[1..4])?;

        assert_eq!(shares.len(), 5);
        assert_eq!(recovered, secret);
        assert_eq!(
            SharksSecretSharing::recover(3, &shares[0..2]),
            Err(ShamirError::NotEnoughShares)
        );
        Ok(())
    }
}
