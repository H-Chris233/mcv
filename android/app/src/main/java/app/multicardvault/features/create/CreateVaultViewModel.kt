package app.multicardvault.features.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.multicardvault.core.toStableHex
import app.multicardvault.features.cards.CardVerificationResult
import app.multicardvault.features.cards.CardVerificationTarget
import app.multicardvault.features.cards.InspectCardUseCase
import app.multicardvault.features.cards.InterruptedReissueRecoveryResult
import app.multicardvault.features.cards.RecoverInterruptedReissueUseCase
import app.multicardvault.features.cards.ScannedCardPayload
import app.multicardvault.features.cards.StartCardSetReissueUseCase
import app.multicardvault.features.cards.VerifyCardSetUseCase
import app.multicardvault.features.unlock.NotEnoughCardsException
import app.multicardvault.features.unlock.UnlockVaultUseCase
import app.multicardvault.features.unlock.UnlockedVaultSummary
import app.multicardvault.features.vault.ListVaultsUseCase
import app.multicardvault.features.vault.SavedVaultSummary
import app.multicardvault.features.vault.UpdateVaultUseCase
import app.multicardvault.features.vault.VaultEntry
import app.multicardvault.features.vault.VaultEntryDraft
import app.multicardvault.nfc.NfcCardResult
import app.multicardvault.settings.AppSettings
import app.multicardvault.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom

data class CreateVaultFormState(
    val displayName: String = "Personal Vault",
    val password: String = "",
    val threshold: Int = CreateVaultUseCase.DefaultThreshold,
    val total: Int = CreateVaultUseCase.DefaultTotal,
)

sealed interface CreateVaultUiState {
    data object Editing : CreateVaultUiState

    data object Creating : CreateVaultUiState

    data class WritingCards(
        val summary: CreatedVaultSummary,
        val writtenCount: Int,
        val total: Int,
        val nextCardNumber: Int,
        val message: String,
    ) : CreateVaultUiState

    data class ReadingCards(
        val summary: CreatedVaultSummary,
        val readCount: Int,
        val threshold: Int,
        val message: String,
    ) : CreateVaultUiState

    data class Unlocking(
        val summary: CreatedVaultSummary,
        val readCount: Int,
        val threshold: Int,
    ) : CreateVaultUiState

    data class VerifyingCard(
        val summary: CreatedVaultSummary,
        val currentSchemeIdHex: String,
        val scannedShareIndexes: Set<Int>,
        val message: String,
    ) : CreateVaultUiState

    data class CardVerified(
        val summary: CreatedVaultSummary,
        val result: CardVerificationResult,
    ) : CreateVaultUiState

    data class RecoveringInterruptedReissue(
        val readCount: Int,
        val threshold: Int,
        val message: String,
    ) : CreateVaultUiState

    data class Unlocked(
        val summary: CreatedVaultSummary,
        val unlocked: UnlockedVaultSummary,
        val draft: VaultEntryDraft = VaultEntryDraft(),
        val isSaving: Boolean = false,
        val message: String? = null,
    ) : CreateVaultUiState

    data class Failed(
        val message: String,
    ) : CreateVaultUiState
}

sealed interface NfcCommand {
    data class Write(
        val payload: ByteArray,
    ) : NfcCommand

    data object Read : NfcCommand
}

