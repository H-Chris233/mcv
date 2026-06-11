#![forbid(unsafe_code)]

use std::env;
use std::error::Error;

use camino::Utf8PathBuf;

fn main() -> Result<(), Box<dyn Error>> {
    let mut args = env::args().skip(1);
    let library_path = args
        .next()
        .map(Utf8PathBuf::from)
        .unwrap_or_else(|| Utf8PathBuf::from("target/debug/libmcv_uniffi.so"));
    let out_dir = args
        .next()
        .map(Utf8PathBuf::from)
        .unwrap_or_else(|| Utf8PathBuf::from("bindings/kotlin"));

    let metadata = cargo_metadata::MetadataCommand::new()
        .current_dir(".")
        .exec()?;
    let config_supplier = uniffi::CargoMetadataConfigSupplier::from(metadata);

    uniffi::generate_bindings_library_mode(
        &library_path,
        Some("mcv_uniffi".to_owned()),
        &uniffi::KotlinBindingGenerator,
        &config_supplier,
        None,
        &out_dir,
        true,
    )?;

    Ok(())
}
