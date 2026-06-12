package app.multicardvault.core

import app.multicardvault.uniffi.CreateVaultRequest
import app.multicardvault.uniffi.McvFfiException
import app.multicardvault.uniffi.UnlockVaultRequest
import app.multicardvault.uniffi.UpdateVaultRequest
import app.multicardvault.uniffi.VaultPlaintext as FfiVaultPlaintext
import app.multicardvault.uniffi.VaultPlaintextEntry as FfiVaultPlaintextEntry
import app.multicardvault.uniffi.createVault as ffiCreateVault
import app.multicardvault.uniffi.decodeVaultPlaintext as ffiDecodeVaultPlaintext
import app.multicardvault.uniffi.emptyVaultPlaintext as ffiEmptyVaultPlaintext
import app.multicardvault.uniffi.encodeVaultPlaintext as ffiEncodeVaultPlaintext
import app.multicardvault.uniffi.mcvProjectName
import app.multicardvault.uniffi.mcvProjectStatus
import app.multicardvault.uniffi.unlockVault as ffiUnlockVault
import app.multicardvault.uniffi.updateVault as ffiUpdateVault

data class RustCreateVaultResult(
    val vaultId: ByteArray,
    val schemeId: ByteArray,
    val vaultBlob: ByteArray,
    val cardPayloads: List<ByteArray>,
)

data class RustUnlockVaultResult(
    val plaintext: ByteArray,
)

data class RustUpdateVaultResult(
    val newVaultBlob: ByteArray,
)

data class RustVaultPlaintext(
    val entries: List<RustVaultEntry>,
)

data class RustVaultEntry(
    val id: ByteArray,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

interface McvCore {
    fun projectName(): String
    fun projectStatus(): String
    fun emptyVaultPlaintext(): ByteArray
    fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext
    fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray
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

    fun updateVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult
}

class RustMcvCore : McvCore {
    override fun projectName(): String = mcvProjectName()

    override fun projectStatus(): String = mcvProjectStatus()

    override fun emptyVaultPlaintext(): ByteArray = ffiEmptyVaultPlaintext()

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext {
        val plaintext = try {
            ffiDecodeVaultPlaintext(bytes)
        } catch (error: McvFfiException) {
            throw McvCoreException("Rust core rejected vault plaintext decode", error)
        }
        return RustVaultPlaintext(
            entries = plaintext.entries.map { entry ->
                RustVaultEntry(
                    id = entry.id,
                    title = entry.title,
                    content = entry.content,
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt,
                )
            },
        )
    }

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray = try {
        ffiEncodeVaultPlaintext(
            FfiVaultPlaintext(
                entries = plaintext.entries.map { entry ->
                    FfiVaultPlaintextEntry(
                        id = entry.id,
                        title = entry.title,
                        content = entry.content,
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt,
                    )
                },
            ),
        )
    } catch (error: McvFfiException) {
        throw McvCoreException("Rust core rejected vault plaintext encode", error)
    }

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

    override fun updateVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult {
        val response = try {
            ffiUpdateVault(
                UpdateVaultRequest(
                    password = password,
                    deviceSecret = deviceSecret,
                    vaultBlob = vaultBlob,
                    cardPayloads = cardPayloads,
                    newPlaintext = newPlaintext,
                ),
            )
        } catch (error: McvFfiException) {
            throw McvCoreException("Rust core rejected vault update", error)
        }

        return RustUpdateVaultResult(
            newVaultBlob = response.newVaultBlob,
        )
    }
}

class McvCoreException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
