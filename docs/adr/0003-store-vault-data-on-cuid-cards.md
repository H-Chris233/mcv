# Store Vault Data on CUID Cards

Status: accepted

MCV will move the recoverable Vault data out of local Room storage and onto a set of MIFARE Classic 1K compatible CUID Cards. A Vault must be recoverable from only the User Password and any Threshold-sized set of Cards, so the protocol must remove Device Secret from Final Key derivation, store both encrypted Share material and Vault Data Fragment material in Card Payloads, and treat local machine state as non-recovery metadata only.

## Considered Options

- Keeping Vault Blob in local Room storage is simpler and matches the v0.1 implementation, but it violates the product requirement that the data exists on cards and can be restored without the original machine.
- Using high-capacity NDEF tags would preserve the current NDEF repository shape, but it does not match the available hardware.
- Using CUID Card block storage matches the available cards, but forces a smaller Vault capacity budget and a dedicated MIFARE Classic read/write path.

## Consequences

- Android NFC support must add a MIFARE Classic 1K path instead of relying only on `Ndef` and `NdefFormatable`.
- The app must not rely on CUID UID-rewrite behavior; CUID Cards are used only as app-owned storage media.
- The first implementation will write encrypted Card Payload data into default-key-authenticated data blocks and avoid changing sector keys to reduce lock-card risk.
- Updating Vault Plaintext must produce a fresh card set and rewrite all Total Cards, so any Threshold-sized subset can recover the latest Vault.
- Vault capacity must be explicitly bounded by the usable payload across Threshold MIFARE Classic 1K Cards after card headers, authentication metadata, encrypted Shares, Data Fragments, and integrity overhead.
