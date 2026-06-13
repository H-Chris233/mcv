package app.multicardvault.features.create

import app.multicardvault.MainDispatcherRule
import app.multicardvault.core.McvCore
import app.multicardvault.core.McvCoreException
import app.multicardvault.core.RustCreateVaultResult
import app.multicardvault.core.RustUnlockVaultResult
import app.multicardvault.core.RustUpdateVaultResult
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.data.VaultRecord
import app.multicardvault.data.VaultRepository
import app.multicardvault.features.unlock.UnlockVaultUseCase
import app.multicardvault.features.vault.ListVaultsUseCase
import app.multicardvault.features.vault.UpdateVaultUseCase
import app.multicardvault.nfc.NfcCardResult
import app.multicardvault.settings.AppSettings
import app.multicardvault.settings.AppSettingsRepository
import app.multicardvault.uniffi.McvFfiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateVaultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun writeCardProgressAdvancesOnNfcSuccess() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = testViewModel()
            viewModel.updatePassword("passphrase")
            viewModel.createVault()
            advanceUntilIdle()

            val command = viewModel.nextNfcCommand()
            assertTrue(command is NfcCommand.Write)
            viewModel.onNfcResult(NfcCardResult.Success((command as NfcCommand.Write).payload))

            val state = viewModel.uiState.value as CreateVaultUiState.WritingCards
            assertEquals(1, state.writtenCount)
            assertEquals(2, state.nextCardNumber)
        }

    @Test
    fun duplicateExactPayloadDoesNotCountTwiceDuringUnlock() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = testViewModel()
            viewModel.updatePassword("passphrase")
            viewModel.createVault()
            advanceUntilIdle()
            writeAllCards(viewModel)

            val payload = byteArrayOf(10)
            viewModel.onNfcResult(NfcCardResult.Success(payload))
            viewModel.onNfcResult(NfcCardResult.Success(payload))

            val state = viewModel.uiState.value as CreateVaultUiState.ReadingCards
            assertEquals(1, state.readCount)
            assertEquals("这张卡已经读取过，请换另一张卡。", state.message)
        }

    @Test
    fun unlockTriggersWhenThresholdIsReached() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = testViewModel()
            viewModel.updatePassword("passphrase")
            viewModel.createVault()
            advanceUntilIdle()
            writeAllCards(viewModel)

            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(10)))
            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(11)))
            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(12)))
            advanceUntilIdle()

            val state = viewModel.uiState.value as CreateVaultUiState.Unlocked
            assertEquals("01010101010101010101010101010101", state.unlocked.vaultIdHex)
            assertEquals(2, state.unlocked.plaintextSize)
            assertTrue(state.unlocked.entries.isEmpty())
        }

    @Test
    fun addingEntryPersistsUpdatedVaultBlob() =
        runTest(mainDispatcherRule.dispatcher) {
            val harness = testHarness()
            val viewModel = harness.viewModel
            viewModel.updatePassword("passphrase")
            viewModel.createVault()
            advanceUntilIdle()
            writeAllCards(viewModel)

            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(10)))
            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(11)))
            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(12)))
            advanceUntilIdle()

            viewModel.updateEntryTitle("First")
            viewModel.updateEntryContent("Secret content")
            viewModel.saveEntryDraft()
            advanceUntilIdle()

            val rewriteState = viewModel.uiState.value as CreateVaultUiState.WritingCards
            assertEquals("条目已保存。 请依次重写所有 CUID 卡。", rewriteState.message)
            writeAllUpdatedCards(viewModel)

            val state = viewModel.uiState.value as CreateVaultUiState.Unlocked
            assertEquals("条目已保存，所有 CUID 卡已重写。", state.message)
            assertEquals(1, state.unlocked.entries.size)
            assertEquals(
                "First",
                state.unlocked.entries
                    .single()
                    .title,
            )
            assertEquals(
                "Secret content",
                state.unlocked.entries
                    .single()
                    .content,
            )
            assertEquals(1, state.unlocked.plaintextSize)
            assertTrue(state.draft.title.isEmpty())
            assertEquals(listOf(1), harness.core.encodedEntryCounts)
            assertEquals(listOf("01010101010101010101010101010101"), harness.vaultRepository.touchedVaultIds)
        }

    @Test
    fun existingVaultCanUnlockFromSavedVaultListAfterViewModelRestart() =
        runTest(mainDispatcherRule.dispatcher) {
            val vaultRepository = ViewModelFakeVaultRepository()
            val firstViewModel =
                testHarness(
                    vaultRepository = vaultRepository,
                ).viewModel
            firstViewModel.updatePassword("passphrase")
            firstViewModel.createVault()
            advanceUntilIdle()

            val restartedViewModel =
                testHarness(
                    vaultRepository = vaultRepository,
                ).viewModel
            advanceUntilIdle()

            val savedVault = restartedViewModel.savedVaults.value.single()
            assertEquals("01010101010101010101010101010101", savedVault.vaultIdHex)
            restartedViewModel.updatePassword("passphrase")
            restartedViewModel.startUnlockSavedVault(savedVault)

            val command = restartedViewModel.nextNfcCommand()
            assertTrue(command is NfcCommand.Read)
            restartedViewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(10)))
            restartedViewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(11)))
            restartedViewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(12)))
            advanceUntilIdle()

            val state = restartedViewModel.uiState.value as CreateVaultUiState.Unlocked
            assertEquals(savedVault.vaultIdHex, state.unlocked.vaultIdHex)
        }

    @Test
    fun cardRecoveryDoesNotRequireSavedVaultMetadata() =
        runTest(mainDispatcherRule.dispatcher) {
            val harness = testHarness()
            val viewModel = harness.viewModel
            viewModel.updatePassword("passphrase")
            viewModel.startRecoverFromCards()

            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(10)))
            advanceUntilIdle()
            val firstReadState = viewModel.uiState.value as CreateVaultUiState.ReadingCards
            assertEquals(1, firstReadState.readCount)
            assertEquals("已读取 1 张卡，卡片还不够，请继续刷入。", firstReadState.message)

            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(11)))
            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(12)))
            advanceUntilIdle()

            val unlocked = viewModel.uiState.value as CreateVaultUiState.Unlocked
            assertEquals("01010101010101010101010101010101", unlocked.summary.vaultIdHex)
            assertEquals("Recovered Vault", unlocked.summary.displayName)
            assertEquals(1, harness.vaultRepository.records.size)
            assertEquals(
                "Recovered Vault",
                harness.vaultRepository.records.values
                    .single()
                    .displayName,
            )
        }

    @Test
    fun settingsUpdatesAreExposedInUiState() =
        runTest(mainDispatcherRule.dispatcher) {
            val harness = testHarness()
            advanceUntilIdle()

            harness.viewModel.setOnboardingCompleted(true)
            harness.viewModel.setNfcExperimentalEnabled(true)
            harness.viewModel.setDiagnosticsEnabled(true)
            advanceUntilIdle()

            val settings = harness.viewModel.settings.value
            assertTrue(settings.onboardingCompleted)
            assertTrue(settings.nfcExperimentalEnabled)
            assertTrue(settings.diagnosticsEnabled)
        }

    private fun testViewModel(): CreateVaultViewModel = testHarness().viewModel

    private fun testHarness(vaultRepository: ViewModelFakeVaultRepository = ViewModelFakeVaultRepository()): ViewModelHarness {
        val core = ViewModelFakeMcvCore()
        val viewModel =
            CreateVaultViewModel(
                createVaultUseCase =
                    CreateVaultUseCase(
                        core = core,
                        vaultRepository = vaultRepository,
                    ),
                listVaultsUseCase =
                    ListVaultsUseCase(
                        vaultRepository = vaultRepository,
                    ),
                unlockVaultUseCase =
                    UnlockVaultUseCase(
                        core = core,
                        vaultRepository = vaultRepository,
                    ),
                updateVaultUseCase =
                    UpdateVaultUseCase(
                        core = core,
                        vaultRepository = vaultRepository,
                    ),
                appSettingsRepository = ViewModelFakeAppSettingsRepository(),
                workDispatcher = mainDispatcherRule.dispatcher,
                entryIdGenerator = { "03030303030303030303030303030303" },
                clock = { 1234 },
            )
        return ViewModelHarness(
            viewModel = viewModel,
            core = core,
            vaultRepository = vaultRepository,
        )
    }

    private fun writeAllCards(viewModel: CreateVaultViewModel) {
        while (viewModel.uiState.value is CreateVaultUiState.WritingCards) {
            val command = viewModel.nextNfcCommand() as NfcCommand.Write
            viewModel.onNfcResult(NfcCardResult.Success(command.payload))
        }
        assertTrue(viewModel.uiState.value is CreateVaultUiState.ReadingCards)
    }

    private fun writeAllUpdatedCards(viewModel: CreateVaultViewModel) {
        while (viewModel.uiState.value is CreateVaultUiState.WritingCards) {
            val command = viewModel.nextNfcCommand() as NfcCommand.Write
            viewModel.onNfcResult(NfcCardResult.Success(command.payload))
        }
        assertTrue(viewModel.uiState.value is CreateVaultUiState.Unlocked)
    }
}

