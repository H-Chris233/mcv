# Recovery Model

The app supports card-side recovery on compatible devices.

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
- Card Inventory rebuild: supported by scanning cards and persisting only local non-recovery metadata.
- Card Set Reissue: supported as a whole Card Set replacement. The app generates replacement Card Payloads and requires rewriting every card.
- Interrupted Card Set Reissue recovery: supported by scanning old and new physical cards until any Vault ID plus Scheme ID group reaches its threshold.

## Unsupported Recovery Cases

- Lost user password.
- Fewer than threshold valid cards.
- Interrupted initial Vault creation before enough cards have been written.
- Resuming an interrupted reissue from cached full Card Payloads on the machine.
- Single-card patch or single-card replacement without rewriting a complete Card Set.
- Backup import/export.

The unsupported cases are intentional product boundaries, not hidden features.
