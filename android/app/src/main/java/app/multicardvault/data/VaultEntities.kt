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
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val vaultBlob: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "device_secret_refs")
data class DeviceSecretEntity(
    @PrimaryKey val vaultIdHex: String,
    val keyAlias: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val nonce: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val ciphertext: ByteArray,
    val createdAt: Long,
)

data class VaultRecord(
    val id: String,
    val vaultId: ByteArray,
    val displayName: String,
    val threshold: Int,
    val total: Int,
    val schemeId: ByteArray,
    val vaultBlob: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
)

fun VaultRecord.toEntity(): VaultEntity = VaultEntity(
    id = id,
    vaultId = vaultId,
    displayName = displayName,
    threshold = threshold,
    total = total,
    schemeId = schemeId,
    vaultBlob = vaultBlob,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun VaultEntity.toRecord(): VaultRecord = VaultRecord(
    id = id,
    vaultId = vaultId,
    displayName = displayName,
    threshold = threshold,
    total = total,
    schemeId = schemeId,
    vaultBlob = vaultBlob,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
