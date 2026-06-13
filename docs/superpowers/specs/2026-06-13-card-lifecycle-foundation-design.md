# Card Lifecycle Foundation Design

## Goal

Move MCV beyond the v0.1 MVP by making CUID Cards manageable as durable Vault storage media.

The first post-MVP slice is Card Lifecycle Foundation. It keeps the product boundary as a local-first Vault application and focuses on Card reliability, not UI polish or generic password-manager features.

## Confirmed Product Boundary

- MCV remains an Android-only, local-first Vault application.
- Recoverable Vault data continues to live on Card Payloads.
- Unlock and recovery continue to require User Password plus enough Cards.
- Room may store non-recovery metadata only.
- Full Card Payloads must not be persisted in Room or DataStore.
- Cloud accounts, sync, backup export/import, collaboration, iOS, DESFire, JavaCard, and NDEF-only cards remain out of scope for this slice.

## Domain Decisions

The following terms are now part of `CONTEXT.md`:

- Card Set: a Threshold/Total group of Cards for one Vault and Scheme ID.
- Card Inventory: local non-recovery card metadata such as nickname, status, and last check result.
- Card Set Reissue: generating and writing a complete replacement Card Set after recovery conditions are met.
- Interrupted Reissue Recovery: recovering from a partial reissue by scanning Cards and finding any Card Set that reaches Threshold.
- Safe Threshold Preset: an allowed Threshold/Total preset selected to avoid unrecoverable mixed-card states during reissue.

## Recommended Scope

Implement these capabilities:

- Safe Threshold Presets:
  - Replace arbitrary threshold input with allowed presets.
  - Start with `2-of-3` and `3-of-5`.
  - Default to `3-of-5`.
  - Reject or hide unsafe combinations such as `4-of-5`.
- Card Payload Inspection:
  - Add Rust/UniFFI support for inspecting non-sensitive Card Payload header metadata.
  - Return Vault ID, Scheme ID, Threshold, Total, Share Index, format version, and algorithm identifiers.
  - Do not return encrypted share bytes, Data Fragment bytes, plaintext, keys, or password-derived material.
- Card Inventory:
  - Add local Room metadata for scanned Cards.
  - Track Vault ID, Scheme ID, Share Index, display label, status, first seen, last seen, and last check result.
  - Enforce one local inventory record for each Vault ID, Scheme ID, and Share Index.
  - Treat inventory as rebuildable metadata, not recovery material.
- Card Verification:
  - Let users scan Cards to verify they belong to a known Vault and current Card Set.
  - Detect duplicate Share Index, wrong Vault, old Scheme ID, unsupported format, empty card, invalid payload, and unreadable card.
  - Update Card Inventory from successful scans.
- Card Set Reissue:
  - From an unlocked Vault, allow replacing the Card Set.
  - Generate a fresh Scheme ID and complete replacement Card Payload set.
  - Require writing all Total Cards before considering the new Card Set complete.
  - Use this same flow for card replacement, suspected compromise, or after changing Safe Threshold Preset.
- Interrupted Reissue Recovery:
  - Do not persist replacement Card Payloads.
  - After interruption, scan Cards and group valid payloads by Vault ID and Scheme ID.
  - When any group reaches Threshold, unlock with User Password and let the user restart Card Set Reissue.
  - Show old/current/mixed-set status through simple functional UI.

## Non-Goals

- No single-card patch protocol.
- No storage of complete Card Payloads outside the current process memory.
- No password-manager entry schema in this slice.
- No final information architecture or visual redesign in this slice.
- No full security claim or audit claim.

## Architecture

### Rust Core And UniFFI

Rust remains the source of truth for protocol parsing and validation.

Add a Card inspection API:

```text
inspect_card_payload(card_payload: Vec<u8>) -> CardPayloadInspection
```

`CardPayloadInspection` contains only non-sensitive metadata:

```text
vault_id
scheme_id
threshold
total
share_index
kdf_id
aead_id
format_version
```

The inspection API validates Card Payload structure and algorithm IDs but does not decrypt encrypted shares or expose Data Fragments.

### Android Data Layer

Add a Card Inventory table keyed by Vault ID, Scheme ID, and Share Index.

Suggested model:

```text
CardInventoryRecord
- id
- vaultId
- schemeId
- shareIndex
- threshold
- total
- label
- status
- firstSeenAt
- lastSeenAt
- lastCheckMessage
```

The Room schema should add this table through an explicit migration. Do not keep relying on destructive migration for post-MVP user builds.

Card status should remain coarse and user-safe:

