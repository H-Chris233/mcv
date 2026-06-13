# Recovery Model

The MVP supports card-side recovery on compatible devices.

As recorded in ADR-0003, recoverable Vault data lives on CUID Cards. The original machine is not a recovery requirement.

## Required Unlock Material

Unlock requires all of:

- Enough valid NFC Card Payloads for the Vault threshold.
- The user password.

Missing either requirement prevents reconstructing and decrypting the Vault Blob.

## Supported Recovery Cases

- App restart after a completed Vault creation: supported through Room metadata, user password, and threshold cards.
- App recovery on a compatible device without Room metadata: supported through the card recovery entry, user password, and threshold cards.
- Re-scanning any threshold-sized subset of valid cards: supported.
- Duplicate card scans: rejected and not counted toward threshold.
- Wrong Vault cards: rejected through `vault_id` and `scheme_id` checks.
- Moving cards to another compatible Android device: supported if the user knows the password.

## Unsupported Recovery Cases

- Lost user password.
- Fewer than threshold valid cards.
- Interrupted write-card flow after app process death.
- Card replacement.
- Backup import/export.

The unsupported cases are intentional MVP boundaries, not hidden features.
