package app.multicardvault.features.create

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

class CreateVaultUseCaseTest {
    @Test
    fun createVaultStoresMetadataAndReturnsCardPayloads() =
        runTest {
            val core = FakeMcvCore()
            val vaultRepository = FakeVaultRepository()
            val useCase =
                CreateVaultUseCase(
                    core = core,
                    vaultRepository = vaultRepository,
                )

            val session =
                useCase(
                    displayName = "  Primary  ",
                    password = "passphrase",
                    threshold = 3,
                    total = 5,
                )

            val summary = session.summary
            assertEquals("Primary", summary.displayName)
            assertEquals(3, summary.threshold)
            assertEquals(5, summary.total)
            assertEquals(5, summary.cardPayloadCount)
            assertEquals(5, session.cardPayloads.size)
            assertEquals("01010101010101010101010101010101", summary.vaultIdHex)
            assertEquals(1, vaultRepository.records.size)
            assertEquals("Primary", vaultRepository.records.single().displayName)
        }

    @Test
    fun createVaultReturnsFailureWhenMetadataInsertFails() =
        runTest {
            val core = FakeMcvCore()
            val vaultRepository = FakeVaultRepository(failCreate = true)
            val useCase =
                CreateVaultUseCase(
                    core = core,
                    vaultRepository = vaultRepository,
                )

            val result =
                runCatching {
                    useCase(
                        displayName = "Primary",
                        password = "passphrase",
                        threshold = 3,
                        total = 5,
                    )
                }

            assertTrue(result.isFailure)
        }
}

private class FakeMcvCore : McvCore {
    override fun projectName(): String = "Multi-Card Vault"

    override fun projectStatus(): String = "experimental and unaudited"

    override fun emptyVaultPlaintext(): ByteArray = byteArrayOf(9)

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext = RustVaultPlaintext(emptyList())

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray = byteArrayOf(9)

    override fun createVault(
        password: String,
        threshold: Int,
        total: Int,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult =
        RustCreateVaultResult(
            vaultId = ByteArray(16) { 1 },
            schemeId = ByteArray(16) { 2 },
            cardPayloads = List(total) { byteArrayOf(it.toByte()) },
        )

    override fun unlockVault(
        password: String,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult =
        RustUnlockVaultResult(
            vaultId = ByteArray(16) { 1 },
            schemeId = ByteArray(16) { 2 },
            threshold = 3,
            total = 5,
            plaintext = byteArrayOf(9),
        )

    override fun updateVault(
        password: String,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult = RustUpdateVaultResult(listOf(byteArrayOf(10)))
}

private class FakeVaultRepository(
    private val failCreate: Boolean = false,
) : VaultRepository {
    val records = mutableListOf<VaultRecord>()

    override suspend fun createVault(record: VaultRecord) {
        if (failCreate) error("insert failed")
        records += record
    }

    override suspend fun getVault(id: String): VaultRecord? = records.singleOrNull { it.id == id }

    override suspend fun listVaults(): List<VaultRecord> = records

    override suspend fun touchVault(
        id: String,
        updatedAt: Long,
    ) {
        val index = records.indexOfFirst { it.id == id }
        check(index >= 0) { "vault not found" }
        records[index] = records[index].copy(updatedAt = updatedAt)
    }

    override suspend fun deleteVault(id: String) {
        records.removeAll { it.id == id }
    }
}
