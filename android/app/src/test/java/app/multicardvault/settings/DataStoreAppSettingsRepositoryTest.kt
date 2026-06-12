package app.multicardvault.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAppSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun settingsRoundtripThroughPreferencesDataStore() =
        runTest {
            val repository = repository(backgroundScope)

            repository.setOnboardingCompleted(true)
            repository.setDefaultTotal(5)
            repository.setDefaultThreshold(3)
            repository.setNfcExperimentalEnabled(true)
            repository.setDiagnosticsEnabled(true)

            val settings = repository.settings.first()
            assertTrue(settings.onboardingCompleted)
            assertEquals(3, settings.defaultThreshold)
            assertEquals(5, settings.defaultTotal)
            assertTrue(settings.nfcExperimentalEnabled)
            assertTrue(settings.diagnosticsEnabled)
        }

    @Test
    fun thresholdIsClampedToTotal() =
        runTest {
            val repository = repository(backgroundScope)

            repository.setDefaultTotal(2)
            repository.setDefaultThreshold(5)

            val settings = repository.settings.first()
            assertEquals(2, settings.defaultThreshold)
            assertEquals(2, settings.defaultTotal)
            assertFalse(settings.diagnosticsEnabled)
        }

    private fun repository(scope: CoroutineScope): DataStoreAppSettingsRepository {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = {
                    temporaryFolder.newFile("settings.preferences_pb")
                },
            )
        return DataStoreAppSettingsRepository(dataStore)
    }
}
