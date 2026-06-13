package app.multicardvault.features.cards

import app.multicardvault.core.McvCore
import app.multicardvault.core.RustVaultEntry
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.core.hexToByteArray
import app.multicardvault.features.vault.VaultEntry

data class CardSetReissueResult(
    val plaintextSize: Int,
    val cardPayloads: List<ByteArray>,
)

class StartCardSetReissueUseCase(
    private val core: McvCore,
) {
    operator fun invoke(
        vaultIdHex: String,
        password: String,
        cardPayloads: List<ByteArray>,
        entries: List<VaultEntry>,
        updatedAt: Long,
    ): CardSetReissueResult {
        require(vaultIdHex.isNotBlank()) { "vault id is required" }
        require(password.isNotEmpty()) { "password is required" }
        require(cardPayloads.isNotEmpty()) { "card payloads are required" }

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
                                    updatedAt = maxOf(entry.updatedAt, updatedAt),
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
            return CardSetReissueResult(
                plaintextSize = encodedPlaintext.size,
                cardPayloads = updated.cardPayloads,
            )
        } finally {
            plaintext?.fill(0)
        }
    }
}
