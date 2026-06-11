package app.multicardvault.features.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.multicardvault.features.unlock.UnlockVaultUseCase
import app.multicardvault.features.unlock.UnlockedVaultSummary
import app.multicardvault.nfc.NfcCardResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    data class Unlocked(
        val summary: CreatedVaultSummary,
        val unlocked: UnlockedVaultSummary,
    ) : CreateVaultUiState

    data class Failed(val message: String) : CreateVaultUiState
}

sealed interface NfcCommand {
    data class Write(val payload: ByteArray) : NfcCommand
    data object Read : NfcCommand
}

class CreateVaultViewModel(
    private val createVaultUseCase: CreateVaultUseCase,
    private val unlockVaultUseCase: UnlockVaultUseCase,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _form = MutableStateFlow(CreateVaultFormState())
    val form: StateFlow<CreateVaultFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<CreateVaultUiState>(CreateVaultUiState.Editing)
    val uiState: StateFlow<CreateVaultUiState> = _uiState.asStateFlow()

    private var currentSession: CreatedVaultSession? = null
    private val writtenPayloadIndexes = mutableSetOf<Int>()
    private val scannedPayloads = mutableListOf<ByteArray>()

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
            _uiState.value = runCatching {
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
                    writtenPayloadIndexes.clear()
                    scannedPayloads.clear()
                    CreateVaultUiState.WritingCards(
                        summary = session.summary,
                        writtenCount = 0,
                        total = session.cardPayloads.size,
                        nextCardNumber = 1,
                        message = "请贴近第 1 张空白 NDEF 标签。",
                    )
                },
                onFailure = { CreateVaultUiState.Failed(UserSafeCreateError) },
            )
        }
    }

    fun nextNfcCommand(): NfcCommand? {
        val session = currentSession ?: return null
        return when (_uiState.value) {
            is CreateVaultUiState.WritingCards -> {
                val index = nextWriteIndex(session) ?: return null
                NfcCommand.Write(session.cardPayloads[index])
            }

            is CreateVaultUiState.ReadingCards -> NfcCommand.Read
            else -> null
        }
    }

    fun onNfcResult(result: NfcCardResult) {
        when (val state = _uiState.value) {
            is CreateVaultUiState.WritingCards -> handleWriteResult(state, result)
            is CreateVaultUiState.ReadingCards -> handleReadResult(state, result)
            else -> Unit
        }
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
        _uiState.value = if (nextIndex == null) {
            CreateVaultUiState.ReadingCards(
                summary = session.summary,
                readCount = 0,
                threshold = session.summary.threshold,
                message = "写卡完成。请刷入任意 ${session.summary.threshold} 张卡解锁。",
            )
        } else {
            CreateVaultUiState.WritingCards(
                summary = session.summary,
                writtenCount = writtenPayloadIndexes.size,
                total = session.cardPayloads.size,
                nextCardNumber = nextIndex + 1,
                message = "写入成功。请贴近第 ${nextIndex + 1} 张空白 NDEF 标签。",
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
        if (scannedPayloads.size >= state.threshold) {
            unlockScannedCards(state.summary)
        } else {
            _uiState.value = state.copy(
                readCount = scannedPayloads.size,
                message = "读取成功。请继续刷入卡片。",
            )
        }
    }

    private fun unlockScannedCards(summary: CreatedVaultSummary) {
        val payloads = scannedPayloads.map { it.copyOf() }
        viewModelScope.launch {
            _uiState.value = CreateVaultUiState.Unlocking(
                summary = summary,
                readCount = payloads.size,
                threshold = summary.threshold,
            )
            _uiState.value = runCatching {
                withContext(workDispatcher) {
                    unlockVaultUseCase(
                        vaultIdHex = summary.vaultIdHex,
                        password = _form.value.password,
                        cardPayloads = payloads,
                    )
                }
            }.fold(
                onSuccess = { unlocked -> CreateVaultUiState.Unlocked(summary, unlocked) },
                onFailure = { CreateVaultUiState.Failed(UnlockFailedMessage) },
            )
        }
    }

    private fun nextWriteIndex(session: CreatedVaultSession): Int? =
        session.cardPayloads.indices.firstOrNull { it !in writtenPayloadIndexes }

    private fun nfcMessage(result: NfcCardResult): String = when (result) {
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
        private val unlockVaultUseCase: UnlockVaultUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateVaultViewModel::class.java))
            return CreateVaultViewModel(createVaultUseCase, unlockVaultUseCase) as T
        }
    }

    companion object {
        private const val UserSafeCreateError = "无法创建保险库。请检查主密码、设备密钥或本地数据状态。"
        private const val UnlockFailedMessage = "密码错误、卡片不属于该保险库，或数据已损坏。"
    }
}
