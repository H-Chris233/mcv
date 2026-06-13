package app.multicardvault.features.cards

data class ScannedCardPayload(
    val payload: ByteArray,
    val inspection: CardPayloadInspectionSummary,
)

sealed interface InterruptedReissueRecoveryResult {
    data class NeedsMoreCards(
        val readCount: Int,
        val threshold: Int,
    ) : InterruptedReissueRecoveryResult

    data class ReadyToUnlock(
        val vaultIdHex: String,
        val schemeIdHex: String,
        val threshold: Int,
        val total: Int,
        val cardPayloads: List<ByteArray>,
    ) : InterruptedReissueRecoveryResult
}

class RecoverInterruptedReissueUseCase {
    operator fun invoke(scannedCards: List<ScannedCardPayload>): InterruptedReissueRecoveryResult {
        val groups =
            scannedCards
                .groupBy { it.inspection.vaultIdHex to it.inspection.schemeIdHex }
                .values
                .map { cards ->
                    cards
                        .distinctBy { it.inspection.shareIndex }
                        .sortedBy { it.inspection.shareIndex }
                }

        val ready =
            groups.firstOrNull { cards ->
                val threshold = cards.firstOrNull()?.inspection?.threshold ?: return@firstOrNull false
                cards.size >= threshold
            }
        if (ready != null) {
            val first = ready.first().inspection
            return InterruptedReissueRecoveryResult.ReadyToUnlock(
                vaultIdHex = first.vaultIdHex,
                schemeIdHex = first.schemeIdHex,
                threshold = first.threshold,
                total = first.total,
                cardPayloads = ready.take(first.threshold).map { it.payload.copyOf() },
            )
        }

        val bestGroup = groups.maxByOrNull { it.size }
        return InterruptedReissueRecoveryResult.NeedsMoreCards(
            readCount = bestGroup?.size ?: 0,
            threshold = bestGroup?.firstOrNull()?.inspection?.threshold ?: 1,
        )
    }
}
