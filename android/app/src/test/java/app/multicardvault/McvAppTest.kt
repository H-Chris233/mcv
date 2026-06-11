package app.multicardvault

import org.junit.Assert.assertEquals
import org.junit.Test

class McvAppTest {
    @Test
    fun appIdentityStatesExperimentalStatus() {
        assertEquals("Multi-Card Vault", McvAppIdentity.Name)
        assertEquals("Experimental and unaudited", McvAppIdentity.Status)
    }
}
