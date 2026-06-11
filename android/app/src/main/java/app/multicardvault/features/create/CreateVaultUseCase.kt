package app.multicardvault.features.create

import app.multicardvault.core.McvCore
import app.multicardvault.core.toStableHex
import app.multicardvault.data.VaultRecord
import app.multicardvault.data.VaultRepository
import app.multicardvault.security.DeviceSecretRepository

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
    private val deviceSecretRepository: DeviceSecretRepository,
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
        require(total in 1..MaxTotal) { "total must be between 1 and 255" }
        require(threshold in 1..total) { "threshold must be between 1 and total" }

        val deviceSecret = deviceSecretRepository.generateDeviceSecret()
        try {
            val plaintext = core.emptyVaultPlaintext()
            val created = core.createVault(
                password = password,
                threshold = threshold,
                total = total,
                deviceSecret = deviceSecret,
                initialPlaintext = plaintext,
            )
            val vaultIdHex = created.vaultId.toStableHex()
            val now = System.currentTimeMillis()

            try {
                deviceSecretRepository.saveDeviceSecret(created.vaultId, deviceSecret)
                vaultRepository.createVault(
                    VaultRecord(
                        id = vaultIdHex,
                        vaultId = created.vaultId,
                        displayName = normalizedDisplayName,
                        threshold = threshold,
                        total = total,
                        schemeId = created.schemeId,
                        vaultBlob = created.vaultBlob,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } catch (error: Throwable) {
                deviceSecretRepository.deleteDeviceSecret(created.vaultId)
                throw error
            }

            return CreatedVaultSession(
                summary = CreatedVaultSummary(
                    vaultIdHex = vaultIdHex,
                    displayName = normalizedDisplayName,
                    threshold = threshold,
                    total = total,
                    cardPayloadCount = created.cardPayloads.size,
                ),
                cardPayloads = created.cardPayloads,
            )
        } finally {
            deviceSecret.fill(0)
        }
    }

    companion object {
        const val DefaultThreshold = 3
        const val DefaultTotal = 5
        private const val MaxTotal = 255
    }
}
