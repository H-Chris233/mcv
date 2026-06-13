package app.multicardvault.features.create

import app.multicardvault.core.McvCore
import app.multicardvault.core.toStableHex
import app.multicardvault.data.VaultRecord
import app.multicardvault.data.VaultRepository

data class CreatedVaultSummary(
    val vaultIdHex: String,
    val displayName: String,
    val threshold: Int,
    val total: Int,
    val cardPayloadCount: Int,
)

data class CreatedVaultSession(
    val summary: CreatedVaultSummary,
    val cardPayloads: List<ByteArray>,
)

class CreateVaultUseCase(
    private val core: McvCore,
    private val vaultRepository: VaultRepository,
) {
    suspend operator fun invoke(
        displayName: String,
        password: String,
        threshold: Int = DefaultThreshold,
        total: Int = DefaultTotal,
    ): CreatedVaultSession {
        val normalizedDisplayName = displayName.trim()
        require(normalizedDisplayName.isNotEmpty()) { "vault display name is required" }
        require(password.isNotEmpty()) { "password is required" }
        require(SafeThresholdPreset.isAllowed(threshold, total)) {
            "threshold preset is not supported"
        }

        val plaintext = core.emptyVaultPlaintext()
        val created =
            core.createVault(
                password = password,
                threshold = threshold,
                total = total,
                initialPlaintext = plaintext,
            )
        val vaultIdHex = created.vaultId.toStableHex()
        val now = System.currentTimeMillis()

        vaultRepository.createVault(
            VaultRecord(
                id = vaultIdHex,
                vaultId = created.vaultId,
                displayName = normalizedDisplayName,
                threshold = threshold,
                total = total,
                schemeId = created.schemeId,
                createdAt = now,
                updatedAt = now,
            ),
        )

        return CreatedVaultSession(
            summary =
                CreatedVaultSummary(
                    vaultIdHex = vaultIdHex,
                    displayName = normalizedDisplayName,
                    threshold = threshold,
                    total = total,
                    cardPayloadCount = created.cardPayloads.size,
                ),
            cardPayloads = created.cardPayloads,
        )
    }

    companion object {
        val DefaultThreshold: Int = SafeThresholdPreset.Default.threshold
        val DefaultTotal: Int = SafeThresholdPreset.Default.total
    }
}
