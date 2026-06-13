package app.multicardvault.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.multicardvault.features.create.CreateVaultUseCase
import app.multicardvault.features.create.SafeThresholdPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val defaultThreshold: Int = CreateVaultUseCase.DefaultThreshold,
    val defaultTotal: Int = CreateVaultUseCase.DefaultTotal,
    val nfcExperimentalEnabled: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
)

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setOnboardingCompleted(value: Boolean)

    suspend fun setDefaultThreshold(value: Int)

    suspend fun setDefaultTotal(value: Int)

    suspend fun setNfcExperimentalEnabled(value: Boolean)

    suspend fun setDiagnosticsEnabled(value: Boolean)
}

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
)

class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {
    constructor(context: Context) : this(context.applicationContext.appSettingsDataStore)

    override val settings: Flow<AppSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(
                        androidx.datastore.preferences.core
                            .emptyPreferences(),
                    )
                } else {
                    throw error
                }
            }.map { preferences ->
                val preset =
                    SafeThresholdPreset.from(
                        threshold =
                            preferences[DefaultThresholdKey]
                                ?: SafeThresholdPreset.Default.threshold,
                        total =
                            preferences[DefaultTotalKey]
                                ?: SafeThresholdPreset.Default.total,
                    )
                AppSettings(
                    onboardingCompleted = preferences[OnboardingCompletedKey] ?: false,
                    defaultThreshold = preset.threshold,
                    defaultTotal = preset.total,
                    nfcExperimentalEnabled = preferences[NfcExperimentalEnabledKey] ?: false,
                    diagnosticsEnabled = preferences[DiagnosticsEnabledKey] ?: false,
                )
            }

    override suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { it[OnboardingCompletedKey] = value }
    }

    override suspend fun setDefaultThreshold(value: Int) {
        dataStore.edit { preferences ->
            val preset =
                SafeThresholdPreset.from(
                    threshold = value,
                    total = preferences[DefaultTotalKey] ?: SafeThresholdPreset.Default.total,
                )
            preferences[DefaultThresholdKey] = preset.threshold
            preferences[DefaultTotalKey] = preset.total
        }
    }

    override suspend fun setDefaultTotal(value: Int) {
        dataStore.edit { preferences ->
            val preset =
                SafeThresholdPreset.from(
                    threshold =
                        preferences[DefaultThresholdKey]
                            ?: SafeThresholdPreset.Default.threshold,
                    total = value,
                )
            preferences[DefaultTotalKey] = preset.total
            preferences[DefaultThresholdKey] = preset.threshold
        }
    }

    override suspend fun setNfcExperimentalEnabled(value: Boolean) {
        dataStore.edit { it[NfcExperimentalEnabledKey] = value }
    }

    override suspend fun setDiagnosticsEnabled(value: Boolean) {
        dataStore.edit { it[DiagnosticsEnabledKey] = value }
    }

    private companion object {
        val OnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
        val DefaultThresholdKey = intPreferencesKey("default_threshold")
        val DefaultTotalKey = intPreferencesKey("default_total")
        val NfcExperimentalEnabledKey = booleanPreferencesKey("nfc_experimental_enabled")
        val DiagnosticsEnabledKey = booleanPreferencesKey("diagnostics_enabled")
    }
}
