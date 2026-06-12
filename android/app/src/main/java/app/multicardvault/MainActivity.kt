package app.multicardvault

import android.os.Bundle
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.multicardvault.core.RustMcvCore
import app.multicardvault.data.McvDatabase
import app.multicardvault.data.RoomVaultRepository
import app.multicardvault.features.create.CreateVaultFormState
import app.multicardvault.features.create.NfcCommand
import app.multicardvault.features.create.CreateVaultUiState
import app.multicardvault.features.create.CreateVaultUseCase
import app.multicardvault.features.create.CreateVaultViewModel
import app.multicardvault.features.unlock.UnlockVaultUseCase
import app.multicardvault.features.vault.ListVaultsUseCase
import app.multicardvault.features.vault.SavedVaultSummary
import app.multicardvault.features.vault.UpdateVaultUseCase
import app.multicardvault.nfc.NdefNfcRepository
import app.multicardvault.nfc.NfcRepository
import app.multicardvault.security.KeystoreDeviceSecretRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object McvAppIdentity {
    const val Name = "Multi-Card Vault"
    const val Status = "Experimental and unaudited"
    const val Purpose = "Local-first multi-card threshold encrypted vault"
}

class MainActivity : ComponentActivity() {
    private val database: McvDatabase by lazy {
        McvDatabase.open(applicationContext)
    }

    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(this)
    }

    private val nfcRepository: NfcRepository by lazy {
        NdefNfcRepository()
    }

    private val nfcBusy = AtomicBoolean(false)

    private val viewModel: CreateVaultViewModel by lazy {
        val dao = database.vaultDao()
        val core = RustMcvCore()
        val vaultRepository = RoomVaultRepository(dao)
        val deviceSecretRepository = KeystoreDeviceSecretRepository(dao)
        val factory = CreateVaultViewModel.Factory(
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
        )
        ViewModelProvider(this, factory)[CreateVaultViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultiCardVaultApp(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.takeIf { it.isEnabled }?.enableReaderMode(
            this,
            ::onTagDiscovered,
            NfcReaderFlags,
            null,
        )
    }

    override fun onPause() {
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }

    private fun onTagDiscovered(tag: Tag) {
        if (!nfcBusy.compareAndSet(false, true)) return

        lifecycleScope.launch {
            try {
                val command = viewModel.nextNfcCommand() ?: return@launch
                val result = withContext(Dispatchers.IO) {
                    when (command) {
                        NfcCommand.Read -> nfcRepository.readPayload(tag)
                        is NfcCommand.Write -> nfcRepository.writePayload(tag, command.payload)
                    }
                }
                viewModel.onNfcResult(result)
            } finally {
                nfcBusy.set(false)
            }
        }
    }

    companion object {
        private const val NfcReaderFlags =
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V
    }
}

@Composable
fun MultiCardVaultApp(
    viewModel: CreateVaultViewModel,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedVaults by viewModel.savedVaults.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Header()
                CreateVaultForm(
                    form = form,
                    uiState = uiState,
                    onDisplayNameChange = viewModel::updateDisplayName,
                    onPasswordChange = viewModel::updatePassword,
                    onCreateClick = viewModel::createVault,
                )
                SavedVaultList(
                    vaults = savedVaults,
                    password = form.password,
                    uiState = uiState,
                    onUnlockClick = viewModel::startUnlockSavedVault,
                )
                CreateVaultStatus(
                    uiState = uiState,
                    onEntryTitleChange = viewModel::updateEntryTitle,
                    onEntryContentChange = viewModel::updateEntryContent,
                    onSaveEntry = viewModel::saveEntryDraft,
                    onEditEntry = viewModel::editEntry,
                    onDeleteEntry = viewModel::deleteEntry,
                    onCancelEntryEdit = viewModel::cancelEntryEdit,
                )
            }
        }
    }
}

