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
}
