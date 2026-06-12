package app.multicardvault.features.vault

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

class UpdateVaultUseCaseTest {
    @Test
    fun updateVaultReencryptsPlaintextAndStoresNewBlob() = runTest {
        val core = UpdateFakeMcvCore()
        val vaultRepository = UpdateFakeVaultRepository()
        val deviceSecretRepository = UpdateFakeDeviceSecretRepository()
        val useCase = UpdateVaultUseCase(
            core = core,
            vaultRepository = vaultRepository,
            deviceSecretRepository = deviceSecretRepository,
        )

        val result = useCase(
            vaultIdHex = UpdateVaultIdHex,
            password = "passphrase",
            cardPayloads = listOf(byteArrayOf(1), byteArrayOf(2)),
            entries = listOf(
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
        assertArrayEquals(byteArrayOf(8, 8), vaultRepository.record.vaultBlob)
        assertEquals(99, vaultRepository.record.updatedAt)
        assertEquals(1, core.encodedEntryCount)
        assertArrayEquals(byteArrayOf(1), core.receivedNewPlaintext)
        assertEquals(2, core.receivedCardPayloadCount)
    }

    @Test
    fun updateVaultClearsDeviceSecretWhenPlaintextEncodeFails() = runTest {
        val core = UpdateFakeMcvCore(failEncode = true)
        val vaultRepository = UpdateFakeVaultRepository()
        val deviceSecretRepository = UpdateFakeDeviceSecretRepository()
        val useCase = UpdateVaultUseCase(
            core = core,
            vaultRepository = vaultRepository,
            deviceSecretRepository = deviceSecretRepository,
        )

        val result = runCatching {
            useCase(
                vaultIdHex = UpdateVaultIdHex,
                password = "passphrase",
                cardPayloads = listOf(byteArrayOf(1), byteArrayOf(2)),
                entries = listOf(
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
        assertTrue(deviceSecretRepository.secret.all { it == 0.toByte() })
    }
}

private const val UpdateVaultIdHex = "01010101010101010101010101010101"

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
        deviceSecret: ByteArray,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult = error("not used")

    override fun unlockVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult = error("not used")

    override fun updateVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult {
        receivedNewPlaintext = newPlaintext.copyOf()
        receivedCardPayloadCount = cardPayloads.size
        return RustUpdateVaultResult(newVaultBlob = byteArrayOf(8, 8))
    }
}

private class UpdateFakeVaultRepository : VaultRepository {
    var record = VaultRecord(
        id = UpdateVaultIdHex,
        vaultId = ByteArray(16) { 1 },
        displayName = "Primary",
        threshold = 2,
        total = 3,
        schemeId = ByteArray(16) { 2 },
        vaultBlob = byteArrayOf(7, 7),
        createdAt = 1,
        updatedAt = 1,
    )

    override suspend fun createVault(record: VaultRecord) {
        this.record = record
    }

    override suspend fun getVault(id: String): VaultRecord? = record.takeIf { it.id == id }

    override suspend fun listVaults(): List<VaultRecord> = listOf(record)

    override suspend fun updateVaultBlob(id: String, vaultBlob: ByteArray, updatedAt: Long) {
        check(id == record.id)
        record = record.copy(vaultBlob = vaultBlob.copyOf(), updatedAt = updatedAt)
    }

    override suspend fun deleteVault(id: String) {
        if (id == record.id) {
            record = record.copy(id = "deleted")
        }
    }
}

private class UpdateFakeDeviceSecretRepository : DeviceSecretRepository {
    val secret = ByteArray(32) { 7 }

    override fun generateDeviceSecret(): ByteArray = byteArrayOf(9)

    override suspend fun saveDeviceSecret(vaultId: ByteArray, deviceSecret: ByteArray) = Unit

    override suspend fun getDeviceSecret(vaultId: ByteArray): ByteArray = secret

    override suspend fun deleteDeviceSecret(vaultId: ByteArray) = Unit
}
