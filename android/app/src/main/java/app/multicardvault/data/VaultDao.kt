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

    @Query("UPDATE vaults SET vaultBlob = :vaultBlob, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVaultBlob(id: String, vaultBlob: ByteArray, updatedAt: Long): Int

    @Query("DELETE FROM vaults WHERE id = :id")
    suspend fun deleteVault(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeviceSecretRef(ref: DeviceSecretEntity)

    @Query("SELECT * FROM device_secret_refs WHERE vaultIdHex = :vaultIdHex")
    suspend fun getDeviceSecretRef(vaultIdHex: String): DeviceSecretEntity?

    @Query("DELETE FROM device_secret_refs WHERE vaultIdHex = :vaultIdHex")
    suspend fun deleteDeviceSecretRef(vaultIdHex: String)
}
