# Use Multiple Rust Crates

The Rust side starts as a workspace with separate crates for core orchestration, cryptography, format handling, Shamir sharing, and UniFFI bindings. This is heavier than a single `mcv-core` crate, but it preserves the security boundary from the handoff document and prevents Android bindings or storage concerns from leaking into protocol and cryptography code.

## Considered Options

- A single `mcv-core` crate with internal modules would reduce early boilerplate but make future crate extraction and ownership boundaries easier to blur.
- A single root crate would be fastest for a prototype but would not match the intended Android + UniFFI integration shape.

## Consequences

Early implementation must keep each crate minimal and avoid speculative abstractions. Crate separation exists to protect boundaries, not to justify extra framework code.
