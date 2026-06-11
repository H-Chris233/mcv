package app.multicardvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.multicardvault.core.RustMcvCore
import app.multicardvault.data.McvDatabase
import app.multicardvault.data.RoomVaultRepository
import app.multicardvault.features.create.CreateVaultFormState
import app.multicardvault.features.create.CreateVaultUiState
import app.multicardvault.features.create.CreateVaultUseCase
import app.multicardvault.features.create.CreateVaultViewModel
import app.multicardvault.security.KeystoreDeviceSecretRepository

object McvAppIdentity {
    const val Name = "Multi-Card Vault"
    const val Status = "Experimental and unaudited"
    const val Purpose = "Local-first multi-card threshold encrypted vault"
}

class MainActivity : ComponentActivity() {
    private val database: McvDatabase by lazy {
        McvDatabase.open(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = database.vaultDao()
        val createVaultUseCase = CreateVaultUseCase(
            core = RustMcvCore(),
            vaultRepository = RoomVaultRepository(dao),
            deviceSecretRepository = KeystoreDeviceSecretRepository(dao),
        )
        val factory = CreateVaultViewModel.Factory(createVaultUseCase)

        setContent {
            MultiCardVaultApp(factory)
        }
    }
}

@Composable
fun MultiCardVaultApp(
    viewModelFactory: ViewModelProvider.Factory? = null,
) {
    val viewModel: CreateVaultViewModel = viewModel(factory = viewModelFactory)
    val form by viewModel.form.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                CreateVaultStatus(uiState)
            }
        }
    }
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
private fun CreateVaultStatus(uiState: CreateVaultUiState) {
    when (uiState) {
        CreateVaultUiState.Editing -> Box(modifier = Modifier.fillMaxWidth())
        CreateVaultUiState.Creating -> Text("正在调用 Rust core 生成 Vault Blob 和 Card Payload。")
        is CreateVaultUiState.Failed -> Text(uiState.message)
        is CreateVaultUiState.Created -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Vault 已创建", style = MaterialTheme.typography.titleMedium)
                Text("名称：${uiState.summary.displayName}")
                Text("Vault ID：${uiState.summary.vaultIdHex.take(16)}...")
                Text("门限：${uiState.summary.threshold}-of-${uiState.summary.total}")
                Text("待写入卡片 payload：${uiState.summary.cardPayloadCount}")
            }
        }
    }
}
