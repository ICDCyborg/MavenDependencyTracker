# TODO.md: Maven Dependency Tracker 開発タスク

## 開発方針
- TDD (テスト駆動開発) と Outside-In (外側から内側へ) アプローチを採用する。
- 各コンポーネントは、まずテストを書き、そのテストが通るように実装を進める。

## 1. テスト環境構築
- [x] JUnit 5 のセットアップ
- [x] MockK のセットアップ
- [x] Turbine のセットアップ (Flowのテスト用)
- [x] Ktor Client Mock のセットアップ (HTTPリクエストのモック用)
- [x] JaCoCo のセットアップ (テストカバレッジ計測用)

## 2. UI Layer (外側から開始)

### 2.1. `MainActivity.kt`
- [x] `MainActivity` の単体テスト作成
- [x] UIコンポーネントのレイアウト定義 (入力欄、ボタン、プログレスバー、結果リスト)
- [x] `MainViewModel` の初期化と `UiState` の監視ロジックの実装
- [x] 検索中UI (入力欄・ボタン非活性化、プログレスバー・中断ボタン表示) の実装
- [x] ユーザーイベント (検索ボタン、中断ボタンクリック) の `ViewModel` への通知ロジックの実装
- [x] エラーダイアログ表示ロジックの実装

### 2.2. `MainViewModel.kt`
- [x] `MainViewModel` の単体テスト作成
- [x] `UiState` データクラスの定義 (`isResolving`, `resolvedDependencies`, `error`)
- [x] `_uiState` (MutableStateFlow) と `uiState` (StateFlow) の定義
- [x] `startResolution(coordinate: String)` メソッドの実装
    - [x] 入力値の正規表現バリデーション (`^[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+$`)
    - [x] バリデーション失敗時の `UiState` エラーメッセージ設定
    - [x] `isResolving` の更新、`error` と `resolvedDependencies` のクリア
    - [x] `viewModelScope.launch` を用いたコルーチンジョブの管理 (`resolutionJob`)
    - [x] `dependencyRepository.resolveDependencies(coordinate)` の `Flow` 収集 (`collect`)
    - [x] `Flow` からの `emit` ごとに `resolvedDependencies` リストへの追加と `UiState` 更新
    - [x] `onCompletion` または `catch` ブロックでの `isResolving` の `false` 更新
    - [x] エラー発生時の `UiState` へのエラーメッセージ設定
- [x] `cancelResolution()` メソッドの実装 (`resolutionJob?.cancel()`)

## 3. Domain Layer

### 3.1. `DependencyRepository.kt` (Interface)
- [x] `interface DependencyRepository` の定義
- [x] `fun resolveDependencies(coordinate: String): Flow<String>` メソッドの定義

### 3.2. `DependencyRepositoryImpl.kt`
- [x] `DependencyRepositoryImpl` の単体テスト作成
- [x] クラス定義と依存関係 (PomCache, MavenRemoteDataSource, PomParser) のインジェクション
- [x] `resolveDependencies` メソッドの実装 (`flow { ... }` を使用し、`resolveRecursive` を呼び出す)
- [x] `resolveRecursive` メソッドの実装
    - [x] 循環参照防止ロジック (`resolved` セット)
    - [x] キャッシュの確認と利用 (PomCache)
    - [x] `MavenRemoteDataSource` を用いたPOM取得
    - [x] `PomParser` を用いたXML解析
    - [x] 取得した `PomData` のキャッシュ保存
    - [x] 基本情報の抽出とプロパティ解決 (`resolveProperties`)
    - [x] `dependencyManagement` を参照したバージョン解決 (`resolveVersionFromDependencyManagement`)
    - [x] 依存関係のフィルタリング (optional, scope: test, provided)
    - [x] 親POMの再帰的解決
    - [x] 解決済み依存関係の `emit`
    - [x] 未解決プロパティ/バージョンに対する警告ログ出力と追跡停止
- [x] `resolveProperties` メソッドの実装 (プロパティ解決ロジック)
- [x] `resolveVersionFromDependencyManagement` メソッドの実装 (バージョン解決ロジック)
- [x] 内部データ構造 (`PomData`, `ParentData`, `Dependency`) の定義

## 4. Data Layer

### 4.1. `PomCache.kt`
- [ ] `PomCache` の単体テスト作成
- [ ] クラス定義とシングルトンインスタンスとしての提供
- [ ] `ConcurrentHashMap` を用いたキャッシュの実装 (`cache` プロパティ)
- [ ] `get(coordinate: String)`, `put(coordinate: String, pomData: PomData)`, `contains(coordinate: String)` メソッドの実装

### 4.2. `MavenRemoteDataSource.kt`
- [ ] `MavenRemoteDataSource` の単体テスト作成
- [ ] クラス定義と `HttpClient` のインジェクション
- [ ] `BASE_URL` の定義
- [ ] `getPomXml(coordinate: String)` メソッドの実装
    - [ ] POMファイルURL生成ロジック
    - [ ] Ktor `HttpClient` を用いたHTTP GETリクエスト
    - [ ] リクエスト失敗時の1秒間隔1回リトライロジック
    - [ ] `Result` 型での成功/失敗の返却
    - [ ] HTTP通信の成否のデバッグログ出力

### 4.3. `PomParser.kt`
- [x] `PomParser` の単体テスト作成
- [x] クラス定義
- [x] `parse(xmlString: String): Result<PomData>` メソッドの実装
    - [x] `XmlPullParser` の初期化とXML解析
    - [x] `project.groupId`, `project.artifactId`, `project.version` (または `project.parent` から) の抽出
    - [x] `project.parent` 情報の抽出
    - [x] `project.dependencies` から `Dependency` オブジェクト生成
    - [x] `project.properties` の抽出
    - [x] `project.dependencyManagement.dependencies` の抽出
    - [x] 必須情報 (`groupId`, `artifactId`, `version`) 欠落時のエラー返却
    - [x] `Result` 型での成功/失敗の返却
    - [x] XML解析の成否のデバッグログ出力

## 5. 結合テスト / E2Eテスト
- [ ] 不正な入力文字列でエラーダイアログが表示されることの確認
- [ ] 依存関係が0個、または1個の単純なライブラリで期待通りの出力が得られることの確認
- [ ] 親POMを持つライブラリで、親の情報が正しく依存関係に追加されることの確認
- [ ] 複雑な依存関係ツリーが正しく解決されることの確認
- [ ] ネットワークエラーやXML解析エラー時のUI表示とエラーメッセージの確認

## 6. テストカバレッジ計測
- [ ] JaCoCo を使用したカバレッジレポートの生成と確認
