package app.multicardvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVault(vault: VaultEntity)

    @Query("SELECT * FROM vaults WHERE id = :id")
    suspend fun getVault(id: String): VaultEntity?

    @Query("SELECT * FROM vaults ORDER BY updatedAt DESC")
    suspend fun listVaults(): List<VaultEntity>

    @Query("UPDATE vaults SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchVault(
        id: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM vaults WHERE id = :id")
    suspend fun deleteVault(id: String)
}
