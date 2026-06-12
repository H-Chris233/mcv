# Recovery Model

The MVP does not support cross-device recovery.

As recorded in ADR-0002, the MVP requires the current device secret. Encrypted backup import is not equivalent to cross-device recovery.

## Required Unlock Material

Unlock requires all of:

- Enough valid NFC Card Payloads for the Vault threshold.
- The user password.
- The current Android Device Secret.
- The local Vault Blob.

Missing any one of these prevents decrypting the Vault Blob in the MVP.

## Supported Recovery Cases

- App restart after a completed Vault creation: supported through Room, Keystore-wrapped Device Secret, and saved Vault unlock.
- Re-scanning any threshold-sized subset of valid cards: supported.
- Duplicate card scans: rejected and not counted toward threshold.
- Wrong Vault cards: rejected through `vault_id` and `scheme_id` checks.

## Unsupported Recovery Cases

- Lost user password.
- Fewer than threshold valid cards.
- Deleted or inaccessible Android Device Secret.
- Moving only the Vault Blob to another device.
- Interrupted write-card flow after app process death.
- Card replacement.
- Backup import/export.

The unsupported cases are intentional MVP boundaries, not hidden features. Cross-device recovery should be designed later as a separate protocol with explicit security review.
