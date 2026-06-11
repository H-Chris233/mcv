package app.multicardvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object McvAppIdentity {
    const val Name = "Multi-Card Vault"
    const val Status = "Experimental and unaudited"
    const val Purpose = "Local-first multi-card threshold encrypted vault"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MultiCardVaultApp()
        }
    }
}

@Composable
fun MultiCardVaultApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = McvAppIdentity.Name,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = McvAppIdentity.Status,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = McvAppIdentity.Purpose,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
