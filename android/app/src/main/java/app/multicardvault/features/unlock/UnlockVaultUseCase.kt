package app.multicardvault.features.unlock

import app.multicardvault.core.McvCore
import app.multicardvault.data.VaultRepository
import app.multicardvault.security.DeviceSecretRepository

data class UnlockedVaultSummary(
    val vaultIdHex: String,
    val plaintextSize: Int,
)

class UnlockVaultUseCase(
    private val core: McvCore,
    private val vaultRepository: VaultRepository,
    private val deviceSecretRepository: DeviceSecretRepository,
) {
    suspend operator fun invoke(
        vaultIdHex: String,
        password: String,
        cardPayloads: List<ByteArray>,
    ): UnlockedVaultSummary {
        require(password.isNotEmpty()) { "password is required" }

        val vault = vaultRepository.getVault(vaultIdHex) ?: error("vault not found")
        require(cardPayloads.size >= vault.threshold) { "not enough card payloads" }

        val deviceSecret = deviceSecretRepository.getDeviceSecret(vault.vaultId)
            ?: error("device secret not found")
        try {
            val plaintext = core.unlockVault(
                password = password,
                deviceSecret = deviceSecret,
                vaultBlob = vault.vaultBlob,
                cardPayloads = cardPayloads,
            ).plaintext
            return try {
                UnlockedVaultSummary(
                    vaultIdHex = vaultIdHex,
                    plaintextSize = plaintext.size,
                )
            } finally {
                plaintext.fill(0)
            }
        } finally {
            deviceSecret.fill(0)
        }
    }
}
