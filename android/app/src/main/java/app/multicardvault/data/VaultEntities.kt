package app.multicardvault.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vaults")
data class VaultEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val vaultId: ByteArray,
    val displayName: String,
    val threshold: Int,
    val total: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val schemeId: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)

data class VaultRecord(
    val id: String,
    val vaultId: ByteArray,
    val displayName: String,
    val threshold: Int,
    val total: Int,
    val schemeId: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)

fun VaultRecord.toEntity(): VaultEntity =
    VaultEntity(
        id = id,
        vaultId = vaultId,
        displayName = displayName,
        threshold = threshold,
        total = total,
        schemeId = schemeId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun VaultEntity.toRecord(): VaultRecord =
    VaultRecord(
        id = id,
        vaultId = vaultId,
        displayName = displayName,
        threshold = threshold,
        total = total,
        schemeId = schemeId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