class CreateVaultViewModel(
    private val createVaultUseCase: CreateVaultUseCase,
    private val listVaultsUseCase: ListVaultsUseCase,
    private val unlockVaultUseCase: UnlockVaultUseCase,
    private val updateVaultUseCase: UpdateVaultUseCase,
    private val inspectCardUseCase: InspectCardUseCase,
    private val verifyCardSetUseCase: VerifyCardSetUseCase,
    private val startCardSetReissueUseCase: StartCardSetReissueUseCase,
    private val recoverInterruptedReissueUseCase: RecoverInterruptedReissueUseCase,
    private val appSettingsRepository: AppSettingsRepository,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val entryIdGenerator: () -> String = { generateEntryIdHex() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _form = MutableStateFlow(CreateVaultFormState())
    val form: StateFlow<CreateVaultFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<CreateVaultUiState>(CreateVaultUiState.Editing)
    val uiState: StateFlow<CreateVaultUiState> = _uiState.asStateFlow()

    private val _savedVaults = MutableStateFlow<List<SavedVaultSummary>>(emptyList())
    val savedVaults: StateFlow<List<SavedVaultSummary>> = _savedVaults.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var currentSession: CreatedVaultSession? = null
    private var pendingUnlockedAfterRewrite: CreateVaultUiState.Unlocked? = null
    private val writtenPayloadIndexes = mutableSetOf<Int>()
    private val scannedPayloads = mutableListOf<ByteArray>()
    private val interruptedScans = mutableListOf<ScannedCardPayload>()

    init {
        refreshSavedVaults()
        observeSettings()
    }

    fun updateDisplayName(value: String) {
        _form.update { it.copy(displayName = value) }
        resetFailure()
    }

    fun updatePassword(value: String) {
        _form.update { it.copy(password = value) }
        resetFailure()
    }

    fun createVault() {
        if (_uiState.value == CreateVaultUiState.Creating) return

        val snapshot = _form.value
        viewModelScope.launch {
            _uiState.value = CreateVaultUiState.Creating
            _uiState.value =
                runCatching {
                    withContext(workDispatcher) {
                        createVaultUseCase(
                            displayName = snapshot.displayName,
                            password = snapshot.password,
                            threshold = snapshot.threshold,
                            total = snapshot.total,
                        )
                    }
                }.fold(
                    onSuccess = { session ->
                        currentSession = session
                        pendingUnlockedAfterRewrite = null
                        writtenPayloadIndexes.clear()
                        scannedPayloads.clear()
                        refreshSavedVaults()
                        CreateVaultUiState.WritingCards(
                            summary = session.summary,
                            writtenCount = 0,
                            total = session.cardPayloads.size,
                            nextCardNumber = 1,
                            message = "请贴近第 1 张 CUID 卡。",
                        )
                    },
                    onFailure = { CreateVaultUiState.Failed(UserSafeCreateError) },
                )
        }
    }

    fun startUnlockSavedVault(vault: SavedVaultSummary) {
        if (_form.value.password.isEmpty()) {
            _uiState.value = CreateVaultUiState.Failed("请输入主密码后再解锁已有保险库。")
            return
        }
        if (_uiState.value == CreateVaultUiState.Creating) return
        if (_uiState.value is CreateVaultUiState.WritingCards) return
        if (_uiState.value is CreateVaultUiState.ReadingCards) return
        if (_uiState.value is CreateVaultUiState.Unlocking) return

        val summary =
            CreatedVaultSummary(
                vaultIdHex = vault.vaultIdHex,
                displayName = vault.displayName,
                threshold = vault.threshold,
                total = vault.total,
                cardPayloadCount = 0,
            )
        currentSession =
            CreatedVaultSession(
                summary = summary,
                cardPayloads = emptyList(),
            )
        pendingUnlockedAfterRewrite = null
        writtenPayloadIndexes.clear()
        scannedPayloads.clear()
        _uiState.value =
            CreateVaultUiState.ReadingCards(
                summary = summary,
                readCount = 0,
                threshold = summary.threshold,
                message = "请刷入任意 ${summary.threshold} 张卡解锁已有保险库。",
            )
    }

    fun startRecoverFromCards() {
        if (_form.value.password.isEmpty()) {
            _uiState.value = CreateVaultUiState.Failed("请输入主密码后再从卡片恢复。")
            return
        }
        if (!canStartCardReading()) return

        val summary =
            CreatedVaultSummary(
                vaultIdHex = "",
                displayName = "Recovered Vault",
                threshold = _form.value.threshold,
                total = _form.value.total,
                cardPayloadCount = 0,
            )
        currentSession =
            CreatedVaultSession(
                summary = summary,
                cardPayloads = emptyList(),
            )
        pendingUnlockedAfterRewrite = null
        writtenPayloadIndexes.clear()
        scannedPayloads.clear()
        _uiState.value =
            CreateVaultUiState.ReadingCards(
                summary = summary,
                readCount = 0,
                threshold = summary.threshold,
                message = "请刷入 CUID 卡。卡片足够后将自动恢复保险库。",
            )
    }

    fun nextNfcCommand(): NfcCommand? {
        val session = currentSession ?: return null
        return when (_uiState.value) {
            is CreateVaultUiState.WritingCards -> {
                val index = nextWriteIndex(session) ?: return null
                NfcCommand.Write(session.cardPayloads[index])
            }

            is CreateVaultUiState.ReadingCards -> NfcCommand.Read
            is CreateVaultUiState.VerifyingCard -> NfcCommand.Read
            is CreateVaultUiState.RecoveringInterruptedReissue -> NfcCommand.Read
            else -> null
        }
    }

    fun onNfcResult(result: NfcCardResult) {
        when (val state = _uiState.value) {
            is CreateVaultUiState.WritingCards -> handleWriteResult(state, result)
            is CreateVaultUiState.ReadingCards -> handleReadResult(state, result)
            is CreateVaultUiState.VerifyingCard -> handleVerifyResult(state, result)
            is CreateVaultUiState.RecoveringInterruptedReissue -> handleInterruptedRecoveryResult(state, result)
            else -> Unit
        }
    }

    fun startVerifyCurrentCard() {
        val state = _uiState.value as? CreateVaultUiState.Unlocked ?: return
        _uiState.value =
            CreateVaultUiState.VerifyingCard(
                summary = state.summary,
                currentSchemeIdHex = state.unlocked.schemeIdHex,
                scannedShareIndexes = emptySet(),
                message = "请贴近要校验的 CUID 卡。",
            )
    }

    fun startCardSetReissue() {
        val state = _uiState.value as? CreateVaultUiState.Unlocked ?: return
        val payloads = scannedPayloads.map { it.copyOf() }
        if (payloads.size < state.unlocked.threshold) {
            _uiState.value = state.copy(message = "当前会话没有足够卡片 payload，需重新刷卡解锁后再重发。")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, message = "正在生成新的卡组。")
            _uiState.value =
                runCatching {
                    withContext(workDispatcher) {
                        startCardSetReissueUseCase(
                            vaultIdHex = state.summary.vaultIdHex,
                            password = _form.value.password,
                            cardPayloads = payloads,
                            entries = state.unlocked.entries,
                            updatedAt = clock(),
                        )
                    }
                }.fold(
                    onSuccess = { reissue ->
                        currentSession =
                            CreatedVaultSession(
                                summary = state.summary,
                                cardPayloads = reissue.cardPayloads,
                            )
                        pendingUnlockedAfterRewrite = state.copy(isSaving = false, message = "卡组已重发。")
                        writtenPayloadIndexes.clear()
                        scannedPayloads.clear()
                        CreateVaultUiState.WritingCards(
                            summary = state.summary,
                            writtenCount = 0,
                            total = reissue.cardPayloads.size,
                            nextCardNumber = 1,
                            message = "请依次重写所有 CUID 卡。",
                        )
                    },
                    onFailure = {
                        state.copy(isSaving = false, message = "无法生成新的卡组。")
                    },
                )
        }
    }

    fun startInterruptedReissueRecovery() {
        if (_form.value.password.isEmpty()) {
            _uiState.value = CreateVaultUiState.Failed("请输入主密码后再恢复中断重发。")
            return
        }
        if (!canStartCardReading()) return
        currentSession =
            CreatedVaultSession(
                summary =
                    CreatedVaultSummary(
                        vaultIdHex = "",
                        displayName = "Recovered Vault",
                        threshold = _form.value.threshold,
                        total = _form.value.total,
                        cardPayloadCount = 0,
                    ),
                cardPayloads = emptyList(),
            )
        interruptedScans.clear()
        scannedPayloads.clear()
        _uiState.value =
            CreateVaultUiState.RecoveringInterruptedReissue(
                readCount = 0,
                threshold = _form.value.threshold,
                message = "请刷入中断重发前后的 CUID 卡，凑够任一卡组门限后自动解锁。",
            )
    }

    fun updateEntryTitle(value: String) {
        _uiState.update { state ->
            if (state is CreateVaultUiState.Unlocked) {
                state.copy(draft = state.draft.copy(title = value), message = null)
            } else {
                state
            }
        }
    }

    fun updateEntryContent(value: String) {
        _uiState.update { state ->
            if (state is CreateVaultUiState.Unlocked) {
                state.copy(draft = state.draft.copy(content = value), message = null)
            } else {
                state
            }
        }
    }

    fun editEntry(entryIdHex: String) {
        val state = _uiState.value as? CreateVaultUiState.Unlocked ?: return
        if (state.isSaving) return

        val entry = state.unlocked.entries.firstOrNull { it.idHex == entryIdHex } ?: return
        _uiState.value =
            state.copy(
                draft =
                    VaultEntryDraft(
                        editingEntryIdHex = entry.idHex,
                        title = entry.title,
                        content = entry.content,
                    ),
                message = null,
            )
    }

    fun cancelEntryEdit() {
        _uiState.update { state ->
            if (state is CreateVaultUiState.Unlocked) {
                state.copy(draft = VaultEntryDraft(), message = null)
            } else {
                state
            }
        }
    }

    fun saveEntryDraft() {
        val state = _uiState.value as? CreateVaultUiState.Unlocked ?: return
        if (state.isSaving) return

        val title = state.draft.title.trim()
        val content = state.draft.content
        if (title.isEmpty()) {
            _uiState.value = state.copy(message = "标题不能为空。")
            return
        }

        val now = clock()
        val nextEntries =
            if (state.draft.editingEntryIdHex == null) {
                state.unlocked.entries +
                    VaultEntry(
                        idHex = entryIdGenerator(),
                        title = title,
                        content = content,
                        createdAt = now,
                        updatedAt = now,
                    )
            } else {
                state.unlocked.entries.map { entry ->
                    if (entry.idHex == state.draft.editingEntryIdHex) {
                        entry.copy(
                            title = title,
                            content = content,
                            updatedAt = maxOf(now, entry.createdAt),
                        )
                    } else {
                        entry
                    }
                }
            }

        persistUnlockedEntries(
            state = state,
            entries = nextEntries,
            updatedAt = now,
            nextDraft = VaultEntryDraft(),
            successMessage = "条目已保存。",
        )
    }

    fun deleteEntry(entryIdHex: String) {
        val state = _uiState.value as? CreateVaultUiState.Unlocked ?: return
        if (state.isSaving) return

        val nextEntries = state.unlocked.entries.filterNot { it.idHex == entryIdHex }
        if (nextEntries.size == state.unlocked.entries.size) return

        persistUnlockedEntries(
            state = state,
            entries = nextEntries,
            updatedAt = clock(),
            nextDraft =
                if (state.draft.editingEntryIdHex == entryIdHex) {
                    VaultEntryDraft()
                } else {
                    state.draft
                },
            successMessage = "条目已删除。",
        )
    }

    fun setOnboardingCompleted(value: Boolean) {
        updateSettings { appSettingsRepository.setOnboardingCompleted(value) }
    }

    fun setNfcExperimentalEnabled(value: Boolean) {
        updateSettings { appSettingsRepository.setNfcExperimentalEnabled(value) }
    }

    fun setDiagnosticsEnabled(value: Boolean) {
        updateSettings { appSettingsRepository.setDiagnosticsEnabled(value) }
    }

    private fun handleWriteResult(
        state: CreateVaultUiState.WritingCards,
        result: NfcCardResult,
    ) {
        val session = currentSession ?: return
        if (result !is NfcCardResult.Success) {
            _uiState.value = state.copy(message = nfcMessage(result))
            return
        }

        val writtenIndex = nextWriteIndex(session) ?: return
        writtenPayloadIndexes += writtenIndex
        val nextIndex = nextWriteIndex(session)
        _uiState.value =
            if (nextIndex == null) {
                val pending = pendingUnlockedAfterRewrite
                if (pending == null) {
                    CreateVaultUiState.ReadingCards(
                        summary = session.summary,
                        readCount = 0,
                        threshold = session.summary.threshold,
                        message = "写卡完成。请刷入任意 ${session.summary.threshold} 张卡解锁。",
                    )
                } else {
                    pendingUnlockedAfterRewrite = null
                    scannedPayloads.clear()
                    scannedPayloads += session.cardPayloads.map { it.copyOf() }
                    pending.copy(message = "条目已保存，所有 CUID 卡已重写。")
                }
            } else {
                CreateVaultUiState.WritingCards(
                    summary = session.summary,
                    writtenCount = writtenPayloadIndexes.size,
                    total = session.cardPayloads.size,
                    nextCardNumber = nextIndex + 1,
                    message = "写入成功。请贴近第 ${nextIndex + 1} 张 CUID 卡。",
                )
            }
    }

    private fun handleReadResult(
        state: CreateVaultUiState.ReadingCards,
        result: NfcCardResult,
    ) {
        if (result !is NfcCardResult.Success) {
            _uiState.value = state.copy(message = nfcMessage(result))
            return
        }

        if (scannedPayloads.any { it.contentEquals(result.payload) }) {
            _uiState.value = state.copy(message = "这张卡已经读取过，请换另一张卡。")
            return
        }

        scannedPayloads += result.payload.copyOf()
        if (state.summary.isCardRecovery() || scannedPayloads.size >= state.threshold) {
            unlockScannedCards(state.summary)
        } else {
            _uiState.value =
                state.copy(
                    readCount = scannedPayloads.size,
                    message = "读取成功。请继续刷入卡片。",
                )
        }
    }

    private fun handleVerifyResult(
        state: CreateVaultUiState.VerifyingCard,
        result: NfcCardResult,
    ) {
        if (result !is NfcCardResult.Success) {
            _uiState.value = state.copy(message = nfcMessage(result))
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(message = "正在校验卡片。")
            _uiState.value =
                runCatching {
                    withContext(workDispatcher) {
                        verifyCardSetUseCase(
                            target =
                                CardVerificationTarget(
                                    vaultIdHex = state.summary.vaultIdHex,
                                    currentSchemeIdHex = state.currentSchemeIdHex,
                                    displayName = state.summary.displayName,
                                ),
                            cardPayload = result.payload,
                            scannedShareIndexes = state.scannedShareIndexes,
                        )
                    }
                }.fold(
                    onSuccess = { verified ->
                        CreateVaultUiState.CardVerified(
                            summary = state.summary,
                            result = verified,
                        )
                    },
                    onFailure = {
                        state.copy(message = "无法校验卡片。")
                    },
                )
        }
    }

    private fun handleInterruptedRecoveryResult(
        state: CreateVaultUiState.RecoveringInterruptedReissue,
        result: NfcCardResult,
    ) {
        if (result !is NfcCardResult.Success) {
            _uiState.value = state.copy(message = nfcMessage(result))
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(message = "正在检查卡片所属卡组。")
            _uiState.value =
                runCatching {
                    withContext(workDispatcher) {
                        val inspection = inspectCardUseCase(result.payload)
                        interruptedScans +=
                            ScannedCardPayload(
                                payload = result.payload.copyOf(),
                                inspection = inspection,
                            )
                        recoverInterruptedReissueUseCase(interruptedScans.toList())
                    }
                }.fold(
                    onSuccess = { recovery ->
                        when (recovery) {
                            is InterruptedReissueRecoveryResult.NeedsMoreCards ->
                                CreateVaultUiState.RecoveringInterruptedReissue(
                                    readCount = recovery.readCount,
                                    threshold = recovery.threshold,
                                    message = "已读取 ${recovery.readCount} 张有效卡，请继续刷入。",
                                )

                            is InterruptedReissueRecoveryResult.ReadyToUnlock -> {
                                scannedPayloads.clear()
                                scannedPayloads += recovery.cardPayloads.map { it.copyOf() }
                                val summary =
                                    CreatedVaultSummary(
                                        vaultIdHex = recovery.vaultIdHex,
                                        displayName = "Recovered Vault",
                                        threshold = recovery.threshold,
                                        total = recovery.total,
                                        cardPayloadCount = 0,
                                    )
                                unlockScannedCards(summary)
                                CreateVaultUiState.Unlocking(
                                    summary = summary,
                                    readCount = recovery.cardPayloads.size,
                                    threshold = recovery.threshold,
                                )
                            }
                        }
                    },
                    onFailure = {
                        state.copy(message = "卡片不是有效的 MCV 卡片，请继续刷入。")
                    },
                )
        }
    }

    private fun unlockScannedCards(summary: CreatedVaultSummary) {
        val payloads = scannedPayloads.map { it.copyOf() }
        viewModelScope.launch {
            _uiState.value =
                CreateVaultUiState.Unlocking(
                    summary = summary,
                    readCount = payloads.size,
                    threshold = summary.threshold,
                )
            _uiState.value =
                runCatching {
                    withContext(workDispatcher) {
                        unlockVaultUseCase(
                            vaultIdHex = summary.vaultIdHex.ifBlank { null },
                            displayName = summary.displayName,
                            password = _form.value.password,
                            cardPayloads = payloads,
                        )
                    }
                }.fold(
                    onSuccess = { unlocked ->
                        val resolvedSummary =
                            if (summary.isCardRecovery()) {
                                CreatedVaultSummary(
                                    vaultIdHex = unlocked.vaultIdHex,
                                    displayName = unlocked.displayName,
                                    threshold = unlocked.threshold,
                                    total = unlocked.total,
                                    cardPayloadCount = 0,
                                )
                            } else {
                                summary
                            }
                        refreshSavedVaults()
                        CreateVaultUiState.Unlocked(resolvedSummary, unlocked)
                    },
                    onFailure = { error ->
                        if (error is NotEnoughCardsException && summary.isCardRecovery()) {
                            CreateVaultUiState.ReadingCards(
                                summary = summary,
                                readCount = payloads.size,
                                threshold = summary.threshold,
                                message = "已读取 ${payloads.size} 张卡，卡片还不够，请继续刷入。",
                            )
                        } else {
                            CreateVaultUiState.Failed(UnlockFailedMessage)
                        }
                    },
                )
        }
    }

    private fun persistUnlockedEntries(
        state: CreateVaultUiState.Unlocked,
        entries: List<VaultEntry>,
        updatedAt: Long,
        nextDraft: VaultEntryDraft,
        successMessage: String,
    ) {
        val payloads = scannedPayloads.map { it.copyOf() }
        val password = _form.value.password

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, message = "正在生成新的卡片数据。")
            _uiState.value =
                runCatching {
                    withContext(workDispatcher) {
                        updateVaultUseCase(
                            vaultIdHex = state.summary.vaultIdHex,
                            password = password,
                            cardPayloads = payloads,
                            entries = entries,
                            updatedAt = updatedAt,
                        )
                    }
                }.fold(
                    onSuccess = { updated ->
                        refreshSavedVaults()
                        val unlockedState =
                            state.copy(
                                unlocked =
                                    state.unlocked.copy(
                                        plaintextSize = updated.plaintextSize,
                                        entries = entries,
                                    ),
                                draft = nextDraft,
                                isSaving = false,
                                message = successMessage,
                            )
                        currentSession =
                            CreatedVaultSession(
                                summary = state.summary,
                                cardPayloads = updated.cardPayloads,
                            )
                        pendingUnlockedAfterRewrite = unlockedState
                        writtenPayloadIndexes.clear()
                        scannedPayloads.clear()
                        CreateVaultUiState.WritingCards(
                            summary = state.summary,
                            writtenCount = 0,
                            total = updated.cardPayloads.size,
                            nextCardNumber = 1,
                            message = "$successMessage 请依次重写所有 CUID 卡。",
                        )
                    },
                    onFailure = {
                        state.copy(
                            isSaving = false,
                            message = SaveFailedMessage,
                        )
                    },
                )
        }
    }

    private fun refreshSavedVaults() {
        viewModelScope.launch {
            val vaults =
                runCatching {
                    withContext(workDispatcher) {
                        listVaultsUseCase()
                    }
                }.getOrDefault(emptyList())
            _savedVaults.value = vaults
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            appSettingsRepository.settings.collect { settings ->
                _settings.value = settings
            }
        }
    }

    private fun updateSettings(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(workDispatcher) {
                    block()
                }
            }
        }
    }

    private fun nextWriteIndex(session: CreatedVaultSession): Int? =
        session.cardPayloads.indices.firstOrNull { it !in writtenPayloadIndexes }

    private fun canStartCardReading(): Boolean =
        when (_uiState.value) {
            CreateVaultUiState.Creating -> false
            is CreateVaultUiState.WritingCards -> false
            is CreateVaultUiState.ReadingCards -> false
            is CreateVaultUiState.Unlocking -> false
            is CreateVaultUiState.VerifyingCard -> false
            is CreateVaultUiState.RecoveringInterruptedReissue -> false
            else -> true
        }

    private fun CreatedVaultSummary.isCardRecovery(): Boolean = vaultIdHex.isBlank()

    private fun nfcMessage(result: NfcCardResult): String =
        when (result) {
            is NfcCardResult.Success -> "NFC 操作成功。"
            is NfcCardResult.UnsupportedTag -> result.reason
            NfcCardResult.EmptyTag -> "标签为空，或没有本应用的 Card Payload。"
            is NfcCardResult.InvalidPayload -> result.reason
            is NfcCardResult.CapacityTooSmall -> "标签容量不足，需要 ${result.requiredBytes} 字节，可用 ${result.maxBytes} 字节。"
            is NfcCardResult.IoError -> result.reason
        }

    private fun resetFailure() {
        if (_uiState.value is CreateVaultUiState.Failed) {
            _uiState.value = CreateVaultUiState.Editing
        }
    }

    class Factory(
        private val createVaultUseCase: CreateVaultUseCase,
        private val listVaultsUseCase: ListVaultsUseCase,
        private val unlockVaultUseCase: UnlockVaultUseCase,
        private val updateVaultUseCase: UpdateVaultUseCase,
        private val inspectCardUseCase: InspectCardUseCase,
        private val verifyCardSetUseCase: VerifyCardSetUseCase,
        private val startCardSetReissueUseCase: StartCardSetReissueUseCase,
        private val recoverInterruptedReissueUseCase: RecoverInterruptedReissueUseCase,
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateVaultViewModel::class.java))
            return CreateVaultViewModel(
                createVaultUseCase = createVaultUseCase,
                listVaultsUseCase = listVaultsUseCase,
                unlockVaultUseCase = unlockVaultUseCase,
                updateVaultUseCase = updateVaultUseCase,
                inspectCardUseCase = inspectCardUseCase,
                verifyCardSetUseCase = verifyCardSetUseCase,
                startCardSetReissueUseCase = startCardSetReissueUseCase,
                recoverInterruptedReissueUseCase = recoverInterruptedReissueUseCase,
                appSettingsRepository = appSettingsRepository,
            ) as T
        }
    }

    companion object {
        private const val UserSafeCreateError = "无法创建保险库。请检查主密码或卡片数据状态。"
        private const val UnlockFailedMessage = "密码错误、卡片不属于该保险库，或数据已损坏。"
        private const val SaveFailedMessage = "无法保存条目。请确认 Vault 仍处于解锁会话并重试。"
        private val EntryIdRandom = SecureRandom()

        private fun generateEntryIdHex(): String {
            val bytes = ByteArray(16)
            EntryIdRandom.nextBytes(bytes)
            return bytes.toStableHex()
        }
    }
}
