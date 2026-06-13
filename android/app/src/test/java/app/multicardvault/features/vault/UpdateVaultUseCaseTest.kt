package app.multicardvault.features.vault

import app.multicardvault.core.McvCore
import app.multicardvault.core.RustCreateVaultResult
import app.multicardvault.core.RustUnlockVaultResult
import app.multicardvault.core.RustUpdateVaultResult
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.data.VaultRecord
import app.multicardvault.data.VaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVaultUseCaseTest {
    @Test
    fun updateVaultReturnsReplacementCardPayloadsAndTouchesMetadata() =
        runTest {
            val core = UpdateFakeMcvCore()
            val vaultRepository = UpdateFakeVaultRepository()
            val useCase =
                UpdateVaultUseCase(
                    core = core,
                    vaultRepository = vaultRepository,
                )

            val result =
                useCase(
                    vaultIdHex = UPDATE_VAULT_ID_HEX,
                    password = "passphrase",
                    cardPayloads = listOf(byteArrayOf(1), byteArrayOf(2)),
                    entries =
                        listOf(
                            VaultEntry(
                                idHex = "03030303030303030303030303030303",
                                title = "Entry",
                                content = "Content",
                                createdAt = 10,
                                updatedAt = 11,
                            ),
                        ),
                    updatedAt = 99,
                )

            assertEquals(1, result.plaintextSize)
            assertEquals(listOf(byteArrayOf(8), byteArrayOf(9)).map { it.toList() }, result.cardPayloads.map { it.toList() })
            assertEquals(99, vaultRepository.record.updatedAt)
            assertEquals(1, core.encodedEntryCount)
            assertEquals(byteArrayOf(1).toList(), core.receivedNewPlaintext.toList())
            assertEquals(2, core.receivedCardPayloadCount)
        }

    @Test
    fun updateVaultReturnsFailureWhenPlaintextEncodeFails() =
        runTest {
            val core = UpdateFakeMcvCore(failEncode = true)
            val vaultRepository = UpdateFakeVaultRepository()
            val useCase =
                UpdateVaultUseCase(
                    core = core,
                    vaultRepository = vaultRepository,
                )

            val result =
                runCatching {
                    useCase(
                        vaultIdHex = UPDATE_VAULT_ID_HEX,
                        password = "passphrase",
                        cardPayloads = listOf(byteArrayOf(1), byteArrayOf(2)),
                        entries =
                            listOf(
                                VaultEntry(
                                    idHex = "03030303030303030303030303030303",
                                    title = "Entry",
                                    content = "Content",
                                    createdAt = 10,
                                    updatedAt = 11,
                                ),
                            ),
                    )
                }

            assertTrue(result.isFailure)
        }
}

private const val UPDATE_VAULT_ID_HEX = "01010101010101010101010101010101"

private class UpdateFakeMcvCore(
    private val failEncode: Boolean = false,
) : McvCore {
    var encodedEntryCount = 0
    var receivedNewPlaintext: ByteArray = byteArrayOf()
    var receivedCardPayloadCount = 0

    override fun projectName(): String = "Multi-Card Vault"

    override fun projectStatus(): String = "experimental and unaudited"

    override fun emptyVaultPlaintext(): ByteArray = byteArrayOf()

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext = RustVaultPlaintext(emptyList())

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray {
        if (failEncode) error("encode failed")
        encodedEntryCount = plaintext.entries.size
        return byteArrayOf(plaintext.entries.size.toByte())
    }

    override fun createVault(
        password: String,
        threshold: Int,
        total: Int,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult = error("not used")

    override fun unlockVault(
        password: String,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult = error("not used")

    override fun updateVault(
        password: String,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult {
        receivedNewPlaintext = newPlaintext.copyOf()
        receivedCardPayloadCount = cardPayloads.size
        return RustUpdateVaultResult(cardPayloads = listOf(byteArrayOf(8), byteArrayOf(9)))
    }
}

private class UpdateFakeVaultRepository : VaultRepository {
    var record =
        VaultRecord(
            id = UPDATE_VAULT_ID_HEX,
            vaultId = ByteArray(16) { 1 },
            displayName = "Primary",
            threshold = 2,
            total = 3,
            schemeId = ByteArray(16) { 2 },
            createdAt = 1,
            updatedAt = 1,
        )

    override suspend fun createVault(record: VaultRecord) {
        this.record = record
    }

    override suspend fun getVault(id: String): VaultRecord? = record.takeIf { it.id == id }

    override suspend fun listVaults(): List<VaultRecord> = listOf(record)

    override suspend fun touchVault(
        id: String,
        updatedAt: Long,
    ) {
        check(id == record.id)
        record = record.copy(updatedAt = updatedAt)
    }

    override suspend fun deleteVault(id: String) {
        if (id == record.id) {
            record = record.copy(id = "deleted")
        }
    }
}