private class ViewModelFakeAppSettingsRepository : AppSettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings())

    override val settings = mutableSettings.asStateFlow()

    override suspend fun setOnboardingCompleted(value: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(onboardingCompleted = value)
    }

    override suspend fun setDefaultThreshold(value: Int) {
        mutableSettings.value = mutableSettings.value.copy(defaultThreshold = value)
    }

    override suspend fun setDefaultTotal(value: Int) {
        mutableSettings.value = mutableSettings.value.copy(defaultTotal = value)
    }

    override suspend fun setNfcExperimentalEnabled(value: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(nfcExperimentalEnabled = value)
    }

    override suspend fun setDiagnosticsEnabled(value: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(diagnosticsEnabled = value)
    }
}

private data class ViewModelHarness(
    val viewModel: CreateVaultViewModel,
    val core: ViewModelFakeMcvCore,
    val vaultRepository: ViewModelFakeVaultRepository,
)

private class ViewModelFakeMcvCore : McvCore {
    val encodedEntryCounts = mutableListOf<Int>()

    override fun projectName(): String = "Multi-Card Vault"

    override fun projectStatus(): String = "experimental and unaudited"

    override fun emptyVaultPlaintext(): ByteArray = byteArrayOf(9)

    override fun decodeVaultPlaintext(bytes: ByteArray): RustVaultPlaintext = RustVaultPlaintext(emptyList())

