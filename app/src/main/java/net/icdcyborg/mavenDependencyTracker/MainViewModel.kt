package net.icdcyborg.mavenDependencyTracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
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
    val error: String? = null
)

/**
 * メイン画面のビジネスロジックを担当するViewModelです。
 * UIの状態を管理し、ユーザー操作に応じて依存関係の解決処理を開始・中断します。
 *
 * @property dependencyRepository 依存関係を解決するためのリポジトリです。
 */
class MainViewModel(private val dependencyRepository: DependencyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var resolutionJob: Job? = null

    /**
     * 指定されたMaven座標の依存関係解決を開始します。
     *
     * @param coordinate Maven座標 (例: "group:artifact:version")。
     */
    fun startResolution(coordinate: String) {
        val regex = "^[a-zA-Z0-9\\.\\-]+:[a-zA-Z0-9\\.\\-]+:[a-zA-Z0-9\\.\\-]+".toRegex()
        if (!regex.matches(coordinate)) {
            _uiState.value = _uiState.value.copy(error = "入力形式が正しくありません (例: group:artifact:version)")
            return
        }

        resolutionJob?.cancel()
        _uiState.value = UiState(isResolving = true)

        resolutionJob = viewModelScope.launch {
            dependencyRepository.resolveDependencies(coordinate)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
                .onCompletion {
                    _uiState.value = _uiState.value.copy(isResolving = false)
                }
                .collect {
                    _uiState.value = _uiState.value.copy(
                        resolvedDependencies = _uiState.value.resolvedDependencies + it
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
}