package app.multicardvault.features.cards

import app.multicardvault.data.CardInventoryRecord
import app.multicardvault.data.CardInventoryRepository
import app.multicardvault.data.CardInventoryStatus

class VerifyCardSetUseCase(
    private val inspectCardUseCase: InspectCardUseCase,
    private val cardInventoryRepository: CardInventoryRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend operator fun invoke(
        target: CardVerificationTarget,
        cardPayload: ByteArray,
        scannedShareIndexes: Set<Int> = emptySet(),
    ): CardVerificationResult {
        val inspection =
            runCatching {
                inspectCardUseCase(cardPayload)
            }.getOrElse {
                return CardVerificationResult(
                    status = CardInventoryStatus.Unreadable,
                    message = "无法读取本应用 Card Payload。",
                    inspection = null,
                )
            }

        if (inspection.vaultIdHex != target.vaultIdHex) {
            return CardVerificationResult(
                status = CardInventoryStatus.WrongVault,
                message = "这张卡不属于 ${target.displayName}。",
                inspection = inspection,
            )
        }

        if (inspection.shareIndex in scannedShareIndexes) {
            return CardVerificationResult(
                status = CardInventoryStatus.Duplicate,
                message = "这张卡已经读取过，请换另一张卡。",
                inspection = inspection,
            )
        }

        val status =
            if (inspection.schemeIdHex == target.currentSchemeIdHex) {
                CardInventoryStatus.Current
            } else {
                CardInventoryStatus.OldScheme
            }
        val message =
            when (status) {
                CardInventoryStatus.Current -> "卡片属于当前卡组。"
                CardInventoryStatus.OldScheme -> "卡片属于旧卡组。"
                else -> "卡片已检查。"
            }
        persistInspection(inspection, status, message)
        return CardVerificationResult(
            status = status,
            message = message,
            inspection = inspection,
        )
    }

    private suspend fun persistInspection(
        inspection: CardPayloadInspectionSummary,
        status: CardInventoryStatus,
        message: String,
    ) {
        val now = clock()
        cardInventoryRepository.upsert(
            CardInventoryRecord(
                vaultIdHex = inspection.vaultIdHex,
                schemeIdHex = inspection.schemeIdHex,
                shareIndex = inspection.shareIndex,
                threshold = inspection.threshold,
                total = inspection.total,
                label = "Card ${inspection.shareIndex}",
                status = status,
                firstSeenAt = now,
                lastSeenAt = now,
                lastCheckMessage = message,
            ),
        )
    }
}
