# Manual NFC Test Record

Status: template for v0.1 MVP hardware validation.

## Test Environment

- Date:
- Tester:
- App commit:
- APK SHA256:
- Android device 1:
- Android device 2:
- Android version(s):
- NFC tags:
- Notes:

## Required NTAG216 Tests

| Case | Expected Result | Status | Notes |
| --- | --- | --- | --- |
| Create a 3-of-5 Vault | Vault Blob is stored locally and 5 Card Payloads are generated | Not run | |
| Write all 5 blank NTAG216 tags | Each tag accepts one NDEF payload | Not run | |
| Unlock with cards 1, 2, 3 | Vault unlocks | Not run | |
| Unlock with cards 1, 3, 5 | Vault unlocks | Not run | |
| Unlock with only 2 valid cards | Unlock fails with not-enough-cards progress | Not run | |
| Repeat the same card 3 times | Duplicate card does not count twice | Not run | |
| Use a card from another Vault | Unlock fails with user-safe error | Not run | |
| Use the wrong password | Unlock fails with user-safe error | Not run | |
| Restart app after creating Vault | Saved Vault appears and can unlock with threshold cards | Not run | |
| Add, edit, delete a text entry after unlock | Vault Blob updates and can be unlocked again | Not run | |
| Read an empty tag | UI reports empty or invalid tag | Not run | |
| Use a tag with unrelated NDEF data | UI reports invalid payload | Not run | |
| Use a capacity-insufficient tag | UI reports capacity error | Not run | |
| Disable NFC and try scan flow | UI or platform prompts user to enable NFC | Not run | |
| Interrupt tag write | Old Vault Blob remains locally available; failed tag is not counted as written | Not run | |

## Result

- Overall result: Not run
- Blocking issues:
- Follow-up issues:
