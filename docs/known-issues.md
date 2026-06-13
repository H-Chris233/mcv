# Known Issues

Status: Card Lifecycle Foundation known issues.

- Real MIFARE Classic 1K compatible CUID Card end-to-end testing is still required before publishing a user-facing APK.
- Interrupted initial Vault creation before a usable threshold has been written still requires creating a fresh Vault and Card Set.
- Card replacement is whole Card Set Reissue only. Single-card patching is not implemented.
- Interrupted Card Set Reissue recovery requires rescanning physical cards; the app intentionally does not resume from cached full Card Payloads.
- The Android UI is a single-screen MVP prototype, not the final multi-screen navigation model.
- Vault entries are simple text records only. This is not a full password-manager schema.
- DESFire, JavaCard, and NDEF-only tags are not implemented in the main card storage path.
- Security status remains experimental and unaudited.
