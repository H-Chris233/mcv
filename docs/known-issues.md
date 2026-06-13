# Known Issues

Status: v0.1 MVP known issues.

- Real MIFARE Classic 1K compatible CUID Card end-to-end testing is still required before publishing a user-facing APK.
- Interrupted write-card flow recovery is not implemented. If the app process dies while writing cards, create a fresh Vault and card set.
- Card replacement is not implemented.
- The Android UI is a single-screen MVP prototype, not the final multi-screen navigation model.
- Vault entries are simple text records only. This is not a full password-manager schema.
- DESFire, JavaCard, and NDEF-only tags are not implemented in the main card storage path.
- Security status remains experimental and unaudited.
