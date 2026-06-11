# MVP Does Not Support Cross-Device Recovery

The MVP requires enough valid cards, the user password, and the current device secret to derive the final vault decryption capability. Because the device secret participates in the final key and is not exported, importing a vault blob on another device is not a supported recovery path in the MVP.

## Considered Options

- Supporting cross-device recovery in the MVP would require a separate recovery secret or re-binding protocol, which expands the cryptographic protocol before the local unlock model is proven.
- Leaving the behavior undefined would make user-facing backup and recovery language ambiguous.

## Consequences

MVP documentation and UI must state that encrypted backup import is not the same as cross-device recovery. Cross-device recovery can be designed later as an explicit protocol rather than inferred from card possession alone.
