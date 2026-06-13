package app.multicardvault.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CardInventoryRepositoryTest {
    @Test
    fun upsertCardInventoryKeepsOneRecordPerVaultSchemeAndShareIndex() =
        runTest {
            val dao = FakeCardInventoryDao()
            val repository = RoomCardInventoryRepository(dao)
            val first = cardRecord(label = "Card 1", lastSeenAt = 1)
            val second = first.copy(label = "Primary Card", lastSeenAt = 2)

            repository.upsert(second)
            repository.upsert(first)

            val records = repository.listForVault(first.vaultIdHex)
            assertEquals(1, records.size)
            assertEquals("Primary Card", records.single().label)
            assertEquals(2, records.single().lastSeenAt)
            assertEquals(1, records.single().firstSeenAt)
        }

    @Test
    fun markCurrentCardSetMarksOtherSchemesOld() =
        runTest {
            val dao = FakeCardInventoryDao()
            val repository = RoomCardInventoryRepository(dao)
            repository.upsert(cardRecord(schemeIdHex = "old", shareIndex = 1))
            repository.upsert(cardRecord(schemeIdHex = "current", shareIndex = 1))

            repository.markCurrentCardSet(
                vaultIdHex = "vault",
                currentSchemeIdHex = "current",
            )

            val records = repository.listForVault("vault")
            assertEquals(CardInventoryStatus.OldScheme, records.single { it.schemeIdHex == "old" }.status)
            assertEquals(CardInventoryStatus.Current, records.single { it.schemeIdHex == "current" }.status)
        }

    private fun cardRecord(
        vaultIdHex: String = "vault",
        schemeIdHex: String = "scheme",
        shareIndex: Int = 1,
        label: String = "Card $shareIndex",
        status: CardInventoryStatus = CardInventoryStatus.Current,
        firstSeenAt: Long = 1,
        lastSeenAt: Long = 1,
    ): CardInventoryRecord =
        CardInventoryRecord(
            vaultIdHex = vaultIdHex,
            schemeIdHex = schemeIdHex,
            shareIndex = shareIndex,
            threshold = 3,
            total = 5,
            label = label,
            status = status,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            lastCheckMessage = "ok",
        )
}

private class FakeCardInventoryDao : CardInventoryDao {
    private val records = mutableMapOf<String, CardInventoryEntity>()

    override suspend fun upsertCard(record: CardInventoryEntity) {
        records[record.id] = record
    }

    override suspend fun getCard(
        vaultIdHex: String,
        schemeIdHex: String,
        shareIndex: Int,
    ): CardInventoryEntity? = records[CardInventoryRecord.idFor(vaultIdHex, schemeIdHex, shareIndex)]

    override suspend fun listForVault(vaultIdHex: String): List<CardInventoryEntity> =
        records.values
            .filter { it.vaultIdHex == vaultIdHex }
            .sortedWith(compareBy<CardInventoryEntity> { it.schemeIdHex }.thenBy { it.shareIndex })

    override suspend fun listForCardSet(
        vaultIdHex: String,
        schemeIdHex: String,
    ): List<CardInventoryEntity> =
        records.values
            .filter { it.vaultIdHex == vaultIdHex && it.schemeIdHex == schemeIdHex }
            .sortedBy { it.shareIndex }

    override suspend fun updateStatusForVaultScheme(
        vaultIdHex: String,
        schemeIdHex: String,
        status: String,
    ): Int {
        var updated = 0
        records.replaceAll { _, entity ->
            if (entity.vaultIdHex == vaultIdHex && entity.schemeIdHex == schemeIdHex) {
                updated += 1
                entity.copy(status = status)
            } else {
                entity
            }
        }
        return updated
    }

    override suspend fun updateStatusForOtherSchemes(
        vaultIdHex: String,
        currentSchemeIdHex: String,
        status: String,
    ): Int {
        var updated = 0
        records.replaceAll { _, entity ->
            if (entity.vaultIdHex == vaultIdHex && entity.schemeIdHex != currentSchemeIdHex) {
                updated += 1
                entity.copy(status = status)
            } else {
                entity
            }
        }
        return updated
    }
}
