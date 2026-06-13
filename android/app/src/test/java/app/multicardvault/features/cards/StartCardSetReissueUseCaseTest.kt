package app.multicardvault.features.cards

import app.multicardvault.core.McvCore
import app.multicardvault.core.RustCardPayloadInspection
import app.multicardvault.core.RustCreateVaultResult
import app.multicardvault.core.RustUnlockVaultResult
import app.multicardvault.core.RustUpdateVaultResult
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.features.vault.VaultEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartCardSetReissueUseCaseTest {
    @Test
    fun reissueReturnsCompleteReplacementCardSet() =
        runTest {
            val core = ReissueFakeMcvCore()
            val useCase = StartCardSetReissueUseCase(core = core)

            val result =
                useCase(
                    vaultIdHex = "01010101010101010101010101010101",
                    password = "passphrase",
                    cardPayloads = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)),
                    entries = emptyList(),
                    updatedAt = 10,
                )

            assertEquals(5, result.cardPayloads.size)
            assertTrue(result.cardPayloads.all { it.isNotEmpty() })
            assertEquals(0, result.plaintextSize)
            assertEquals(3, core.receivedCardPayloadCount)
            assertEquals("passphrase", core.receivedPassword)
        }

    @Test
    fun reissueEncodesCurrentEntries() =
        runTest {
            val core = ReissueFakeMcvCore()
            val useCase = StartCardSetReissueUseCase(core = core)

            val result =
                useCase(
                    vaultIdHex = "01010101010101010101010101010101",
                    password = "passphrase",
                    cardPayloads = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)),
                    entries =
                        listOf(
                            VaultEntry(
                                idHex = "03030303030303030303030303030303",
                                title = "Entry",
                                content = "Content",
                                createdAt = 10,
                                updatedAt = 11,
                            ),
                        ),
                    updatedAt = 12,
                )

            assertEquals(1, result.plaintextSize)
            assertEquals(1, core.encodedEntryCount)
        }
}

private class ReissueFakeMcvCore : McvCore {
    var encodedEntryCount = 0
    var receivedCardPayloadCount = 0
    var receivedPassword: String = ""

    override fun projectName(): String = "Multi-Card Vault"

    override fun projectStatus(): String = "experimental and unaudited"

    override fun emptyVaultPlaintext(): ByteArray = byteArrayOf()

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext = RustVaultPlaintext(emptyList())

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray {
        encodedEntryCount = plaintext.entries.size
        return ByteArray(plaintext.entries.size)
    }

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
    ): RustUpdateVaultResult {
        receivedPassword = password
        receivedCardPayloadCount = cardPayloads.size
        return RustUpdateVaultResult(
            cardPayloads = List(5) { index -> byteArrayOf((50 + index).toByte()) },
        )
    }

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
