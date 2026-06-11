#![forbid(unsafe_code)]
#![doc = "UniFFI binding boundary crate for Multi-Card Vault."]

/// Returns the project name through the future binding boundary.
#[must_use]
pub fn mcv_project_name() -> String {
    mcv_core::project_identity().name.to_owned()
}

/// Returns the project status through the future binding boundary.
#[must_use]
pub fn mcv_project_status() -> String {
    mcv_core::project_identity().status.to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn binding_boundary_exposes_project_identity() {
        assert_eq!(mcv_project_name(), "Multi-Card Vault");
        assert_eq!(mcv_project_status(), "experimental and unaudited");
    }
}
