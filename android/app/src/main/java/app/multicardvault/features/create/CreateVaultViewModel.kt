package app.multicardvault.features.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    data class Created(val summary: CreatedVaultSummary) : CreateVaultUiState
    data class Failed(val message: String) : CreateVaultUiState
}

class CreateVaultViewModel(
    private val createVaultUseCase: CreateVaultUseCase,
) : ViewModel() {
    private val _form = MutableStateFlow(CreateVaultFormState())
    val form: StateFlow<CreateVaultFormState> = _form.asStateFlow()

    private val _uiState = MutableStateFlow<CreateVaultUiState>(CreateVaultUiState.Editing)
    val uiState: StateFlow<CreateVaultUiState> = _uiState.asStateFlow()

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
                withContext(Dispatchers.Default) {
                    createVaultUseCase(
                        displayName = snapshot.displayName,
                        password = snapshot.password,
                        threshold = snapshot.threshold,
                        total = snapshot.total,
                    )
                }
            }.fold(
                onSuccess = { CreateVaultUiState.Created(it) },
                onFailure = { CreateVaultUiState.Failed(UserSafeCreateError) },
            )
        }
    }

    private fun resetFailure() {
        if (_uiState.value is CreateVaultUiState.Failed) {
            _uiState.value = CreateVaultUiState.Editing
        }
    }

    class Factory(
        private val createVaultUseCase: CreateVaultUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateVaultViewModel::class.java))
            return CreateVaultViewModel(createVaultUseCase) as T
        }
    }

    companion object {
        private const val UserSafeCreateError = "无法创建保险库。请检查主密码、设备密钥或本地数据状态。"
    }
}
