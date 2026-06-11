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
    fun createVaultThroughUniffiReturnsVaultBlobAndCardPayloads() {
        val result = core.createVault(
            password = "correct horse battery staple",
            threshold = 2,
            total = 3,
            deviceSecret = ByteArray(32) { 7 },
            initialPlaintext = core.emptyVaultPlaintext(),
        )

        assertEquals(16, result.vaultId.size)
        assertEquals(16, result.schemeId.size)
        assertEquals(3, result.cardPayloads.size)
        assertTrue(result.vaultBlob.isNotEmpty())
        assertTrue(result.cardPayloads.all { it.isNotEmpty() })
    }

    @Test
    fun unlockVaultThroughUniffiReturnsPlaintext() {
        val password = "correct horse battery staple"
        val deviceSecret = ByteArray(32) { 7 }
        val created = core.createVault(
            password = password,
            threshold = 2,
            total = 3,
            deviceSecret = deviceSecret,
            initialPlaintext = core.emptyVaultPlaintext(),
        )

        val unlocked = core.unlockVault(
            password = password,
            deviceSecret = deviceSecret,
            vaultBlob = created.vaultBlob,
            cardPayloads = created.cardPayloads.take(2),
        )

        assertTrue(unlocked.plaintext.isNotEmpty())
    }

    @Test
    fun vaultPlaintextRoundtripsThroughUniffi() {
        val plaintext = RustVaultPlaintext(
            entries = listOf(
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
    fun updateVaultThroughUniffiReturnsReplacementBlob() {
        val password = "correct horse battery staple"
        val deviceSecret = ByteArray(32) { 7 }
        val created = core.createVault(
            password = password,
            threshold = 2,
            total = 3,
            deviceSecret = deviceSecret,
            initialPlaintext = core.emptyVaultPlaintext(),
        )
        val nextPlaintext = core.encodeVaultPlaintext(
            RustVaultPlaintext(
                entries = listOf(
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

        val updated = core.updateVault(
            password = password,
            deviceSecret = deviceSecret,
            vaultBlob = created.vaultBlob,
            cardPayloads = created.cardPayloads.take(2),
            newPlaintext = nextPlaintext,
        )
        val unlocked = core.unlockVault(
            password = password,
            deviceSecret = deviceSecret,
            vaultBlob = updated.newVaultBlob,
            cardPayloads = created.cardPayloads.take(2),
        )

        val decoded = core.decodeVaultPlaintext(unlocked.plaintext)
        assertEquals("Updated", decoded.entries.single().title)
        assertTrue(updated.newVaultBlob.isNotEmpty())
    }
}
