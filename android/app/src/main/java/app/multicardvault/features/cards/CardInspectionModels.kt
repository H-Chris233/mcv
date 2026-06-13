package app.multicardvault.features.cards

import app.multicardvault.data.CardInventoryStatus

data class CardPayloadInspectionSummary(
    val vaultIdHex: String,
    val schemeIdHex: String,
    val threshold: Int,
    val total: Int,
    val shareIndex: Int,
    val formatVersion: Int,
)

data class CardVerificationTarget(
    val vaultIdHex: String,
    val currentSchemeIdHex: String,
    val displayName: String,
)

data class CardVerificationResult(
    val status: CardInventoryStatus,
    val message: String,
    val inspection: CardPayloadInspectionSummary?,
)
