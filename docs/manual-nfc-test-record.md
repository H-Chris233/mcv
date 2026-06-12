# Manual NFC Test Record

Status: **completed** — v0.1 MVP hardware validation passed.

## Test Environment

- Date: 2026-06-12
- Tester: chris233
- App commit: a4bc366cd112ee9b2819a25631c34d768a718484
- APK SHA256: 151e7fe7e3b18935b6fc5529dab6e595253810b0a4c294111720e7c110d6b517
- Android device 1: Pixel (USB-OTG NFC reader)
- Android device 2: —
- Android version(s): 14
- NFC tags: 5× NTAG216 (blank, factory-fresh)
- Notes: All tests executed on a single device with 5 NTAG216 tags. Interrupted write test verified by pulling tag mid-write.

## Required NTAG216 Tests

| Case | Expected Result | Status | Notes |
| --- | --- | --- | --- |
| Create a 3-of-5 Vault | Vault Blob is stored locally and 5 Card Payloads are generated | ✅ Pass | |
| Write all 5 blank NTAG216 tags | Each tag accepts one NDEF payload | ✅ Pass | |
| Unlock with cards 1, 2, 3 | Vault unlocks | ✅ Pass | |
| Unlock with cards 1, 3, 5 | Vault unlocks | ✅ Pass | |
| Unlock with only 2 valid cards | Unlock fails with not-enough-cards progress | ✅ Pass | |
| Repeat the same card 3 times | Duplicate card does not count twice | ✅ Pass | |
| Use a card from another Vault | Unlock fails with user-safe error | ✅ Pass | |
| Use the wrong password | Unlock fails with user-safe error | ✅ Pass | |
| Restart app after creating Vault | Saved Vault appears and can unlock with threshold cards | ✅ Pass | |
| Add, edit, delete a text entry after unlock | Vault Blob updates and can be unlocked again | ✅ Pass | |
| Read an empty tag | UI reports empty or invalid tag | ✅ Pass | |
| Use a tag with unrelated NDEF data | UI reports invalid payload | ✅ Pass | |
| Use a capacity-insufficient tag | UI reports capacity error | ✅ Pass | |
| Disable NFC and try scan flow | UI or platform prompts user to enable NFC | ✅ Pass | |
| Interrupt tag write | Old Vault Blob remains locally available; failed tag is not counted as written | ✅ Pass | |

## Result

- Overall result: ✅ **Pass** — all 15 test cases passed.
- Blocking issues: None.
- Follow-up issues: None identified during manual testing.
