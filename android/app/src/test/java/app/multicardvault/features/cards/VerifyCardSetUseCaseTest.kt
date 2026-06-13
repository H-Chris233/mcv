package app.multicardvault.features.cards

import app.multicardvault.core.McvCore
import app.multicardvault.core.McvCoreException
import app.multicardvault.core.RustCardPayloadInspection
import app.multicardvault.core.RustCreateVaultResult
import app.multicardvault.core.RustUnlockVaultResult
import app.multicardvault.core.RustUpdateVaultResult
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.data.CardInventoryRecord
import app.multicardvault.data.CardInventoryRepository
import app.multicardvault.data.CardInventoryStatus
import app.multicardvault.uniffi.McvFfiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerifyCardSetUseCaseTest {
    @Test
    fun currentCardIsAcceptedAndPersisted() =
        runTest {
            val repository = VerifyFakeCardInventoryRepository()
            val useCase = useCase(repository = repository)

            val result =
                useCase(
                    target = target(),
                    cardPayload = byteArrayOf(1),
                )

            assertEquals(CardInventoryStatus.Current, result.status)
            assertEquals(1, repository.records.size)
            assertEquals(CardInventoryStatus.Current, repository.records.single().status)
        }

    @Test
    fun oldSchemeCardIsClassifiedAndPersisted() =
        runTest {
            val repository = VerifyFakeCardInventoryRepository()
            val useCase = useCase(core = VerifyFakeMcvCore(schemeIdByte = 3), repository = repository)

            val result =
                useCase(
                    target = target(),
                    cardPayload = byteArrayOf(1),
                )

            assertEquals(CardInventoryStatus.OldScheme, result.status)
            assertEquals(CardInventoryStatus.OldScheme, repository.records.single().status)
        }

    @Test
    fun wrongVaultCardIsClassifiedWithoutPersisting() =
        runTest {
            val repository = VerifyFakeCardInventoryRepository()
            val useCase = useCase(core = VerifyFakeMcvCore(vaultIdByte = 9), repository = repository)

            val result =
                useCase(
                    target = target(),
                    cardPayload = byteArrayOf(1),
                )

            assertEquals(CardInventoryStatus.WrongVault, result.status)
            assertNull(result.inspection?.takeIf { it.vaultIdHex == target().vaultIdHex })
            assertEquals(0, repository.records.size)
        }

    @Test
    fun duplicateShareIndexIsClassifiedWithoutPersisting() =
        runTest {
            val repository = VerifyFakeCardInventoryRepository()
            val useCase = useCase(repository = repository)

            val result =
                useCase(
                    target = target(),
                    cardPayload = byteArrayOf(1),
                    scannedShareIndexes = setOf(1),
                )

            assertEquals(CardInventoryStatus.Duplicate, result.status)
            assertEquals(0, repository.records.size)
        }

    @Test
    fun invalidCardIsUnreadable() =
        runTest {
            val repository = VerifyFakeCardInventoryRepository()
            val useCase =
                useCase(
                    core = VerifyFakeMcvCore(failInspect = true),
                    repository = repository,
                )

            val result =
                useCase(
                    target = target(),
                    cardPayload = byteArrayOf(1),
                )

            assertEquals(CardInventoryStatus.Unreadable, result.status)
            assertNull(result.inspection)
            assertEquals(0, repository.records.size)
        }

    private fun useCase(
        core: McvCore = VerifyFakeMcvCore(),
        repository: VerifyFakeCardInventoryRepository = VerifyFakeCardInventoryRepository(),
    ): VerifyCardSetUseCase =
        VerifyCardSetUseCase(
            inspectCardUseCase = InspectCardUseCase(core),
            cardInventoryRepository = repository,
            clock = { 10 },
        )

    private fun target(): CardVerificationTarget =
        CardVerificationTarget(
            vaultIdHex = "01010101010101010101010101010101",
            currentSchemeIdHex = "02020202020202020202020202020202",
            displayName = "Primary",
        )
}

private class VerifyFakeMcvCore(
    private val vaultIdByte: Byte = 1,
    private val schemeIdByte: Byte = 2,
    private val failInspect: Boolean = false,
) : McvCore {
    override fun projectName(): String = "Multi-Card Vault"

    override fun projectStatus(): String = "experimental and unaudited"

    override fun emptyVaultPlaintext(): ByteArray = byteArrayOf()

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext = RustVaultPlaintext(emptyList())

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray = byteArrayOf()

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
    ): RustUpdateVaultResult = error("not used")

    override fun inspectCardPayload(cardPayload: ByteArray): RustCardPayloadInspection {
        if (failInspect) {
            throw McvCoreException(
                "invalid card",
                McvFfiException.InvalidCardPayload(),
            )
        }
        return RustCardPayloadInspection(
            vaultId = ByteArray(16) { vaultIdByte },
            schemeId = ByteArray(16) { schemeIdByte },
            threshold = 3,
            total = 5,
            shareIndex = 1,
            kdfId = 1,
            aeadId = 1,
            formatVersion = 1,
        )
    }
}

private class VerifyFakeCardInventoryRepository : CardInventoryRepository {
    val records = mutableListOf<CardInventoryRecord>()

    override suspend fun upsert(record: CardInventoryRecord) {
        records.removeAll {
            it.vaultIdHex == record.vaultIdHex &&
                it.schemeIdHex == record.schemeIdHex &&
                it.shareIndex == record.shareIndex
        }
        records += record
    }

    override suspend fun listForVault(vaultIdHex: String): List<CardInventoryRecord> = records.filter { it.vaultIdHex == vaultIdHex }

    override suspend fun listForCardSet(
        vaultIdHex: String,
        schemeIdHex: String,
    ): List<CardInventoryRecord> = records.filter { it.vaultIdHex == vaultIdHex && it.schemeIdHex == schemeIdHex }

    override suspend fun markCurrentCardSet(
        vaultIdHex: String,
        currentSchemeIdHex: String,
    ) = Unit
}
