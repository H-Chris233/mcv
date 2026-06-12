#![forbid(unsafe_code)]

use std::error::Error;

use mcv_core::{create_vault_with_rng, CreateVaultRequest};
use mcv_format::{KdfParams, VaultEntryV1, VaultPlaintextV1, ID_LEN};
use rand::SeedableRng;
use rand_chacha::ChaCha20Rng;

const SECRET_LEN: usize = 32;

fn main() -> Result<(), Box<dyn Error>> {
    let plaintext = VaultPlaintextV1 {
        entries: vec![VaultEntryV1 {
            id: vec![0x11; ID_LEN],
            title: "vector-entry".to_owned(),
            content: "vector-content".to_owned(),
            created_at: 1_700_000_000,
            updated_at: 1_700_000_000,
        }],
    }
    .encode()?;
    let request = CreateVaultRequest {
        password: "mcv test password".to_owned(),
        threshold: 3,
        total: 5,
        device_secret: vec![0x22; SECRET_LEN],
        initial_plaintext: plaintext.clone(),
    };
    let mut rng = ChaCha20Rng::from_seed([0x42; 32]);
    let response = create_vault_with_rng(request, KdfParams::TEST, &mut rng)?;

    println!("{{");
    println!("  \"name\": \"m1-deterministic-3-of-5\",");
    println!(
        "  \"kdf_params\": {{ \"memory_cost_kib\": 64, \"time_cost\": 1, \"parallelism\": 1 }},"
    );
    println!("  \"unlock_phrase\": \"mcv test password\",");
    println!("  \"device_secret_hex\": \"{}\",", hex(&[0x22; SECRET_LEN]));
    println!("  \"plaintext_hex\": \"{}\",", hex(&plaintext));
    println!("  \"vault_id_hex\": \"{}\",", hex(&response.vault_id));
    println!("  \"scheme_id_hex\": \"{}\",", hex(&response.scheme_id));
    println!("  \"vault_blob_hex\": \"{}\",", hex(&response.vault_blob));
    println!("  \"card_payloads_hex\": [");
    for (index, payload) in response.card_payloads.iter().enumerate() {
        let suffix = if index + 1 == response.card_payloads.len() {
            ""
        } else {
            ","
        };
        println!("    \"{}\"{}", hex(payload), suffix);
    }
    println!("  ]");
    println!("}}");

    Ok(())
}

fn hex(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        output.push(char::from(HEX[usize::from(byte >> 4)]));
        output.push(char::from(HEX[usize::from(byte & 0x0f)]));
    }
    output
}
