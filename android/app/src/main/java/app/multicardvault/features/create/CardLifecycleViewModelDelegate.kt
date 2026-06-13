package app.multicardvault.features.create

import app.multicardvault.features.cards.CardVerificationTarget
import app.multicardvault.features.cards.InspectCardUseCase
import app.multicardvault.features.cards.InterruptedReissueRecoveryResult
import app.multicardvault.features.cards.RecoverInterruptedReissueUseCase
import app.multicardvault.features.cards.ScannedCardPayload
import app.multicardvault.features.cards.StartCardSetReissueUseCase
import app.multicardvault.features.cards.VerifyCardSetUseCase
import app.multicardvault.nfc.NfcCardResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CardLifecycleViewModelDelegate(
    private val inspectCardUseCase: InspectCardUseCase,
    private val verifyCardSetUseCase: VerifyCardSetUseCase,
    private val startCardSetReissueUseCase: StartCardSetReissueUseCase,
    private val recoverInterruptedReissueUseCase: RecoverInterruptedReissueUseCase,
    private val workDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val getState: () -> CreateVaultUiState,
    private val setState: (CreateVaultUiState) -> Unit,
    private val getForm: () -> CreateVaultFormState,
    private val setCurrentSession: (CreatedVaultSession) -> Unit,
    private val setPendingUnlockedAfterRewrite: (CreateVaultUiState.Unlocked?) -> Unit,
    private val clearWrittenPayloadIndexes: () -> Unit,
    private val canStartCardReading: () -> Boolean,
    private val unlockScannedCards: (CreatedVaultSummary) -> Unit,
    private val scannedPayloads: MutableList<ByteArray>,
    private val clock: () -> Long,
) {
    private val interruptedScans = mutableListOf<ScannedCardPayload>()

    fun startVerifyCurrentCard() {
        val state = getState() as? CreateVaultUiState.Unlocked ?: return
        setState(
            CreateVaultUiState.VerifyingCard(
                summary = state.summary,
                currentSchemeIdHex = state.unlocked.schemeIdHex,
                scannedShareIndexes = emptySet(),
                message = "请贴近要校验的 CUID 卡。",
            ),
        )
    }

    fun startCardSetReissue() {
        val state = getState() as? CreateVaultUiState.Unlocked ?: return
        val payloads = scannedPayloads.map { it.copyOf() }
        if (payloads.size < state.unlocked.threshold) {
            setState(state.copy(message = "当前会话没有足够卡片 payload，需重新刷卡解锁后再重发。"))
            return
        }
        scope.launch {
            setState(state.copy(isSaving = true, message = "正在生成新的卡组。"))
            setState(
                runCatching {
                    withContext(workDispatcher) {
                        startCardSetReissueUseCase(
                            vaultIdHex = state.summary.vaultIdHex,
                            password = getForm().password,
                            cardPayloads = payloads,
                            entries = state.unlocked.entries,
                            updatedAt = clock(),
                        )
                    }
                }.fold(
                    onSuccess = { reissue ->
                        setCurrentSession(
                            CreatedVaultSession(
                                summary = state.summary,
                                cardPayloads = reissue.cardPayloads,
                            ),
                        )
                        setPendingUnlockedAfterRewrite(state.copy(isSaving = false, message = "卡组已重发。"))
                        clearWrittenPayloadIndexes()
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
                ),
            )
        }
    }

    fun startInterruptedReissueRecovery() {
        val form = getForm()
        if (form.password.isEmpty()) {
            setState(CreateVaultUiState.Failed("请输入主密码后再恢复中断重发。"))
            return
        }
        if (!canStartCardReading()) return
        setCurrentSession(
            CreatedVaultSession(
                summary =
                    CreatedVaultSummary(
                        vaultIdHex = "",
                        displayName = "Recovered Vault",
                        threshold = form.threshold,
                        total = form.total,
                        cardPayloadCount = 0,
                    ),
                cardPayloads = emptyList(),
            ),
        )
        interruptedScans.clear()
        scannedPayloads.clear()
        setState(
            CreateVaultUiState.RecoveringInterruptedReissue(
                readCount = 0,
                threshold = form.threshold,
                message = "请刷入中断重发前后的 CUID 卡，凑够任一卡组门限后自动解锁。",
            ),
        )
    }

    fun handleVerifyResult(
        state: CreateVaultUiState.VerifyingCard,
        result: NfcCardResult,
        nfcMessage: (NfcCardResult) -> String,
    ) {
        if (result !is NfcCardResult.Success) {
            setState(state.copy(message = nfcMessage(result)))
            return
        }

        scope.launch {
            setState(state.copy(message = "正在校验卡片。"))
            setState(
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
                ),
            )
        }
    }

    fun handleInterruptedRecoveryResult(
        state: CreateVaultUiState.RecoveringInterruptedReissue,
        result: NfcCardResult,
        nfcMessage: (NfcCardResult) -> String,
    ) {
        if (result !is NfcCardResult.Success) {
            setState(state.copy(message = nfcMessage(result)))
            return
        }

        scope.launch {
            setState(state.copy(message = "正在检查卡片所属卡组。"))
            setState(
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
                    onSuccess = ::interruptedRecoveryState,
                    onFailure = {
                        state.copy(message = "卡片不是有效的 MCV 卡片，请继续刷入。")
                    },
                ),
            )
        }
    }

    private fun interruptedRecoveryState(recovery: InterruptedReissueRecoveryResult): CreateVaultUiState =
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
}
