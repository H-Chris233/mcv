#![forbid(unsafe_code)]

use std::error::Error;

use mcv_crypto::{aead_decrypt, aead_encrypt, derive_final_key, derive_password_key, SECRET_LEN};
use mcv_format::{KdfParams, SALT_LEN, XCHACHA20_NONCE_LEN};
use mcv_shamir::{BlahajSecretSharing, SecretSharing};
use rand::SeedableRng;
use rand_chacha::ChaCha20Rng;

fn main() -> Result<(), Box<dyn Error>> {
    let password_key = derive_password_key("correct horse", &[1_u8; SALT_LEN], KdfParams::TEST)?;
    let final_key = derive_final_key(
        &[2_u8; SECRET_LEN],
        password_key.as_slice(),
        &[4_u8; SALT_LEN],
    )?;

    let key = [7_u8; SECRET_LEN];
    let nonce = [8_u8; XCHACHA20_NONCE_LEN];
    let ciphertext = aead_encrypt(&key, &nonce, b"vault plaintext", b"header")?;
    let decrypted = aead_decrypt(&key, &nonce, &ciphertext, b"header")?;

    let mut rng = ChaCha20Rng::from_seed([1_u8; 32]);
    let secret = [42_u8; SECRET_LEN];
    let shares = BlahajSecretSharing::split(&secret, 3, 5, &mut rng)?;
    let recovered = BlahajSecretSharing::recover(3, &shares[1..4])?;

    println!("argon2id_output_hex={}", hex(password_key.as_slice()));
    println!("final_key_hex={}", hex(final_key.as_slice()));
    println!("aead_ciphertext_hex={}", hex(&ciphertext));
    println!("aead_decrypted_hex={}", hex(decrypted.as_slice()));
    println!("shamir_secret_hex={}", hex(&secret));
    for share in shares {
        println!("shamir_share_{}_hex={}", share.index(), hex(share.value()));
    }
    println!("shamir_recovered_hex={}", hex(&recovered));
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
