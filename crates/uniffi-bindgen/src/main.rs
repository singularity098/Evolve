//! UniFFI's bindings generator, at the version the evolve crate uses:
//! `cargo run -p uniffi-bindgen -- generate --library <libevolve.so>
//! --language kotlin --out-dir <dir>`.

fn main() {
    uniffi::uniffi_bindgen_main()
}
