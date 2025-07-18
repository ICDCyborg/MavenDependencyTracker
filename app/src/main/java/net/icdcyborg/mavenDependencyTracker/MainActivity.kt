package net.icdcyborg.mavenDependencyTracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import net.icdcyborg.mavenDependencyTracker.data.PomParser
import net.icdcyborg.mavenDependencyTracker.data.MavenRemoteDataSource
import net.icdcyborg.mavenDependencyTracker.data.PomCache
import net.icdcyborg.mavenDependencyTracker.domain.DependencyRepositoryImpl

/**
 * アプリケーションのメインアクティビティです。
 * このアクティビティは、UIの表示とユーザー操作の受付を担当します。
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                // TODO: DIライブラリを導入し、Repositoryのインスタンスを注入するように修正する
                return MainViewModel(DependencyRepositoryImpl(
                    PomCache(),
                    MavenRemoteDataSource(HttpClient(CIO)),
                    PomParser()
                )) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(viewModel)
            }
        }
    }
}

/**
 * アプリケーションのメイン画面（UI）です。
 * `MainViewModel` からUIの状態を受け取り、画面を描画します。
 * ユーザーの操作（ボタンクリックなど）を `MainViewModel` に通知します。
 *
 * @param viewModel メイン画面のViewModel。
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Maven Coordinate") },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isResolving
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.startResolution("") },
                enabled = !uiState.isResolving
            ) {
                Text("Resolve")
            }
        }

        if (uiState.isResolving) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resolving dependencies...")
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.cancelResolution() }) {
                    Text("Cancel")
                }
            }
        }

        uiState.error?.let {
            AlertDialog(
                onDismissRequest = { /* TODO */ },
                title = { Text("Error") },
                text = { Text(it) },
                confirmButton = {
                    Button(onClick = { /* TODO */ }) {
                        Text("OK")
                    }
                }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.resolvedDependencies) {
                Text(it)
            }
        }
    }
}