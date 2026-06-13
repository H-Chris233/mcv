# Manual NFC Test Record

Status: **pending** — CUID Card hardware validation is not complete.

## Test Environment

- Date: 2026-06-12
- Tester: chris233
- App commit: TBD
- APK SHA256: TBD
- Android device 1: Pixel (USB-OTG NFC reader)
- Android device 2: —
- Android version(s): 14
- NFC tags: 5× MIFARE Classic 1K compatible CUID Cards
- Notes: Must be rerun after Card Lifecycle Foundation build is installed on device.

## Required CUID Card Tests

| Case | Expected Result | Status | Notes |
| --- | --- | --- | --- |
| Create a 3-of-5 Vault | 5 Card Payloads are generated and local Room stores metadata only | Pending | |
| Write all 5 CUID Cards | Each card accepts one MIFARE Classic payload | Pending | |
| Unlock with cards 1, 2, 3 | Vault unlocks | Pending | |
| Unlock with cards 1, 3, 5 | Vault unlocks | Pending | |
| Unlock with only 2 valid cards | Unlock fails with not-enough-cards progress | Pending | |
| Repeat the same card 3 times | Duplicate card does not count twice | Pending | |
| Use a card from another Vault | Unlock fails with user-safe error | Pending | |
| Use the wrong password | Unlock fails with user-safe error | Pending | |
| Restart app after creating Vault | Saved metadata appears and can unlock with threshold cards | Pending | |
| Add, edit, delete a text entry after unlock | App requires all-card rewrite and updated cards unlock again | Pending | |
| Scan and label all cards in a Card Set | Card Inventory records all five cards as local non-recovery metadata | Pending | |
| Verify a current card | UI reports current card and Card Inventory marks it `Current` | Pending | |
| Verify an old card after reissue | UI reports old card and Card Inventory marks it `OldScheme` | Pending | |
| Replace one physical card through Card Set Reissue | App generates a complete replacement Card Set and requires rewriting every card, including the replacement card | Pending | |
| Interrupt reissue after one card | Recovery flow keeps scanning and does not unlock until a card group reaches threshold | Pending | |
| Interrupt reissue near the threshold boundary | Recovery flow unlocks only after one old or new Scheme ID group reaches threshold | Pending | |
| Scan mixed old and new cards after interrupted reissue | Recovery flow groups by Scheme ID and converges to one threshold-sized Card Set | Pending | |
| Read an empty card | UI reports empty or invalid card | Pending | |
| Use a card with unrelated MIFARE Classic data | UI reports invalid payload | Pending | |
| Use a capacity-insufficient card/payload | UI reports capacity error | Pending | |
| Disable NFC and try scan flow | UI or platform prompts user to enable NFC | Pending | |
| Interrupt card write | Failed card is not counted as written | Pending | |

## Result

- Overall result: Pending.
- Blocking issues: CUID Card manual test not yet run.
- Follow-up issues: TBD.
