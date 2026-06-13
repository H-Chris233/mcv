package app.multicardvault.data

interface CardInventoryRepository {
    suspend fun upsert(record: CardInventoryRecord)

    suspend fun listForVault(vaultIdHex: String): List<CardInventoryRecord>

    suspend fun listForCardSet(
        vaultIdHex: String,
        schemeIdHex: String,
    ): List<CardInventoryRecord>

    suspend fun markCurrentCardSet(
        vaultIdHex: String,
        currentSchemeIdHex: String,
    )
}

class RoomCardInventoryRepository(
    private val dao: CardInventoryDao,
) : CardInventoryRepository {
    override suspend fun upsert(record: CardInventoryRecord) {
        val entity =
            dao.getCard(
                vaultIdHex = record.vaultIdHex,
                schemeIdHex = record.schemeIdHex,
                shareIndex = record.shareIndex,
            )
        val existing = entity?.toRecord()
        val next = merge(existing, record)
        dao.upsertCard(next.toEntity())
    }

    override suspend fun listForVault(vaultIdHex: String): List<CardInventoryRecord> = dao.listForVault(vaultIdHex).map { it.toRecord() }

    override suspend fun listForCardSet(
        vaultIdHex: String,
        schemeIdHex: String,
    ): List<CardInventoryRecord> = dao.listForCardSet(vaultIdHex, schemeIdHex).map { it.toRecord() }

    override suspend fun markCurrentCardSet(
        vaultIdHex: String,
        currentSchemeIdHex: String,
    ) {
        dao.updateStatusForVaultScheme(
            vaultIdHex = vaultIdHex,
            schemeIdHex = currentSchemeIdHex,
            status = CardInventoryStatus.Current.name,
        )
        dao.updateStatusForOtherSchemes(
            vaultIdHex = vaultIdHex,
            currentSchemeIdHex = currentSchemeIdHex,
            status = CardInventoryStatus.OldScheme.name,
        )
    }

    private fun merge(
        existing: CardInventoryRecord?,
        incoming: CardInventoryRecord,
    ): CardInventoryRecord {
        if (existing == null) return incoming
        val newest = if (incoming.lastSeenAt >= existing.lastSeenAt) incoming else existing
        return newest.copy(
            firstSeenAt = minOf(existing.firstSeenAt, incoming.firstSeenAt),
            lastSeenAt = maxOf(existing.lastSeenAt, incoming.lastSeenAt),
        )
    }
}
