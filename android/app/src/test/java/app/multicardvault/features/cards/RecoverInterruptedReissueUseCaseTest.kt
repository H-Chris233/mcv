package app.multicardvault.features.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoverInterruptedReissueUseCaseTest {
    @Test
    fun groupingDoesNotRecoverUntilSchemeReachesThreshold() {
        val useCase = RecoverInterruptedReissueUseCase()

        val result =
            useCase(
                scannedCards =
                    listOf(
                        scannedCard(payload = byteArrayOf(1), shareIndex = 1),
                        scannedCard(payload = byteArrayOf(2), shareIndex = 2),
                    ),
            )

        assertTrue(result is InterruptedReissueRecoveryResult.NeedsMoreCards)
        val needsMore = result as InterruptedReissueRecoveryResult.NeedsMoreCards
        assertEquals(2, needsMore.readCount)
        assertEquals(3, needsMore.threshold)
    }

    @Test
    fun groupingChoosesSchemeThatReachedThreshold() {
        val useCase = RecoverInterruptedReissueUseCase()

        val result =
            useCase(
                scannedCards =
                    listOf(
                        scannedCard(payload = byteArrayOf(1), schemeIdHex = "old", shareIndex = 1),
                        scannedCard(payload = byteArrayOf(2), schemeIdHex = "new", shareIndex = 1),
                        scannedCard(payload = byteArrayOf(3), schemeIdHex = "new", shareIndex = 2),
                        scannedCard(payload = byteArrayOf(4), schemeIdHex = "new", shareIndex = 3),
                    ),
            )

        assertTrue(result is InterruptedReissueRecoveryResult.ReadyToUnlock)
        val ready = result as InterruptedReissueRecoveryResult.ReadyToUnlock
        assertEquals("vault", ready.vaultIdHex)
        assertEquals("new", ready.schemeIdHex)
        assertEquals(listOf(listOf(2.toByte()), listOf(3.toByte()), listOf(4.toByte())), ready.cardPayloads.map { it.toList() })
    }

    @Test
    fun duplicateShareIndexDoesNotIncreaseProgress() {
        val useCase = RecoverInterruptedReissueUseCase()

        val result =
            useCase(
                scannedCards =
                    listOf(
                        scannedCard(payload = byteArrayOf(1), shareIndex = 1),
                        scannedCard(payload = byteArrayOf(2), shareIndex = 1),
                        scannedCard(payload = byteArrayOf(3), shareIndex = 2),
                    ),
            )

        assertTrue(result is InterruptedReissueRecoveryResult.NeedsMoreCards)
        val needsMore = result as InterruptedReissueRecoveryResult.NeedsMoreCards
        assertEquals(2, needsMore.readCount)
        assertEquals(3, needsMore.threshold)
    }

    private fun scannedCard(
        payload: ByteArray,
        vaultIdHex: String = "vault",
        schemeIdHex: String = "scheme",
        shareIndex: Int,
    ): ScannedCardPayload =
        ScannedCardPayload(
            payload = payload,
            inspection =
                CardPayloadInspectionSummary(
                    vaultIdHex = vaultIdHex,
                    schemeIdHex = schemeIdHex,
                    threshold = 3,
                    total = 5,
                    shareIndex = shareIndex,
                    formatVersion = 1,
                ),
        )
}