```text
Current
OldScheme
Duplicate
WrongVault
Unreadable
Unknown
```

### Android Domain Layer

Add focused use cases instead of expanding `CreateVaultViewModel` further:

- `InspectCardUseCase`
- `ListCardInventoryUseCase`
- `VerifyCardSetUseCase`
- `StartCardSetReissueUseCase`
- `RecoverInterruptedReissueUseCase`

Existing use cases remain responsible for create, unlock, update, and list Vault metadata.

### Android UI State

The UI may stay visually simple, but state should become clearer:

- Saved Vault list.
- Vault unlock/recovery flow.
- Card Inventory screen or section.
- Card verification scan mode.
- Card Set Reissue write mode.
- Interrupted recovery scan mode.

This can still live in one Activity during this slice, but state should be separated enough that a later navigation refactor is straightforward.

## Data Flow

### Settings Migration

1. Read existing `defaultThreshold` and `defaultTotal` settings.
2. If the stored pair is a Safe Threshold Preset, keep it.
3. If it is unsafe or unknown, migrate to the default `3-of-5` preset.
4. Create and reissue flows should only accept Safe Threshold Presets.
5. Existing Vaults remain unlockable even if their old Threshold/Total pair is no longer offered for new Vault creation.

### Verify Card

1. User selects a Vault or global card scan.
2. Android reads Card Payload bytes from MIFARE Classic.
3. Rust inspects non-sensitive Card Payload metadata.
4. Android compares inspection metadata with known Vault metadata and Card Inventory.
5. Android updates Card Inventory with status and last check result.
6. UI displays a user-safe result.

### Reissue Card Set

1. User unlocks a Vault with User Password and enough Cards.
2. User starts Card Set Reissue.
3. Rust generates a fresh Scheme ID and full replacement Card Payload set.
4. Android writes all Total Cards in sequence.
5. Android verifies written cards where practical.
6. On completion, Card Inventory marks the new Scheme ID as current and the old Scheme ID as old.

### Recover Interrupted Reissue

1. User enters interrupted recovery mode.
2. Android scans Cards and inspects each Card Payload.
3. Cards are grouped by Vault ID and Scheme ID.
4. When a group reaches Threshold, Android asks for User Password if needed.
5. Rust unlocks using that group.
6. User restarts Card Set Reissue to converge to one current Card Set.

## Error Handling

Use user-safe messages and keep secrets out of logs.

Required cases:

- Empty card.
- Unsupported card type.
- Malformed Card Payload.
- Unsupported protocol version.
- Wrong Vault ID.
- Old Scheme ID.
- Duplicate Share Index.
- Threshold not reached.
- Wrong User Password.
- Capacity too small.
- NFC read/write I/O failure.
- App interruption during Card Set Reissue.

## Testing

### Rust

- Inspection returns only expected metadata.
- Inspection rejects malformed Card Payloads.
- Inspection rejects unsupported versions and invalid threshold metadata.
- Existing create/unlock/update tests continue to pass.

### Android Unit Tests

- Card Inventory repository stores and updates records.
- Room migration preserves existing Vault metadata while adding Card Inventory.
- Verify-card use case classifies current, old, wrong-vault, duplicate, and invalid cards.
- Safe Threshold Presets reject unsafe combinations.
- Existing unsafe settings migrate to the default safe preset.
- Interrupted recovery grouping picks a Scheme ID only after reaching Threshold.
- Reissue flow does not persist complete Card Payloads.

### Manual NFC Tests

Extend the manual matrix with:

- Scan and label all Cards in a Card Set.
- Verify a current Card.
- Verify an old Card after reissue.
- Replace one Card through Card Set Reissue.
- Interrupt reissue after one card and recover.
- Interrupt reissue near the threshold boundary and recover.
- Scan mixed old/new Cards and converge to one current Card Set.

## Sequencing

Implement in this order:

1. Rust/UniFFI Card Payload inspection.
2. Safe Threshold Presets.
3. Card Inventory Room model and repository.
4. Card verification use case and tests.
5. Card Set Reissue use case built on current update flow.
6. Interrupted Reissue Recovery grouping and tests.
7. Simple functional UI wiring.
8. Documentation and manual NFC test updates.

## Open Risks

- MIFARE Classic capacity remains tight. More metadata must stay in Room unless explicitly needed in Card Payload.
- `CreateVaultViewModel` is already too broad. This slice should add use case boundaries before adding more state.
- Real NFC behavior can differ across Android devices and CUID Card batches. Manual testing remains required.
- Safe presets are a product constraint, not a cryptographic proof. The implementation should still guide users to converge to one current Card Set after interruption.
