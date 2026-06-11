package app.multicardvault.features.create

import app.multicardvault.core.McvCore
import app.multicardvault.core.RustCreateVaultResult
import app.multicardvault.core.RustUnlockVaultResult
import app.multicardvault.core.RustUpdateVaultResult
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.data.VaultRecord
import app.multicardvault.data.VaultRepository
import app.multicardvault.security.DeviceSecretRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateVaultUseCaseTest {
    @Test
    fun createVaultStoresEncryptedVaultRecordAndDeviceSecretReference() = runTest {
        val core = FakeMcvCore()
        val vaultRepository = FakeVaultRepository()
        val deviceSecretRepository = FakeDeviceSecretRepository()
        val useCase = CreateVaultUseCase(
            core = core,
            vaultRepository = vaultRepository,
            deviceSecretRepository = deviceSecretRepository,
        )

        val session = useCase(
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
        assertArrayEquals(deviceSecretRepository.generatedSecret, core.receivedDeviceSecret)
        assertArrayEquals(deviceSecretRepository.generatedSecret, deviceSecretRepository.savedSecret)
        assertTrue(deviceSecretRepository.deletedVaultIds.isEmpty())
    }

    @Test
    fun createVaultDeletesDeviceSecretRefWhenVaultInsertFails() = runTest {
        val core = FakeMcvCore()
        val vaultRepository = FakeVaultRepository(failCreate = true)
        val deviceSecretRepository = FakeDeviceSecretRepository()
        val useCase = CreateVaultUseCase(
            core = core,
            vaultRepository = vaultRepository,
            deviceSecretRepository = deviceSecretRepository,
        )

        val result = runCatching {
            useCase(
                displayName = "Primary",
                password = "passphrase",
                threshold = 3,
                total = 5,
            )
        }

        assertTrue(result.isFailure)
        assertEquals(listOf("01010101010101010101010101010101"), deviceSecretRepository.deletedVaultIds)
    }
}

private class FakeMcvCore : McvCore {
    var receivedDeviceSecret: ByteArray = byteArrayOf()

    override fun projectName(): String = "Multi-Card Vault"

    override fun projectStatus(): String = "experimental and unaudited"

    override fun emptyVaultPlaintext(): ByteArray = byteArrayOf(9)

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext = RustVaultPlaintext(emptyList())

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray = byteArrayOf(9)

    override fun createVault(
        password: String,
        threshold: Int,
        total: Int,
        deviceSecret: ByteArray,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult {
        receivedDeviceSecret = deviceSecret.copyOf()
        return RustCreateVaultResult(
            vaultId = ByteArray(16) { 1 },
            schemeId = ByteArray(16) { 2 },
            vaultBlob = byteArrayOf(3, 4, 5),
            cardPayloads = List(total) { byteArrayOf(it.toByte()) },
        )
    }

    override fun unlockVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult = RustUnlockVaultResult(byteArrayOf(9))

    override fun updateVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult = RustUpdateVaultResult(byteArrayOf(10))
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

    override suspend fun updateVaultBlob(id: String, vaultBlob: ByteArray, updatedAt: Long) {
        val index = records.indexOfFirst { it.id == id }
        check(index >= 0) { "vault not found" }
        records[index] = records[index].copy(vaultBlob = vaultBlob, updatedAt = updatedAt)
    }

    override suspend fun deleteVault(id: String) {
        records.removeAll { it.id == id }
    }
}

private class FakeDeviceSecretRepository : DeviceSecretRepository {
    val generatedSecret = ByteArray(32) { 7 }
    var savedSecret: ByteArray = byteArrayOf()
    val deletedVaultIds = mutableListOf<String>()

    override fun generateDeviceSecret(): ByteArray = generatedSecret.copyOf()

    override suspend fun saveDeviceSecret(vaultId: ByteArray, deviceSecret: ByteArray) {
        savedSecret = deviceSecret.copyOf()
    }

    override suspend fun getDeviceSecret(vaultId: ByteArray): ByteArray? = savedSecret.copyOf()

    override suspend fun deleteDeviceSecret(vaultId: ByteArray) {
        deletedVaultIds += vaultId.toStableTestHex()
    }
}

private fun ByteArray.toStableTestHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte)
}
