package app.multicardvault.features.unlock

import app.multicardvault.core.McvCore
import app.multicardvault.core.McvCoreException
import app.multicardvault.core.toStableHex
import app.multicardvault.data.VaultRecord
import app.multicardvault.data.VaultRepository
import app.multicardvault.features.vault.VaultEntry
import app.multicardvault.uniffi.McvFfiException

data class UnlockedVaultSummary(
    val vaultIdHex: String,
    val displayName: String,
    val threshold: Int,
    val total: Int,
    val plaintextSize: Int,
    val entries: List<VaultEntry>,
)

class NotEnoughCardsException : RuntimeException("not enough cards")

class UnlockVaultUseCase(
    private val core: McvCore,
    private val vaultRepository: VaultRepository,
) {
    suspend operator fun invoke(
        vaultIdHex: String? = null,
        displayName: String? = null,
        password: String,
        cardPayloads: List<ByteArray>,
    ): UnlockedVaultSummary {
        require(password.isNotEmpty()) { "password is required" }

        val unlocked =
            try {
                core.unlockVault(
                    password = password,
                    cardPayloads = cardPayloads,
                )
            } catch (error: McvCoreException) {
                if (error.cause is McvFfiException.NotEnoughShares) {
                    throw NotEnoughCardsException()
                }
                throw error
            }
        val recoveredVaultIdHex = unlocked.vaultId.toStableHex()
        if (!vaultIdHex.isNullOrEmpty() && vaultIdHex != recoveredVaultIdHex) {
            error("card payloads belong to a different vault")
        }

        val resolvedDisplayName = persistRecoveredMetadata(unlocked, recoveredVaultIdHex, displayName)
        val plaintext = unlocked.plaintext
        try {
            val decoded = core.decodeVaultPlaintext(plaintext)
            return UnlockedVaultSummary(
                vaultIdHex = recoveredVaultIdHex,
                displayName = resolvedDisplayName,
                threshold = unlocked.threshold,
                total = unlocked.total,
                plaintextSize = plaintext.size,
                entries =
                    decoded.entries.map { entry ->
                        VaultEntry(
                            idHex = entry.id.toStableHex(),
                            title = entry.title,
                            content = entry.content,
                            createdAt = entry.createdAt,
                            updatedAt = entry.updatedAt,
                        )
                    },
            )
        } finally {
            plaintext.fill(0)
        }
    }

    private suspend fun persistRecoveredMetadata(
        unlocked: app.multicardvault.core.RustUnlockVaultResult,
        vaultIdHex: String,
        displayName: String?,
    ): String {
        val existing = vaultRepository.getVault(vaultIdHex)
        val now = System.currentTimeMillis()
        if (existing != null) {
            return existing.displayName
        }

        val resolvedDisplayName =
            displayName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Recovered Vault ${vaultIdHex.take(8)}"
        vaultRepository.createVault(
            VaultRecord(
                id = vaultIdHex,
                vaultId = unlocked.vaultId,
                displayName = resolvedDisplayName,
                threshold = unlocked.threshold,
                total = unlocked.total,
                schemeId = unlocked.schemeId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return resolvedDisplayName
    }
}
