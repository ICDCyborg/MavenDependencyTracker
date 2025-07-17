# TODO.md: Maven Dependency Tracker 開発タスク

## 開発方針
- TDD (テスト駆動開発) と Outside-In (外側から内側へ) アプローチを採用する。
- 各コンポーネントは、まずテストを書き、そのテストが通るように実装を進める。

## 1. テスト環境構築
- [ ] JUnit 5 のセットアップ
- [ ] MockK のセットアップ
- [ ] Turbine のセットアップ (Flowのテスト用)
- [ ] Ktor Client Mock のセットアップ (HTTPリクエストのモック用)
- [ ] JaCoCo のセットアップ (テストカバレッジ計測用)

## 2. UI Layer (外側から開始)

### 2.1. `MainActivity.kt`
- [ ] UIコンポーネントのレイアウト定義 (入力欄、ボタン、プログレスバー、結果リスト)
- [ ] `MainViewModel` の初期化と `UiState` の監視ロジックの実装
- [ ] 検索中UI (入力欄・ボタン非活性化、プログレスバー・中断ボタン表示) の実装
- [ ] ユーザーイベント (検索ボタン、中断ボタンクリック) の `ViewModel` への通知ロジックの実装
- [ ] エラーダイアログ表示ロジックの実装

### 2.2. `MainViewModel.kt`
- [ ] `UiState` データクラスの定義 (`isResolving`, `resolvedDependencies`, `error`)
- [ ] `_uiState` (MutableStateFlow) と `uiState` (StateFlow) の定義
- [ ] `startResolution(coordinate: String)` メソッドの実装
    - [ ] 入力値の正規表現バリデーション (`^[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+$`)
    - [ ] バリデーション失敗時の `UiState` エラーメッセージ設定
    - [ ] `isResolving` の更新、`error` と `resolvedDependencies` のクリア
    - [ ] `viewModelScope.launch` を用いたコルーチンジョブの管理 (`resolutionJob`)
    - [ ] `dependencyRepository.resolveDependencies(coordinate)` の `Flow` 収集 (`collect`)
    - [ ] `Flow` からの `emit` ごとに `resolvedDependencies` リストへの追加と `UiState` 更新
    - [ ] `onCompletion` または `catch` ブロックでの `isResolving` の `false` 更新
    - [ ] エラー発生時の `UiState` へのエラーメッセージ設定
- [ ] `cancelResolution()` メソッドの実装 (`resolutionJob?.cancel()`)
- [ ] `DependencyRepository` のモックを使った単体テスト
    - [ ] 有効な入力での正常な依存関係解決フローのテスト
    - [ ] 不正な入力でのエラーハンドリングのテスト
    - [ ] 検索中断機能のテスト
    - [ ] `Flow` からのデータ受信と `UiState` 更新のテスト (Turbineを使用)

## 3. Domain Layer

### 3.1. `DependencyRepository.kt` (Interface)
- [ ] `interface DependencyRepository` の定義
- [ ] `fun resolveDependencies(coordinate: String): Flow<String>` メソッドの定義

### 3.2. `DependencyRepositoryImpl.kt`
- [ ] クラス定義と依存関係 (PomCache, MavenRemoteDataSource, PomParser) のインジェクション
- [ ] `resolveDependencies` メソッドの実装 (`flow { ... }` を使用し、`resolveRecursive` を呼び出す)
- [ ] `resolveRecursive` メソッドの実装
    - [ ] 循環参照防止ロジック (`resolved` セット)
    - [ ] キャッシュの確認と利用 (PomCache)
    - [ ] `MavenRemoteDataSource` を用いたPOM取得
    - [ ] `PomParser` を用いたXML解析
    - [ ] 取得した `PomData` のキャッシュ保存
    - [ ] 基本情報の抽出とプロパティ解決 (`resolveProperties`)
    - [ ] `dependencyManagement` を参照したバージョン解決 (`resolveVersionFromDependencyManagement`)
    - [ ] 依存関係のフィルタリング (optional, scope: test, provided)
    - [ ] 親POMの再帰的解決
    - [ ] 解決済み依存関係の `emit`
    - [ ] 未解決プロパティ/バージョンに対する警告ログ出力と追跡停止
