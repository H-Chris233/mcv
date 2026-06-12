package app.multicardvault.nfc

sealed interface NfcCardResult {
    data class Success(
        val payload: ByteArray,
    ) : NfcCardResult

    data class UnsupportedTag(
        val reason: String,
    ) : NfcCardResult

    data object EmptyTag : NfcCardResult

    data class InvalidPayload(
        val reason: String,
    ) : NfcCardResult

    data class CapacityTooSmall(
        val requiredBytes: Int,
        val maxBytes: Int,
    ) : NfcCardResult

    data class IoError(
        val reason: String,
    ) : NfcCardResult
}
