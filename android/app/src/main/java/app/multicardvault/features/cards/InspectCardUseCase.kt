package app.multicardvault.features.cards

import app.multicardvault.core.McvCore
import app.multicardvault.core.toStableHex

class InspectCardUseCase(
    private val core: McvCore,
) {
    operator fun invoke(cardPayload: ByteArray): CardPayloadInspectionSummary {
        val inspection = core.inspectCardPayload(cardPayload)
        return CardPayloadInspectionSummary(
            vaultIdHex = inspection.vaultId.toStableHex(),
            schemeIdHex = inspection.schemeId.toStableHex(),
            threshold = inspection.threshold,
            total = inspection.total,
            shareIndex = inspection.shareIndex,
            formatVersion = inspection.formatVersion,
        )
    }
}
