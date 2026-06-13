package app.multicardvault.features.vault

import app.multicardvault.core.McvCore
import app.multicardvault.core.RustVaultEntry
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.core.hexToByteArray
import app.multicardvault.data.VaultRepository

data class UpdatedVaultSummary(
    val plaintextSize: Int,
    val cardPayloads: List<ByteArray>,
)

class UpdateVaultUseCase(
    private val core: McvCore,
    private val vaultRepository: VaultRepository,
) {
    suspend operator fun invoke(
        vaultIdHex: String,
        password: String,
        cardPayloads: List<ByteArray>,
        entries: List<VaultEntry>,
        updatedAt: Long = System.currentTimeMillis(),
    ): UpdatedVaultSummary {
        require(password.isNotEmpty()) { "password is required" }

        val vault = vaultRepository.getVault(vaultIdHex) ?: error("vault not found")
        require(cardPayloads.size >= vault.threshold) { "not enough card payloads" }

        var plaintext: ByteArray? = null
        try {
            val encodedPlaintext =
                core.encodeVaultPlaintext(
                    RustVaultPlaintext(
                        entries =
                            entries.map { entry ->
                                RustVaultEntry(
                                    id = entry.idHex.hexToByteArray(),
                                    title = entry.title,
                                    content = entry.content,
                                    createdAt = entry.createdAt,
                                    updatedAt = entry.updatedAt,
                                )
                            },
                    ),
                )
            plaintext = encodedPlaintext
            val updated =
                core.updateVault(
                    password = password,
                    cardPayloads = cardPayloads,
                    newPlaintext = encodedPlaintext,
                )
            vaultRepository.touchVault(vaultIdHex, updatedAt)
            return UpdatedVaultSummary(
                plaintextSize = encodedPlaintext.size,
                cardPayloads = updated.cardPayloads,
            )
        } finally {
            plaintext?.fill(0)
        }
    }
}
