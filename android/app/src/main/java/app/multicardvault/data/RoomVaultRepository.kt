package app.multicardvault.data

interface VaultRepository {
    suspend fun createVault(record: VaultRecord)

    suspend fun getVault(id: String): VaultRecord?

    suspend fun listVaults(): List<VaultRecord>

    suspend fun updateVaultBlob(
        id: String,
        vaultBlob: ByteArray,
        updatedAt: Long,
    )

    suspend fun deleteVault(id: String)
}

class RoomVaultRepository(
    private val dao: VaultDao,
) : VaultRepository {
    override suspend fun createVault(record: VaultRecord) {
        dao.insertVault(record.toEntity())
    }

    override suspend fun getVault(id: String): VaultRecord? = dao.getVault(id)?.toRecord()

    override suspend fun listVaults(): List<VaultRecord> = dao.listVaults().map { it.toRecord() }

    override suspend fun updateVaultBlob(
        id: String,
        vaultBlob: ByteArray,
        updatedAt: Long,
    ) {
        check(dao.updateVaultBlob(id, vaultBlob, updatedAt) == 1) { "vault not found" }
    }

    override suspend fun deleteVault(id: String) {
        dao.deleteVault(id)
    }
}