@Composable
private fun SavedVaultList(
    vaults: List<SavedVaultSummary>,
    password: String,
    uiState: CreateVaultUiState,
    onUnlockClick: (SavedVaultSummary) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("已有保险库", style = MaterialTheme.typography.titleLarge)
            if (vaults.isEmpty()) {
                Text("本机暂无已保存的 Vault。")
            } else {
                vaults.forEach { vault ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(vault.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("Vault ID：${vault.vaultIdHex.take(16)}...")
                            Text("门限：${vault.threshold}-of-${vault.total}")
                            Button(
                                onClick = { onUnlockClick(vault) },
                                enabled = password.isNotEmpty() && canStartSavedVaultUnlock(uiState),
                            ) {
                                Text("刷卡解锁")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun canStartSavedVaultUnlock(uiState: CreateVaultUiState): Boolean = when (uiState) {
    CreateVaultUiState.Editing -> true
    is CreateVaultUiState.Failed -> true
    is CreateVaultUiState.Unlocked -> true
    CreateVaultUiState.Creating,
    is CreateVaultUiState.ReadingCards,
    is CreateVaultUiState.Unlocking,
    is CreateVaultUiState.WritingCards -> false
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = McvAppIdentity.Name,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "实验性，未审计",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "本地优先，多卡门限加密保险库。不用于复制、绕过或读取未授权第三方卡片。",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CreateVaultForm(
    form: CreateVaultFormState,
    uiState: CreateVaultUiState,
    onDisplayNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCreateClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "创建本地保险库",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = form.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("保险库名称") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = form.password,
                onValueChange = onPasswordChange,
                label = { Text("主密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("门限方案")
                Text("${form.threshold}-of-${form.total}")
            }
            Button(
                onClick = onCreateClick,
                enabled = form.password.isNotEmpty() && uiState != CreateVaultUiState.Creating,
            ) {
                Text(if (uiState == CreateVaultUiState.Creating) "创建中..." else "创建 Vault")
            }
        }
    }
}

@Composable
private fun CreateVaultStatus(
    uiState: CreateVaultUiState,
    onEntryTitleChange: (String) -> Unit,
    onEntryContentChange: (String) -> Unit,
    onSaveEntry: () -> Unit,
    onEditEntry: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onCancelEntryEdit: () -> Unit,
) {
    when (uiState) {
        CreateVaultUiState.Editing -> Box(modifier = Modifier.fillMaxWidth())
        CreateVaultUiState.Creating -> Text("正在调用 Rust core 生成 Vault Blob 和 Card Payload。")
        is CreateVaultUiState.Failed -> Text(uiState.message)
        is CreateVaultUiState.WritingCards -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Vault 已创建", style = MaterialTheme.typography.titleMedium)
                Text("名称：${uiState.summary.displayName}")
                Text("Vault ID：${uiState.summary.vaultIdHex.take(16)}...")
                Text("门限：${uiState.summary.threshold}-of-${uiState.summary.total}")
                Text("写卡进度：${uiState.writtenCount} / ${uiState.total}")
                Text("下一张卡：${uiState.nextCardNumber}")
                Text(uiState.message)
            }
        }

        is CreateVaultUiState.ReadingCards -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("读取卡片解锁", style = MaterialTheme.typography.titleMedium)
                Text("Vault ID：${uiState.summary.vaultIdHex.take(16)}...")
                Text("读取进度：${uiState.readCount} / ${uiState.threshold}")
                Text(uiState.message)
            }
        }

        is CreateVaultUiState.Unlocking -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("正在解锁", style = MaterialTheme.typography.titleMedium)
                Text("读取进度：${uiState.readCount} / ${uiState.threshold}")
                Text("正在调用 Rust core 恢复密钥并解密 Vault Blob。")
            }
        }

        is CreateVaultUiState.Unlocked -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Vault 已解锁", style = MaterialTheme.typography.titleMedium)
                Text("Vault ID：${uiState.unlocked.vaultIdHex.take(16)}...")
                Text("明文结构大小：${uiState.unlocked.plaintextSize} 字节")
                Text("条目数量：${uiState.unlocked.entries.size}")
                if (uiState.unlocked.entries.isEmpty()) {
                    Text("当前 Vault 为空。")
                }
                uiState.unlocked.entries.forEach { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            Text(entry.content.ifBlank { "无内容" })
                            Text("Entry ID：${entry.idHex.take(12)}...")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onEditEntry(entry.idHex) },
                                    enabled = !uiState.isSaving,
                                ) {
                                    Text("编辑")
                                }
                                Button(
                                    onClick = { onDeleteEntry(entry.idHex) },
                                    enabled = !uiState.isSaving,
                                ) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.draft.title,
                    onValueChange = onEntryTitleChange,
                    label = { Text(if (uiState.draft.isEditing) "编辑标题" else "新条目标题") },
                    singleLine = true,
                    enabled = !uiState.isSaving,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.draft.content,
                    onValueChange = onEntryContentChange,
                    label = { Text(if (uiState.draft.isEditing) "编辑内容" else "新条目内容") },
                    minLines = 3,
                    enabled = !uiState.isSaving,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSaveEntry,
                        enabled = !uiState.isSaving,
                    ) {
                        Text(
                            when {
                                uiState.isSaving -> "保存中..."
                                uiState.draft.isEditing -> "保存修改"
                                else -> "添加条目"
                            },
                        )
                    }
                    if (uiState.draft.isEditing) {
                        Button(
                            onClick = onCancelEntryEdit,
                            enabled = !uiState.isSaving,
                        ) {
                            Text("取消编辑")
                        }
                    }
                }
                uiState.message?.let { Text(it) }
            }
        }
    }
}
