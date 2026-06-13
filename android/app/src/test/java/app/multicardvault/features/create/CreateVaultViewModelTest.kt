package app.multicardvault.features.create

import app.multicardvault.MainDispatcherRule
import app.multicardvault.core.McvCore
import app.multicardvault.core.McvCoreException
import app.multicardvault.core.RustCardPayloadInspection
import app.multicardvault.core.RustCreateVaultResult
import app.multicardvault.core.RustUnlockVaultResult
import app.multicardvault.core.RustUpdateVaultResult
import app.multicardvault.core.RustVaultPlaintext
import app.multicardvault.data.CardInventoryRecord
import app.multicardvault.data.CardInventoryRepository
import app.multicardvault.data.CardInventoryStatus
import app.multicardvault.data.VaultRecord
import app.multicardvault.data.VaultRepository
import app.multicardvault.features.cards.InspectCardUseCase
import app.multicardvault.features.cards.RecoverInterruptedReissueUseCase
import app.multicardvault.features.cards.StartCardSetReissueUseCase
import app.multicardvault.features.cards.VerifyCardSetUseCase
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
import kotlinx.coroutines.test.TestScope
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

    @Test
    fun verifyCardScanUpdatesCardInventoryState() =
        runTest(mainDispatcherRule.dispatcher) {
            val harness = testHarness()
            val viewModel = harness.viewModel
            unlockCreatedVault(viewModel)

            viewModel.startVerifyCurrentCard()
            assertTrue(viewModel.nextNfcCommand() is NfcCommand.Read)
            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(10)))
            advanceUntilIdle()

            val state = viewModel.uiState.value as CreateVaultUiState.CardVerified
            assertEquals(CardInventoryStatus.Current, state.result.status)
            assertEquals(1, harness.cardInventoryRepository.records.size)
            assertEquals(
                CardInventoryStatus.Current,
                harness.cardInventoryRepository.records
                    .single()
                    .status,
            )
        }

    @Test
    fun startCardSetReissueMovesToWritingCards() =
        runTest(mainDispatcherRule.dispatcher) {
            val harness = testHarness()
            val viewModel = harness.viewModel
            unlockCreatedVault(viewModel)

            viewModel.startCardSetReissue()
            advanceUntilIdle()

            val state = viewModel.uiState.value as CreateVaultUiState.WritingCards
            assertEquals(0, state.writtenCount)
            assertEquals(5, state.total)
            assertEquals("请依次重写所有 CUID 卡。", state.message)
            assertEquals(listOf(0), harness.core.encodedEntryCounts)
        }

    @Test
    fun interruptedRecoveryKeepsReadingUntilAGroupReachesThreshold() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = testViewModel()
            viewModel.updatePassword("passphrase")
            viewModel.startInterruptedReissueRecovery()

            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(21)))
            advanceUntilIdle()
            val first = viewModel.uiState.value as CreateVaultUiState.RecoveringInterruptedReissue
            assertEquals(1, first.readCount)

            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(22)))
            advanceUntilIdle()
            val second = viewModel.uiState.value as CreateVaultUiState.RecoveringInterruptedReissue
            assertEquals(2, second.readCount)

            viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(23)))
            advanceUntilIdle()

            val unlocked = viewModel.uiState.value as CreateVaultUiState.Unlocked
            assertEquals("01010101010101010101010101010101", unlocked.summary.vaultIdHex)
            assertEquals(3, unlocked.summary.threshold)
            assertEquals(5, unlocked.summary.total)
        }

    private fun testViewModel(): CreateVaultViewModel = testHarness().viewModel

    private fun testHarness(vaultRepository: ViewModelFakeVaultRepository = ViewModelFakeVaultRepository()): ViewModelHarness {
        val core = ViewModelFakeMcvCore()
        val cardInventoryRepository = ViewModelFakeCardInventoryRepository()
        val inspectCardUseCase = InspectCardUseCase(core)
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
                inspectCardUseCase = inspectCardUseCase,
                verifyCardSetUseCase =
                    VerifyCardSetUseCase(
                        inspectCardUseCase = inspectCardUseCase,
                        cardInventoryRepository = cardInventoryRepository,
                        clock = { 1234 },
                    ),
                startCardSetReissueUseCase = StartCardSetReissueUseCase(core),
                recoverInterruptedReissueUseCase = RecoverInterruptedReissueUseCase(),
                appSettingsRepository = ViewModelFakeAppSettingsRepository(),
                workDispatcher = mainDispatcherRule.dispatcher,
                entryIdGenerator = { "03030303030303030303030303030303" },
                clock = { 1234 },
            )
        return ViewModelHarness(
            viewModel = viewModel,
            core = core,
            vaultRepository = vaultRepository,
            cardInventoryRepository = cardInventoryRepository,
        )
    }

    private fun TestScope.unlockCreatedVault(viewModel: CreateVaultViewModel) {
        viewModel.updatePassword("passphrase")
        viewModel.createVault()
        advanceUntilIdle()
        writeAllCards(viewModel)
        viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(10)))
        viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(11)))
        viewModel.onNfcResult(NfcCardResult.Success(byteArrayOf(12)))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CreateVaultUiState.Unlocked)
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
    val cardInventoryRepository: ViewModelFakeCardInventoryRepository,
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
        require(newPlaintext.contentEquals(byteArrayOf(0)) || newPlaintext.contentEquals(byteArrayOf(1)))
        return RustUpdateVaultResult(List(5) { index -> byteArrayOf((50 + index).toByte()) })
    }

    override fun inspectCardPayload(cardPayload: ByteArray): RustCardPayloadInspection {
        val first = cardPayload.firstOrNull()?.toInt() ?: error("empty card payload")
        val shareIndex =
            when (first) {
                in 10..14 -> first - 9
                in 21..25 -> first - 20
                else -> 1
            }
        return RustCardPayloadInspection(
            vaultId = ByteArray(16) { 1 },
            schemeId = ByteArray(16) { 2 },
            threshold = 3,
            total = 5,
            shareIndex = shareIndex,
            kdfId = 1,
            aeadId = 1,
            formatVersion = 1,
        )
    }
}

private class ViewModelFakeCardInventoryRepository : CardInventoryRepository {
    val records = mutableListOf<CardInventoryRecord>()

    override suspend fun upsert(record: CardInventoryRecord) {
        records.removeAll {
            it.vaultIdHex == record.vaultIdHex &&
                it.schemeIdHex == record.schemeIdHex &&
                it.shareIndex == record.shareIndex
        }
        records += record
    }

    override suspend fun listForVault(vaultIdHex: String): List<CardInventoryRecord> = records.filter { it.vaultIdHex == vaultIdHex }

    override suspend fun listForCardSet(
        vaultIdHex: String,
        schemeIdHex: String,
    ): List<CardInventoryRecord> = records.filter { it.vaultIdHex == vaultIdHex && it.schemeIdHex == schemeIdHex }

    override suspend fun markCurrentCardSet(
        vaultIdHex: String,
        currentSchemeIdHex: String,
    ) = Unit
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
