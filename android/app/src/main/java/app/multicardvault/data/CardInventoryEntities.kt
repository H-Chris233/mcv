package app.multicardvault.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CardInventoryStatus {
    Current,
    OldScheme,
    Duplicate,
    WrongVault,
    Unreadable,
    Unknown,
}

@Entity(
    tableName = "card_inventory",
    indices = [
        Index(
            value = ["vaultIdHex", "schemeIdHex", "shareIndex"],
            unique = true,
        ),
    ],
)
data class CardInventoryEntity(
    @PrimaryKey val id: String,
    val vaultIdHex: String,
    val schemeIdHex: String,
    val shareIndex: Int,
    val threshold: Int,
    val total: Int,
    val label: String,
    val status: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastCheckMessage: String,
)

data class CardInventoryRecord(
    val vaultIdHex: String,
    val schemeIdHex: String,
    val shareIndex: Int,
    val threshold: Int,
    val total: Int,
    val label: String,
    val status: CardInventoryStatus,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val lastCheckMessage: String,
) {
    val id: String = idFor(vaultIdHex, schemeIdHex, shareIndex)

    companion object {
        fun idFor(
            vaultIdHex: String,
            schemeIdHex: String,
            shareIndex: Int,
        ): String = "$vaultIdHex:$schemeIdHex:$shareIndex"
    }
}

fun CardInventoryRecord.toEntity(): CardInventoryEntity =
    CardInventoryEntity(
        id = id,
        vaultIdHex = vaultIdHex,
        schemeIdHex = schemeIdHex,
        shareIndex = shareIndex,
        threshold = threshold,
        total = total,
        label = label,
        status = status.name,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        lastCheckMessage = lastCheckMessage,
    )

fun CardInventoryEntity.toRecord(): CardInventoryRecord =
    CardInventoryRecord(
        vaultIdHex = vaultIdHex,
        schemeIdHex = schemeIdHex,
        shareIndex = shareIndex,
        threshold = threshold,
        total = total,
        label = label,
        status = runCatching { CardInventoryStatus.valueOf(status) }.getOrDefault(CardInventoryStatus.Unknown),
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        lastCheckMessage = lastCheckMessage,
    )
