package app.multicardvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CardInventoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(record: CardInventoryEntity)

    @Query(
        """
        SELECT * FROM card_inventory
        WHERE vaultIdHex = :vaultIdHex AND schemeIdHex = :schemeIdHex AND shareIndex = :shareIndex
        """,
    )
    suspend fun getCard(
        vaultIdHex: String,
        schemeIdHex: String,
        shareIndex: Int,
    ): CardInventoryEntity?

    @Query("SELECT * FROM card_inventory WHERE vaultIdHex = :vaultIdHex ORDER BY schemeIdHex ASC, shareIndex ASC")
    suspend fun listForVault(vaultIdHex: String): List<CardInventoryEntity>

    @Query(
        """
        SELECT * FROM card_inventory
        WHERE vaultIdHex = :vaultIdHex AND schemeIdHex = :schemeIdHex
        ORDER BY shareIndex ASC
        """,
    )
    suspend fun listForCardSet(
        vaultIdHex: String,
        schemeIdHex: String,
    ): List<CardInventoryEntity>

    @Query(
        """
        UPDATE card_inventory SET status = :status
        WHERE vaultIdHex = :vaultIdHex AND schemeIdHex = :schemeIdHex
        """,
    )
    suspend fun updateStatusForVaultScheme(
        vaultIdHex: String,
        schemeIdHex: String,
        status: String,
    ): Int

    @Query(
        """
        UPDATE card_inventory SET status = :status
        WHERE vaultIdHex = :vaultIdHex AND schemeIdHex != :currentSchemeIdHex
        """,
    )
    suspend fun updateStatusForOtherSchemes(
        vaultIdHex: String,
        currentSchemeIdHex: String,
        status: String,
    ): Int
}
