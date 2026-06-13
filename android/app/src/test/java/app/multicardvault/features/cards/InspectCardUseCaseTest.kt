package app.multicardvault.features.cards

import app.multicardvault.core.McvCore
import app.multicardvault.core.RustCardPayloadInspection
import app.multicardvault.core.RustCreateVaultResult
import app.multicardvault.core.RustUnlockVaultResult
import app.multicardvault.core.RustUpdateVaultResult
import app.multicardvault.core.RustVaultPlaintext
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectCardUseCaseTest {
    @Test
    fun inspectCardMapsRustMetadataToStableHex() {
        val useCase = InspectCardUseCase(InspectFakeMcvCore())

        val result = useCase(byteArrayOf(1, 2, 3))

        assertEquals("01010101010101010101010101010101", result.vaultIdHex)
        assertEquals("02020202020202020202020202020202", result.schemeIdHex)
        assertEquals(1, result.shareIndex)
        assertEquals(3, result.threshold)
        assertEquals(5, result.total)
        assertEquals(1, result.formatVersion)
    }
}

class InspectFakeMcvCore : McvCore {
    override fun projectName(): String = "Multi-Card Vault"

    override fun projectStatus(): String = "experimental and unaudited"

    override fun emptyVaultPlaintext(): ByteArray = byteArrayOf()

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext = RustVaultPlaintext(emptyList())

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray = byteArrayOf()

    override fun createVault(
        password: String,
        threshold: Int,
        total: Int,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult = error("not used")

    override fun unlockVault(
        password: String,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult = error("not used")

    override fun updateVault(
        password: String,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult = error("not used")

    override fun inspectCardPayload(cardPayload: ByteArray): RustCardPayloadInspection =
        RustCardPayloadInspection(
            vaultId = ByteArray(16) { 1 },
            schemeId = ByteArray(16) { 2 },
            threshold = 3,
            total = 5,
            shareIndex = 1,
            kdfId = 1,
            aeadId = 1,
            formatVersion = 1,
        )
}
