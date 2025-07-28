@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.icdcyborg.mavenDependencyTracker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.icdcyborg.mavenDependencyTracker.data.MavenRemoteDataSource
import net.icdcyborg.mavenDependencyTracker.data.PomCache
import net.icdcyborg.mavenDependencyTracker.data.PomDataSource
import net.icdcyborg.mavenDependencyTracker.data.PomParser
import net.icdcyborg.mavenDependencyTracker.domain.DependencyRepositoryImpl
import net.icdcyborg.mavenDependencyTracker.util.highlightPomXml

/**
 * アプリケーションのメインアクティビティです。
 * このアクティビティは、UIの表示とユーザー操作の受付を担当します。
 */
class MainActivity : ComponentActivity() {
    @Suppress("UNCHECKED_CAST")
    private val viewModel: MainViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                // TODO: DIライブラリを導入し、Repositoryのインスタンスを注入するように修正する
                return MainViewModel(
                    DependencyRepositoryImpl(
                        PomDataSource(
                            PomCache(),
                            MavenRemoteDataSource(HttpClient(CIO)),
                        ),
                        PomParser(),
                    ),
                ) as T
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

@Suppress("ktlint:standard:function-naming")
/**
 * アプリケーションのメイン画面（UI）です。
 * `MainViewModel` からUIの状態を受け取り、画面を描画します。
 * ユーザーの操作（ボタンクリックなど）を `MainViewModel` に通知します。
 *
 * @param viewModel メイン画面のViewModel。
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val mavenCoordinateInput by viewModel.mavenCoordinateInput.collectAsState() // ViewModelから入力ステートを取得
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.copyEvent.collect { textToCopy ->
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("Resolved Dependencies", textToCopy)
            clipboardManager.setPrimaryClip(clipData)
            snackbarHostState.showSnackbar(
                message = "Result Copied!",
                duration = SnackbarDuration.Short,
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = mavenCoordinateInput, // ViewModelのステートをvalueに設定
                    onValueChange = { newValue ->
                        viewModel.onMavenCoordinateInputChange(newValue) // ユーザーの入力をViewModelに通知してステートを更新
                    },
                    label = { Text("Maven Coordinate") },
                    placeholder = { Text("ex) org.example:example:v1.0") },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isResolving,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.startResolution() },
                    enabled = !uiState.isResolving,
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
                    onDismissRequest = { viewModel.clearError() },
                    title = { Text("Error") },
                    text = { Text(it) },
                    confirmButton = {
                        Button(onClick = { viewModel.clearError() }) {
                            Text("OK")
                        }
                    },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp, end = 8.dp)) {
                    items(uiState.resolvedDependencies) {
                        Text(
                            text = it,
                            modifier =
                                Modifier.fillMaxWidth().pointerInput(Unit) {
                                    detectTapGestures(onLongPress = { _ -> viewModel.onDependencyLongClicked(it) })
                                },
                        )
                    }
                }

                // Copy button
                if (uiState.resolvedDependencies.isNotEmpty() && !uiState.isResolving) {
                    IconButton(
                        onClick = { viewModel.onCopyClicked() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
                    ) {
                        Icon(painterResource(id = R.drawable.copy), contentDescription = "Copy")
                    }
                }
            }

            // POM Dialog
            if (uiState.showPomDialog && uiState.pomContent != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissPomDialog() },
                    title = { Text("POM Content") },
                    text = {
                        Column {
                            SelectionContainer {
                                Text(text = highlightPomXml(uiState.pomContent!!), modifier = Modifier.verticalScroll(rememberScrollState()))
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.onDismissPomDialog() }) {
                            Text("Close")
                        }
                    },
                )
            }
        }
    }
}
