# CLASSES.md: Maven Dependency Tracker

## クラス設計

### 1. UI Layer

#### 1.1. `MainActivity.kt`

- **クラス定義:** `class MainActivity : AppCompatActivity()`
- **責務:**
    - UIコンポーネント（入力欄、ボタン、プログレスバー、結果リスト）のレイアウトと操作。
    - `MainViewModel` の `UiState` を監視し、リアルタイムにUIを更新する。
    - 検索中は入力欄と検索ボタンを非活性化し、プログレスバーと中断ボタンを表示する。
    - 検索ボタンのクリックや中断ボタンのクリックといったユーザーイベントを `ViewModel` に通知する。
- **依存関係:**
    - `MainViewModel`
- **主要なメソッド:**
    - `onCreate(savedInstanceState: Bundle?)`: 初期UI設定、`ViewModel`の初期化、UIイベントリスナーの設定、`UiState`の監視開始。
    - `setupListeners()`: 検索ボタンと中断ボタンのクリックリスナーを設定。
    - `observeUiState(uiStateFlow: StateFlow<UiState>)`: `uiStateFlow` を収集し、UIを更新する。
    - `showErrorDialog(message: String)`: エラーメッセージを表示するダイアログ。

#### 1.2. `MainViewModel.kt`

- **クラス定義:** `class MainViewModel(private val dependencyRepository: DependencyRepository) : ViewModel()`
- **責務:**
    - UIの状態 (`UiState`) を `StateFlow` として公開する。
    - 検索ロジックを実行するコルーチンジョブを管理し、中断要求に応じてキャンセルする。
    - `DependencyRepository` を呼び出し、結果を `Flow` として受け取る。
    - 受け取った依存関係を `UiState` のリストに順次追加していく。
    - 入力値の検証や、エラー発生時のメッセージを生成し `UiState` を更新する。
- **依存関係:**
    - `DependencyRepository`
- **主要なプロパティ:**
    - `private val _uiState = MutableStateFlow(UiState())`
    - `val uiState: StateFlow<UiState> = _uiState.asStateFlow()`
    - `private var resolutionJob: Job? = null`
- **主要なメソッド:**
    - `startResolution(coordinate: String)`:
        - 入力値の正規表現バリデーション (`^[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+$`)。
        - バリデーション失敗時は `UiState` にエラーメッセージを設定。
        - `_uiState.value = _uiState.value.copy(isResolving = true, error = null, resolvedDependencies = emptyList())`
        - `viewModelScope.launch` で `resolutionJob` を開始し、`dependencyRepository.resolveDependencies(coordinate).collect` を呼び出す。
        - `collect` 内で `_uiState.value = _uiState.value.copy(resolvedDependencies = _uiState.value.resolvedDependencies + it)` を実行。
        - `onCompletion` または `catch` ブロックで `_uiState.value = _uiState.value.copy(isResolving = false)` を実行。
        - エラー発生時は `_uiState.value = _uiState.value.copy(error = errorMessage)` を実行。
    - `cancelResolution()`: `resolutionJob?.cancel()` を呼び出し、`isResolving` を `false` に更新。
- **データクラス `UiState`:**
    ```kotlin
    data class UiState(
        val isResolving: Boolean = false, // 検索中か否か
        val resolvedDependencies: List<String> = emptyList(),
        val error: String? = null
    )
    ```
- **エラーメッセージ例:**
    - `"入力形式が正しくありません (例: group:artifact:version)"`
    - `"POMファイルの取得に失敗しました: (URL)"`
    - `"POMファイルの解析に失敗しました: (ライブラリ座標)"`
    - `"必須情報 (groupId, artifactId, version) が見つかりませんでした: (ライブラリ座標)"`

### 2. Domain Layer

#### 2.1. `DependencyRepository.kt` (Interface)

- **インターフェース定義:** `interface DependencyRepository`
- **責務:** 依存関係解決のコアロジックを抽象化する。
- **主要なメソッド:**
    - `fun resolveDependencies(coordinate: String): Flow<String>`

#### 2.2. `DependencyRepositoryImpl.kt` (Implementation)

- **クラス定義:** `class DependencyRepositoryImpl(
    private val pomCache: PomCache,
    private val mavenRemoteDataSource: MavenRemoteDataSource,
    private val pomParser: PomParser
) : DependencyRepository`
- **責務:**
    - 指定されたライブラリ座標を最初の結果として含め、推移的依存関係を再帰的に解決する。
    - 依存関係を一つ解決するごとに、結果を `Flow` としてリアルタイムに通知する。
    - プロパティ解決: 親POMを再帰的に遡り、複数階層にわたるプロパティ (`${...}`) を解決する。
    - `scope` が `test` `provided`、または `optional` が `true` の依存を除外する。
    - 解決済みのライブラリを管理し、循環参照を防止する。
- **依存関係:**
    - `PomCache`
    - `MavenRemoteDataSource`
    - `PomParser`
