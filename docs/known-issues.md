# Known Issues

Status: v0.1 MVP known issues.

- Real NTAG216 end-to-end testing is still required before publishing a user-facing APK.
- Interrupted write-card flow recovery is not implemented. If the app process dies while writing cards, create a fresh Vault and card set.
- Cross-device recovery is intentionally not supported in MVP because Device Secret participates in final key derivation.
- The Android UI is a single-screen MVP prototype, not the final multi-screen navigation model.
- Vault entries are simple text records only. This is not a full password-manager schema.
- StrongBox and biometric-gated Device Secret access are not implemented in MVP.
- Experimental NFC card types such as MIFARE Classic, CUID, DESFire, and JavaCard are not implemented.
- Security status remains experimental and unaudited.