- [ ] `resolveProperties` メソッドの実装 (プロパティ解決ロジック)
- [ ] `resolveVersionFromDependencyManagement` メソッドの実装 (バージョン解決ロジック)
- [ ] 内部データ構造 (`PomData`, `ParentData`, `Dependency`) の定義
- [ ] 単体テスト
    - [ ] 各種POMデータパターンでの依存関係解決のテスト
    - [ ] 循環参照防止のテスト
    - [ ] プロパティ解決のテスト
    - [ ] `dependencyManagement` によるバージョン解決のテスト
    - [ ] フィルタリングルールのテスト (optional, scope)
    - [ ] 親POM解決のテスト
    - [ ] エラーケース (POM取得失敗、解析失敗、必須情報欠落) のテスト
    - [ ] 未解決プロパティ/バージョンでの警告と追跡停止のテスト

## 4. Data Layer

### 4.1. `PomCache.kt`
- [ ] クラス定義とシングルトンインスタンスとしての提供
- [ ] `ConcurrentHashMap` を用いたキャッシュの実装 (`cache` プロパティ)
- [ ] `get(coordinate: String)`, `put(coordinate: String, pomData: PomData)`, `contains(coordinate: String)` メソッドの実装
- [ ] 単体テスト
    - [ ] キャッシュへの保存と取得のテスト
    - [ ] 存在確認のテスト
    - [ ] スレッドセーフの基本的なテスト

### 4.2. `MavenRemoteDataSource.kt`
- [ ] クラス定義と `HttpClient` のインジェクション
- [ ] `BASE_URL` の定義
- [ ] `getPomXml(coordinate: String)` メソッドの実装
    - [ ] POMファイルURL生成ロジック
    - [ ] Ktor `HttpClient` を用いたHTTP GETリクエスト
    - [ ] リクエスト失敗時の1秒間隔1回リトライロジック
    - [ ] `Result` 型での成功/失敗の返却
    - [ ] HTTP通信の成否のデバッグログ出力
- [ ] 単体テスト
    - [ ] 正しいURLでPOMファイルを取得できることの確認
    - [ ] HTTPリクエスト失敗時のリトライとエラーハンドリングのテスト
    - [ ] ネットワークエラー時の `Result.failure` 返却のテスト
    - [ ] Ktor Client Mock を使用したモックテスト

### 4.3. `PomParser.kt`
- [ ] クラス定義
- [ ] `parse(xmlString: String): Result<PomData>` メソッドの実装
    - [ ] `XmlPullParser` の初期化とXML解析
    - [ ] `project.groupId`, `project.artifactId`, `project.version` (または `project.parent` から) の抽出
    - [ ] `project.parent` 情報の抽出
    - [ ] `project.dependencies` から `Dependency` オブジェクト生成
    - [ ] `project.properties` の抽出
    - [ ] `project.dependencyManagement.dependencies` の抽出
    - [ ] 必須情報 (`groupId`, `artifactId`, `version`) 欠落時のエラー返却
    - [ ] `Result` 型での成功/失敗の返却
    - [ ] XML解析の成否のデバッグログ出力
- [ ] 単体テスト
    - [ ] 各種パターンのローカルPOMファイルを正しく解析できることの確認
    - [ ] 解析エラー時に `Result.failure` を返すことの確認
    - [ ] 必須情報欠落時のエラーハンドリングのテスト
    - [ ] 親POM、依存関係、プロパティ、`dependencyManagement` の正しい抽出テスト

## 5. 結合テスト / E2Eテスト
- [ ] 不正な入力文字列でエラーダイアログが表示されることの確認
- [ ] 依存関係が0個、または1個の単純なライブラリで期待通りの出力が得られることの確認
- [ ] 親POMを持つライブラリで、親の情報が正しく依存関係に追加されることの確認
- [ ] 複雑な依存関係ツリーが正しく解決されることの確認
- [ ] ネットワークエラーやXML解析エラー時のUI表示とエラーメッセージの確認

## 6. テストカバレッジ計測
- [ ] JaCoCo を使用したカバレッジレポートの生成と確認