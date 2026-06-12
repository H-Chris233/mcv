package app.multicardvault.features.create

import app.multicardvault.MainDispatcherRule
import app.multicardvault.core.McvCore
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
import app.multicardvault.security.DeviceSecretRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun writeCardProgressAdvancesOnNfcSuccess() = runTest(mainDispatcherRule.dispatcher) {
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
    fun duplicateExactPayloadDoesNotCountTwiceDuringUnlock() = runTest(mainDispatcherRule.dispatcher) {
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
    fun unlockTriggersWhenThresholdIsReached() = runTest(mainDispatcherRule.dispatcher) {
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
    fun addingEntryPersistsUpdatedVaultBlob() = runTest(mainDispatcherRule.dispatcher) {
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

        val state = viewModel.uiState.value as CreateVaultUiState.Unlocked
        assertEquals("条目已保存。", state.message)
        assertEquals(1, state.unlocked.entries.size)
        assertEquals("First", state.unlocked.entries.single().title)
        assertEquals("Secret content", state.unlocked.entries.single().content)
        assertEquals(1, state.unlocked.plaintextSize)
        assertTrue(state.draft.title.isEmpty())
        assertEquals(listOf(1), harness.core.encodedEntryCounts)
        assertEquals(listOf(byteArrayOf(99).toList()), harness.vaultRepository.savedVaultBlobs.map { it.toList() })
    }

    @Test
    fun existingVaultCanUnlockFromSavedVaultListAfterViewModelRestart() = runTest(mainDispatcherRule.dispatcher) {
        val vaultRepository = ViewModelFakeVaultRepository()
        val deviceSecretRepository = ViewModelFakeDeviceSecretRepository()
        val firstViewModel = testHarness(
            vaultRepository = vaultRepository,
            deviceSecretRepository = deviceSecretRepository,
        ).viewModel
        firstViewModel.updatePassword("passphrase")
        firstViewModel.createVault()
        advanceUntilIdle()

        val restartedViewModel = testHarness(
            vaultRepository = vaultRepository,
            deviceSecretRepository = deviceSecretRepository,
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

    private fun testViewModel(): CreateVaultViewModel {
        return testHarness().viewModel
    }

    private fun testHarness(
        vaultRepository: ViewModelFakeVaultRepository = ViewModelFakeVaultRepository(),
        deviceSecretRepository: ViewModelFakeDeviceSecretRepository = ViewModelFakeDeviceSecretRepository(),
    ): ViewModelHarness {
        val core = ViewModelFakeMcvCore()
        val viewModel = CreateVaultViewModel(
            createVaultUseCase = CreateVaultUseCase(
                core = core,
                vaultRepository = vaultRepository,
                deviceSecretRepository = deviceSecretRepository,
            ),
            listVaultsUseCase = ListVaultsUseCase(
                vaultRepository = vaultRepository,
            ),
            unlockVaultUseCase = UnlockVaultUseCase(
                core = core,
                vaultRepository = vaultRepository,
                deviceSecretRepository = deviceSecretRepository,
            ),
            updateVaultUseCase = UpdateVaultUseCase(
                core = core,
                vaultRepository = vaultRepository,
                deviceSecretRepository = deviceSecretRepository,
            ),
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
        deviceSecret: ByteArray,
        initialPlaintext: ByteArray,
    ): RustCreateVaultResult = RustCreateVaultResult(
        vaultId = ByteArray(16) { 1 },
        schemeId = ByteArray(16) { 2 },
        vaultBlob = byteArrayOf(3),
        cardPayloads = List(total) { index -> byteArrayOf((10 + index).toByte()) },
    )

    override fun unlockVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
    ): RustUnlockVaultResult {
        require(cardPayloads.size >= 2)
        return RustUnlockVaultResult(byteArrayOf(4, 5))
    }

    override fun updateVault(
        password: String,
        deviceSecret: ByteArray,
        vaultBlob: ByteArray,
        cardPayloads: List<ByteArray>,
        newPlaintext: ByteArray,
    ): RustUpdateVaultResult {
        require(newPlaintext.contentEquals(byteArrayOf(1)))
        return RustUpdateVaultResult(byteArrayOf(99))
    }
}

private class ViewModelFakeVaultRepository : VaultRepository {
    private val records = mutableMapOf<String, VaultRecord>()
    val savedVaultBlobs = mutableListOf<ByteArray>()

    override suspend fun createVault(record: VaultRecord) {
        records[record.id] = record
    }

    override suspend fun getVault(id: String): VaultRecord? = records[id]

    override suspend fun listVaults(): List<VaultRecord> = records.values.toList()

    override suspend fun updateVaultBlob(id: String, vaultBlob: ByteArray, updatedAt: Long) {
        val record = records[id] ?: error("vault not found")
        savedVaultBlobs += vaultBlob.copyOf()
        records[id] = record.copy(vaultBlob = vaultBlob.copyOf(), updatedAt = updatedAt)
    }

    override suspend fun deleteVault(id: String) {
        records.remove(id)
    }
}

private class ViewModelFakeDeviceSecretRepository : DeviceSecretRepository {
    private val generatedSecret = ByteArray(32) { 7 }
    private val savedSecrets = mutableMapOf<String, ByteArray>()

    override fun generateDeviceSecret(): ByteArray = generatedSecret.copyOf()

    override suspend fun saveDeviceSecret(vaultId: ByteArray, deviceSecret: ByteArray) {
        savedSecrets[vaultId.toStableTestHex()] = deviceSecret.copyOf()
    }

    override suspend fun getDeviceSecret(vaultId: ByteArray): ByteArray? =
        savedSecrets[vaultId.toStableTestHex()]?.copyOf()

    override suspend fun deleteDeviceSecret(vaultId: ByteArray) {
        savedSecrets.remove(vaultId.toStableTestHex())
    }
}

private fun ByteArray.toStableTestHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte)
}