    override fun encodeVaultPlaintext(plaintext: RustVaultPlaintext): ByteArray {
        encodedEntryCounts += plaintext.entries.size
        return byteArrayOf(plaintext.entries.size.toByte())
    }

    override fun createVault(
        password: String,
        threshold: Int,
        total: Int,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult =
        RustCreateVaultResult(
            vaultId = ByteArray(16) { 1 },
            schemeId = ByteArray(16) { 2 },
            cardPayloads = List(total) { index -> byteArrayOf((10 + index).toByte()) },
        )

    override fun unlockVault(
        password: String,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult {
        if (cardPayloads.size < 3) {
            throw McvCoreException("not enough cards", McvFfiException.NotEnoughShares())
        }
        return RustUnlockVaultResult(
            vaultId = ByteArray(16) { 1 },
            schemeId = ByteArray(16) { 2 },
            threshold = 3,
            total = 5,
            plaintext = byteArrayOf(4, 5),
        )
    }

    override fun updateVault(
        password: String,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult {
        require(newPlaintext.contentEquals(byteArrayOf(1)))
        return RustUpdateVaultResult(List(5) { index -> byteArrayOf((50 + index).toByte()) })
    }
}

private class ViewModelFakeVaultRepository : VaultRepository {
    val records = mutableMapOf<String, VaultRecord>()
    val touchedVaultIds = mutableListOf<String>()

    override suspend fun createVault(record: VaultRecord) {
        records[record.id] = record
    }

    override suspend fun getVault(id: String): VaultRecord? = records[id]

    override suspend fun listVaults(): List<VaultRecord> = records.values.toList()

    override suspend fun touchVault(
        id: String,
        updatedAt: Long,
    ) {
        val record = records[id] ?: error("vault not found")
        touchedVaultIds += id
        records[id] = record.copy(updatedAt = updatedAt)
    }

    override suspend fun deleteVault(id: String) {
        records.remove(id)
    }
}
