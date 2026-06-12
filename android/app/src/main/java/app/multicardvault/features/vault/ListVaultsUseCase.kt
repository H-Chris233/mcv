package app.multicardvault.features.vault

import app.multicardvault.data.VaultRepository

data class SavedVaultSummary(
    val vaultIdHex: String,
    val displayName: String,
    val threshold: Int,
    val total: Int,
    val updatedAt: Long,
)

class ListVaultsUseCase(
    private val vaultRepository: VaultRepository,
) {
    suspend operator fun invoke(): List<SavedVaultSummary> = vaultRepository.listVaults().map { vault ->
        SavedVaultSummary(
            vaultIdHex = vault.id,
            displayName = vault.displayName,
            threshold = vault.threshold,
            total = vault.total,
            updatedAt = vault.updatedAt,
        )
    }
}
