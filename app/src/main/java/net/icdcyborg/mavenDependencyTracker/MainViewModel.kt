package net.icdcyborg.mavenDependencyTracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.launch
import net.icdcyborg.mavenDependencyTracker.domain.DependencyRepository

/**
 * UIの状態を表すデータクラスです。
 *
 * @property isResolving 依存関係の解決中であるかを示します。
 * @property resolvedDependencies 解決済みの依存関係のリストです。
 * @property error エラーメッセージです。エラーが発生していない場合はnullです。
 */
data class UiState(
    val isResolving: Boolean = false,
    val resolvedDependencies: List<String> = emptyList(),
    val error: String? = null,
    val pomContent: String? = null,
    val showPomDialog: Boolean = false,
)

/**
 * メイン画面のビジネスロジックを担当するViewModelです。
 * UIの状態を管理し、ユーザー操作に応じて依存関係の解決処理を開始・中断します。
 *
 * @property dependencyRepository 依存関係を解決するためのリポジトリです。
 */
class MainViewModel(
    private val dependencyRepository: DependencyRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var resolutionJob: Job? = null

    // 入力された座標の文字列を保持する
    private val _mavenCoordinateInput = MutableStateFlow("")
    val mavenCoordinateInput: StateFlow<String> = _mavenCoordinateInput.asStateFlow()

    /**
     * 入力欄の更新に伴って文字列を受け取る
     *
     * @param newInput Maven座標 (例: "group:artifact:version")。
     */
    fun onMavenCoordinateInputChange(newInput: String) {
        _mavenCoordinateInput.value = newInput
    }

    /**
     * 指定されたMaven座標の依存関係解決を開始します。
     */
    fun startResolution() {
        val coordinate = _mavenCoordinateInput.value
        val regex = "^[a-zA-Z0-9.-]+:[a-zA-Z0-9.-]+:[a-zA-Z0-9.-]+".toRegex()
        if (!regex.matches(coordinate)) {
            _uiState.value = _uiState.value.copy(error = "入力形式が正しくありません (例: group:artifact:version)")
            return
        }

        resolutionJob?.cancel()
        _uiState.value = UiState(isResolving = true)

        resolutionJob =
            viewModelScope.launch {
                dependencyRepository
                    .resolveDependencies(coordinate)
                    .catch { e ->
                        _uiState.value = _uiState.value.copy(error = e.message)
                    }.onCompletion {
                        println(_uiState.value.resolvedDependencies)
                        val dependenciesWithJar =
                            _uiState.value.resolvedDependencies.map {
                                if (dependencyRepository.checkJarExists(it)) {
                                    "$it (jar)"
                                } else {
                                    it
                                }
                            }
                        println(dependenciesWithJar)
                        _uiState.value =
                            _uiState.value.copy(
                                isResolving = false,
                                resolvedDependencies = dependenciesWithJar,
                            )
                        println("complete")
                    }.collect {
                        _uiState.value =
                            _uiState.value.copy(
                                resolvedDependencies = _uiState.value.resolvedDependencies + it,
                            )
                    }
            }
    }

    /**
     * 進行中の依存関係解決を中断します。
     */
    fun cancelResolution() {
        resolutionJob?.cancel()
    }

    /**
     * UIに表示されているエラーをクリアします。
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private val _copyEvent = MutableSharedFlow<String>()
    val copyEvent: SharedFlow<String> = _copyEvent.asSharedFlow()

    private val _pomContent = MutableStateFlow<String?>(null)
    val pomContent: StateFlow<String?> = _pomContent.asStateFlow()

    private val _showPomDialog = MutableStateFlow(false)
    val showPomDialog: StateFlow<Boolean> = _showPomDialog.asStateFlow()

    /**
     * 解決済みの依存関係をクリップボードにコピーするイベントを発生させます。
     * 依存関係が解決されており、かつ解決中でない場合にのみ実行されます。
     */
    fun onCopyClicked() {
        viewModelScope.launch {
            if (_uiState.value.resolvedDependencies.isNotEmpty() && !_uiState.value.isResolving) {
                val textToCopy = _uiState.value.resolvedDependencies.joinToString("\n")
                _copyEvent.emit(textToCopy)
            }
        }
    }

    /**
     * 依存関係の項目がロングタップされたときにPOMの内容を表示します。
     *
     * @param dependency ロングタップされた依存関係の文字列。
     */
    fun onDependencyLongClicked(dependency: String) {
        val cleanedDependency = dependency.replace(" (jar)", "")
        val regex = "^[a-zA-Z0-9.-]+:[a-zA-Z0-9.-]+:[a-zA-Z0-9.-]+".toRegex()
        if (!regex.matches(cleanedDependency)) {
            // 不正な文字列の場合は何もしない
            return
        }

        viewModelScope.launch {
            try {
                val pom = dependencyRepository.getPom(cleanedDependency).singleOrNull()
                if (pom != null) {
                    _uiState.value = _uiState.value.copy(pomContent = pom, showPomDialog = true)
                } else {
                    // Pomが取得できなかった場合は何もしない
                }
            } catch (e: Exception) {
                // エラーが発生した場合は何もしない
            }
        }
    }

    /**
     * POM表示ダイアログを閉じます。
     */
    fun onDismissPomDialog() {
        _uiState.value = _uiState.value.copy(showPomDialog = false, pomContent = null)
    }
}