- **主要なメソッド:**
    - `override fun resolveDependencies(coordinate: String): Flow<String>`:
        - `flow { ... }` ビルダーを使用。
        - 内部で `resolveRecursive` メソッドを呼び出す。
    - `private suspend fun resolveRecursive(coordinate: String,
                                         resolved: MutableSet<String>,
                                         parentPomData: PomData? = null): Flow<String>`:
        - `resolved` セットで循環参照を防止。
        - キャッシュに存在するか確認し、なければ `MavenRemoteDataSource` と `PomParser` を使用。
        - 取得した `PomData` をキャッシュに保存。
        - `PomData` から基本情報を抽出し、プロパティ解決を行う。
        - `dependencyManagement` を参照してバージョンを解決する。
        - フィルタリングルール (`optional`, `scope`) に基づいて依存関係を処理。
        - 親POMが存在する場合、その親POMも再帰的に解決対象とする。
        - 各解決済み依存関係を `emit` する。
        - 未解決のプロパティやバージョンがある場合、警告ログを出力し、それ以上の推移的依存関係の追跡を行わない。
    - `private fun resolveProperties(value: String, pomData: PomData, parentPomData: PomData?): String`:
        - `${...}` 形式のプロパティを解決するロジック。
        - `project.properties`, `project.parent.properties`、および予約済みプロパティ (`project.groupId`, `project.version`) を参照。
        - プロパティが解決できない場合はそのままの文字列を返す。
    - `private fun resolveVersionFromDependencyManagement(dependency: Dependency, pomData: PomData): String`:
        - `dependencyManagement` セクションからバージョンを解決するロジック。
        - 解決できない場合は `"ERROR"` を返す。
- **内部データ構造:**
    - `PomData` (データクラス) : 解析されたPOMファイルの情報（groupId, artifactId, version, parent, dependencies, propertiesなど）を保持する。
        - プロパティ: `groupId: String?`, `artifactId: String?`, `version: String?`, `parent: ParentData?`, `dependencies: List<Dependency>`, `properties: Map<String, String>`, `dependencyManagement: List<Dependency>`
    - `ParentData` (データクラス) : 親POMの情報（groupId, artifactId, version）を保持する。
    - `Dependency` (データクラス) : 依存関係の情報（groupId, artifactId, version, scope, optional）を保持する。

### 3. Data Layer

#### 3.1. `PomCache.kt`

- **クラス定義:** `class PomCache`
- **責務:** 一度解析に成功したPOMのデータ (`PomData`) をメモリ上にキャッシュする。キー（ライブラリ座標）と値（`PomData`）でデータを保持・提供する。
- **実装方針:** DI (Dependency Injection) を通じてシングルトンインスタンスとして提供する。
- **依存関係:** なし
- **主要なプロパティ:**
    - `private val cache = ConcurrentHashMap<String, PomData>()`
- **主要なメソッド:**
    - `fun get(coordinate: String): PomData?`: キャッシュから `PomData` を取得。
    - `fun put(coordinate: String, pomData: PomData)`: `PomData` をキャッシュに保存。
    - `fun contains(coordinate: String): Boolean`: キャッシュに存在するか確認。

#### 3.2. `MavenRemoteDataSource.kt`

- **クラス定義:** `class MavenRemoteDataSource(private val httpClient: HttpClient)`
- **責務:** ライブラリ座標からPOMファイルのURLを生成し、HTTP GETリクエストでXML文字列を取得する。リクエスト失敗時に1秒間隔で1回リトライする。
- **実装方針:** HTTPクライアントに **Ktor** を使用。`Result` 型で成功/失敗を返す `suspend` 関数を実装する。
- **依存関係:** `io.ktor:ktor-client-*`
- **主要なプロパティ:**
    - `private val BASE_URL = "https://repo1.maven.org/maven2/"`
- **主要なメソッド:**
    - `suspend fun getPomXml(coordinate: String): Result<String>`:
        - `coordinate` からPOMファイルのURLを生成するロジック (`(groupIdの.を/に置換)/(artifactId)/(version)/(artifactId)-(version).pom`)。
        - Ktor `HttpClient` を使用してHTTP GETリクエストを実行。
        - リクエスト失敗時、1秒間隔で1回リトライするロジック。
        - 成功した場合は `Result.success(xmlString)`、失敗した場合は `Result.failure(exception)` を返す。
        - HTTP通信の成否をデバッグログとして出力する。

#### 3.3. `PomParser.kt`

- **クラス定義:** `class PomParser`
- **責務:** POMのXML文字列を解析し、後続処理で必要な情報（基本情報、親、依存、プロパティ等）を抽出した `PomData` オブジェクトを生成する。
- **実装方針:** Android標準の `XmlPullParser` を使用し、軽量なパーサーを実装する。
- **依存関係:** なし (Android SDKの `XmlPullParser`)
- **主要なメソッド:**
    - `fun parse(xmlString: String): Result<PomData>`:
        - `XmlPullParser` を初期化し、XML文字列を解析。
        - `project.groupId`, `project.artifactId`, `project.version` (または `project.parent` から) を抽出。
        - `project.parent` が存在する場合、その情報を抽出。
        - `project.dependencies` 内の各 `dependency` タグを走査し、`Dependency` オブジェクトを生成。
        - `project.properties` 内のプロパティを抽出。
        - `project.dependencyManagement.dependencies` を抽出。
        - 必須情報（`groupId`, `artifactId`, `version`）が見つからない場合、エラーを返す。
        - 解析成功時は `Result.success(pomData)`、失敗時は `Result.failure(exception)` を返す。
        - XML解析の成否をデバッグログとして出力する。