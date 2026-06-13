# CUID Card Storage Design

## Goal

Store recoverable Vault data on MIFARE Classic 1K compatible CUID Cards. A compatible device must recover a Vault from only the User Password and any Threshold-sized set of Cards.

## Current State

The current v0.1 implementation stores `VaultBlobV1` in local Room and writes `CardPayloadV1` values to NDEF tags. The Rust core derives `final_key` from Shamir Shares, User Password, and Android Device Secret. This contradicts ADR-0003 because the original machine and local Room record remain recovery requirements.

## Chosen Approach

Use the existing GF(256) Shamir boundary for both key material and Vault data:

- Keep `VaultBlobV1` as the encrypted representation of `VaultPlaintextV1`.
- Remove Device Secret from protocol-level `final_key` derivation.
- Split the encoded `VaultBlobV1` bytes into Threshold Shamir Data Fragments.
- Store one encrypted Share and one Data Fragment in every Card Payload.
- Recover by scanning any Threshold Card Payloads, reconstructing the Vault Blob from Data Fragments, reconstructing `master_secret` from Shares, deriving `final_key` from `master_secret + password_key`, then decrypting the recovered Vault Blob.

This avoids adding Reed-Solomon or another erasure-code dependency while preserving the "any Threshold Cards" recovery property.

## Android Card Storage

The first Android card backend targets MIFARE Classic 1K compatible CUID Cards through `MifareClassic`, not NDEF. It writes application-owned payload bytes into authenticated data blocks with default keys and does not modify sector trailers or rely on CUID UID rewrite behavior.

Room remains only for non-recovery metadata such as display name, Vault ID, Scheme ID, Threshold, Total, and timestamps. Room must not store recoverable Vault data after the migration.

## Update Model

Updating Vault Plaintext produces a new Vault Blob, new Data Fragments, and new Card Payloads. The UI must require rewriting all Total Cards before the updated Vault is considered complete. Partial Threshold-only rewrites are intentionally excluded because they create mixed old/new card sets.

## Capacity Boundary

MIFARE Classic 1K has a small usable payload budget after sector trailers and application framing. The implementation must enforce an explicit Card Payload size limit and fail before writing if the Vault is too large for the selected Threshold/Total card set.

## Testing

Rust tests must prove create/unlock/update works without Device Secret or local Vault Blob input. Android tests must prove use cases no longer call DeviceSecretRepository for recovery, and NFC tests must cover MIFARE Classic unsupported, capacity, read, and write result mapping where practical without physical hardware.
