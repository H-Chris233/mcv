package app.multicardvault.core

import app.multicardvault.uniffi.CreateVaultRequest
import app.multicardvault.uniffi.McvFfiException
import app.multicardvault.uniffi.UnlockVaultRequest
import app.multicardvault.uniffi.createVault as ffiCreateVault
import app.multicardvault.uniffi.emptyVaultPlaintext as ffiEmptyVaultPlaintext
import app.multicardvault.uniffi.mcvProjectName
import app.multicardvault.uniffi.mcvProjectStatus
import app.multicardvault.uniffi.unlockVault as ffiUnlockVault

data class RustCreateVaultResult(
    val vaultId: ByteArray,
    val schemeId: ByteArray,
    val vaultBlob: ByteArray,
    val cardPayloads: List<ByteArray>,
)

data class RustUnlockVaultResult(
    val plaintext: ByteArray,
)

interface McvCore {
    fun projectName(): String
    fun projectStatus(): String
    fun emptyVaultPlaintext(): ByteArray
    fun createVault(
        password: String,
        threshold: Int,
        total: Int,
        deviceSecret: ByteArray,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult

    fun unlockVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult
}

class RustMcvCore : McvCore {
    override fun projectName(): String = mcvProjectName()

    override fun projectStatus(): String = mcvProjectStatus()

    override fun emptyVaultPlaintext(): ByteArray = ffiEmptyVaultPlaintext()

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun createVault(
        password: String,
        threshold: Int,
        total: Int,
        deviceSecret: ByteArray,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult {
        val response = try {
            ffiCreateVault(
                CreateVaultRequest(
                    password = password,
                    threshold = threshold.toUByte(),
                    total = total.toUByte(),
                    deviceSecret = deviceSecret,
                    initialPlaintext = initialPlaintext,
                ),
            )
        } catch (error: McvFfiException) {
            throw McvCoreException("Rust core rejected vault creation", error)
        }

        return RustCreateVaultResult(
            vaultId = response.vaultId,
            schemeId = response.schemeId,
            vaultBlob = response.vaultBlob,
            cardPayloads = response.cardPayloads,
        )
    }

    override fun unlockVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult {
        val response = try {
            ffiUnlockVault(
                UnlockVaultRequest(
                    password = password,
                    deviceSecret = deviceSecret,
                    vaultBlob = vaultBlob,
                    cardPayloads = cardPayloads,
                ),
            )
        } catch (error: McvFfiException) {
            throw McvCoreException("Rust core rejected vault unlock", error)
        }

        return RustUnlockVaultResult(
            plaintext = response.plaintext,
        )
    }
}

class McvCoreException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
