package app.multicardvault.features.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeThresholdPresetTest {
    @Test
    fun supportedPresetsAreTwoOfThreeAndThreeOfFive() {
        assertTrue(SafeThresholdPreset.isAllowed(2, 3))
        assertTrue(SafeThresholdPreset.isAllowed(3, 5))
        assertFalse(SafeThresholdPreset.isAllowed(4, 5))
        assertFalse(SafeThresholdPreset.isAllowed(1, 1))
    }

    @Test
    fun unsafePairFallsBackToDefault() {
        assertEquals(SafeThresholdPreset.ThreeOfFive, SafeThresholdPreset.from(4, 5))
    }
}
