package app.multicardvault.core

import app.multicardvault.McvAppIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RustMcvCoreTest {
    private val core = RustMcvCore()

    @Test
    fun projectIdentityComesFromRustCore() {
        assertEquals(McvAppIdentity.Name, core.projectName())
        assertEquals("experimental and unaudited", core.projectStatus())
    }

    @Test
    fun createVaultThroughUniffiReturnsCardPayloads() {
        val result =
            core.createVault(
                password = "correct horse battery staple",
                threshold = 2,
                total = 3,
                initialPlaintext = core.emptyVaultPlaintext(),
            )

        assertEquals(16, result.vaultId.size)
        assertEquals(16, result.schemeId.size)
        assertEquals(3, result.cardPayloads.size)
        assertTrue(result.cardPayloads.all { it.isNotEmpty() })
    }

    @Test
    fun unlockVaultThroughUniffiReturnsPlaintextAndRecoveredMetadata() {
        val password = "correct horse battery staple"
        val created =
            core.createVault(
                password = password,
                threshold = 2,
                total = 3,
                initialPlaintext = core.emptyVaultPlaintext(),
            )

        val unlocked =
            core.unlockVault(
                password = password,
                cardPayloads = created.cardPayloads.take(2),
            )

        assertTrue(unlocked.plaintext.isNotEmpty())
        assertEquals(created.vaultId.toList(), unlocked.vaultId.toList())
        assertEquals(created.schemeId.toList(), unlocked.schemeId.toList())
        assertEquals(2, unlocked.threshold)
        assertEquals(3, unlocked.total)
    }

    @Test
    fun vaultPlaintextRoundtripsThroughUniffi() {
        val plaintext =
            RustVaultPlaintext(
                entries =
                    listOf(
                        RustVaultEntry(
                            id = ByteArray(16) { 3 },
                            title = "Entry",
                            content = "Content",
                            createdAt = 1,
                            updatedAt = 2,
                        ),
                    ),
            )

        val encoded = core.encodeVaultPlaintext(plaintext)
        val decoded = core.decodeVaultPlaintext(encoded)

        assertEquals("Entry", decoded.entries.single().title)
        assertEquals("Content", decoded.entries.single().content)
    }

    @Test
    fun updateVaultThroughUniffiReturnsReplacementCards() {
        val password = "correct horse battery staple"
        val created =
            core.createVault(
                password = password,
                threshold = 2,
                total = 3,
                initialPlaintext = core.emptyVaultPlaintext(),
            )
        val nextPlaintext =
            core.encodeVaultPlaintext(
                RustVaultPlaintext(
                    entries =
                        listOf(
                            RustVaultEntry(
                                id = ByteArray(16) { 4 },
                                title = "Updated",
                                content = "Updated content",
                                createdAt = 10,
                                updatedAt = 10,
                            ),
                        ),
                ),
            )

        val updated =
            core.updateVault(
                password = password,
                cardPayloads = created.cardPayloads.take(2),
                newPlaintext = nextPlaintext,
            )
        val unlocked =
            core.unlockVault(
                password = password,
                cardPayloads = updated.cardPayloads.take(2),
            )

        val decoded = core.decodeVaultPlaintext(unlocked.plaintext)
        assertEquals("Updated", decoded.entries.single().title)
        assertEquals(3, updated.cardPayloads.size)
        assertTrue(updated.cardPayloads.all { it.isNotEmpty() })
    }
}
