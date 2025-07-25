## 1\. `resolveParentChain`

### メソッドシグネチャ

```kotlin
private suspend fun resolveParentChain(
    coordinate: String,
    resolvedParents: MutableSet<String> = mutableSetOf()
): Result<List<PomData>>
```

### 責務

指定されたMavenライブラリ座標から、最も遠い親POMまでを再帰的に解決し、そのチェーンを`PomData`のリストとして返します。リストの順序は**最も遠い親から現在の座標まで**とします。このメソッドは、POMデータの取得と解析、および循環参照の検出に特化します。

### 処理フロー

1.  **循環参照の検出**:

      * 引数`coordinate`が`resolvedParents`セットにすでに含まれている場合、**循環参照**と判断します。
      * エラーログを出力し、`Result.failure(CircularReferenceException("Circular reference detected for $coordinate"))` を返します。

2.  **解決済み親の記録**:

      * `coordinate`を`resolvedParents`セットに追加します。

3.  **POMデータの取得**:

      * まず`pomCache.get(coordinate)`を試行し、`PomData`を取得します。
      * キャッシュに存在しない場合、`mavenRemoteDataSource.getPomXml(coordinate)`を呼び出してXML文字列を取得します。
          * 取得が`Result.failure`の場合、エラーログを出力し、その`Result.failure`をそのまま返します（呼び出し元で適切なエラーハンドリングを促す）。
      * XML文字列が正常に取得できた場合、`pomParser.parse(xmlString)`で`PomData`オブジェクトに解析します。
          * 解析が`Result.failure`の場合、エラーログを出力し、その`Result.failure`をそのまま返します。
      * 成功した場合、取得した`PomData`を`pomCache.put(coordinate, pomData)`でキャッシュに保存します。

4.  **親POMの再帰的解決**:

      * 取得した`pomData`に`parent`情報が存在し、かつ親の座標が現在の座標と異なる場合:
          * 親の座標（`pomData.parent.groupId`, `pomData.parent.artifactId`, `pomData.parent.version`）を構築します。
          * `resolveParentChain`を**再帰的に呼び出し**、親チェーンのリスト（`parentChainListResult: Result<List<PomData>>`）を取得します。
          * `parentChainListResult`が`Result.failure`の場合、その`Result.failure`をそのまま返します。
          * `parentChainListResult`が`Result.success`の場合、そのリストの最後に現在の`pomData`を追加し、`Result.success(combinedList)`として返します。
      * `pomData`に親情報がない場合、または親の座標が現在の座標と同じ場合（自身の親である場合）：
          * 現在の`pomData`のみを含むリストを`Result.success(listOf(pomData))`として返します。

### エラー処理

  * **`Result<List<PomData>>`** を返すことで、呼び出し元が成功または失敗を明示的にハンドリングできるようにします。
  * `CircularReferenceException`, `PomFetchException`, `PomParseException`など、具体的なカスタム例外を定義して使用すると、エラーの種類が明確になります。

-----

## 2\. `mergePomData`

### メソッドシグネチャ

```kotlin
private fun mergePomData(pomDataList: List<PomData>): PomData
```

### 責務

`resolveParentChain`から返された`PomData`のリスト（最も遠い親から現在の座標までの順）を受け取り、リストの順序に従って順番に結合処理を実行し、最終的な単一の`PomData`を生成します。結合ルールは、**子`PomData`の情報が存在すれば子を優先し、なければ親の情報を採用する**です。

### 処理フロー

1.  **初期化**:

      * `pomDataList`が空の場合、例外をスローするか、デフォルトの`PomData`を返します（ここでは例外スローを想定）。
      * `mergedPomData`の初期値として、リストの最初の要素（最も遠い親）を設定します。

2.  **ループによる結合**:

      * `pomDataList`の2番目の要素から最後までをループします。

      * 各ループで、現在の`mergedPomData`を親とし、ループ中の要素を子として結合します。

      * 結合ロジックは以下の通りです。

      * **基本情報の結合**:

          * `groupId`, `artifactId`, `version`は、`childPomData`に値があればその値を、なければ`parentPomData`の値を採用します。

      * **プロパティの結合**:

          * 新しい`properties`マップを初期化します。
          * まず`parentPomData.properties`の全てのエントリを追加します。
          * 次に`childPomData.properties`のエントリを追加します。この際、キーが重複する場合は`childPomData`の値で上書きされます。

      * **`dependencyManagement`の結合**:

          * 新しい`dependencyManagement`リストを初期化します。
          * `parentPomData.dependencyManagement`の全ての依存関係を追加します。
          * `childPomData.dependencyManagement`の依存関係を、**`groupId`と`artifactId`の組み合わせで重複がないように**追加します。重複する場合は`childPomData`のエントリを優先します。

      * **`dependencies`の結合**:

          * 新しい`dependencies`リストを初期化します。
          * `parentPomData.dependencies`の全ての依存関係を追加します。
          * `childPomData.dependencies`の依存関係を、**`groupId`と`artifactId`の組み合わせで重複がないように**追加します。重複する場合は`childPomData`のエントリを優先します。

3.  **最終結果の返却**:

      * ループが完了した後、最終的に結合された`mergedPomData`を返します。

-----

## 3\. `resolveRecursive`

### メソッドシグネチャ

```kotlin
private suspend fun resolveRecursive(
    coordinate: String,
    resolvedDependencies: MutableSet<String>,
    // mergePomDataを初回呼び出し時のみ外部で実行するため、この引数は削除
    // mergedPomData: PomData? = null 
): Flow<String>
```

### 責務

指定されたライブラリ座標の推移的依存関係を再帰的に解決し、解決された各依存関係を`Flow<String>`としてリアルタイムに通知します。このメソッドは、`DependencyRepositoryImpl.resolveDependencies` から呼び出される最初の POM については親チェーンの解決と結合を行い、その後、再帰呼び出しで子依存関係を解決します。

### 処理フロー

1.  **循環参照の防止**:

      * 引数`coordinate`が`resolvedDependencies`セットにすでに含まれている場合、循環参照と判断し、`emptyFlow()`を返します。
      * `coordinate`を`resolvedDependencies`セットに追加します。

2.  **POMデータの取得と結合**:

      * `resolveParentChain(coordinate)`を呼び出し、親チェーンの`PomData`リストを取得します。
      * `resolveParentChain`が`Result.failure(exception)`を返した場合、エラーログを出力し、その`exception`を含む`Flow`を返すか、エラーメッセージを`emit`して完了します。例: `flow { throw exception }`
      * `Result.success(pomDataList)`の場合、取得した`pomDataList`を\*\*`mergePomData(pomDataList)`\*\*に渡し、最終的な結合済み`PomData`を生成します。これを`currentPomData`とします。

3.  **現在の座標の`emit`**:

      * `currentPomData`から、現在のライブラリ座標（例: `currentPomData.groupId:currentPomData.artifactId:currentPomData.version`）を構成し、`emit`します。

4.  **プロパティ解決**:

      * `currentPomData`内の`dependencies`リストと`dependencyManagement`リストの各エントリについて、`groupId`, `artifactId`, `version`, `scope`などのフィールドに含まれる`${...}`形式のプロパティ参照を`resolveProperties`メソッドを使用して解決します。
      * `resolveProperties`メソッドは、`currentPomData.properties`を基に解決を行います。
      * プロパティが解決できない場合、警告ログを出力し、そのままの文字列を保持します。

5.  **`dependencyManagement`からのバージョン解決**:

      * `currentPomData`内の`dependencies`リストを走査します。
      * 各依存関係にバージョンが明示されていない場合、`currentPomData.dependencyManagement`セクションを参照してバージョンを解決します（`resolveVersionFromDependencyManagement`メソッドを使用）。
      * バージョンが解決できない場合、警告ログを出力し、その依存関係の推移的解決は行いません（この依存関係は後続のフィルタリングで除外されます）。

6.  **依存関係のフィルタリングと再帰的解決**:

      * `currentPomData`内の`dependencies`リストを走査します。
      * 以下の条件に合致する依存関係は除外します:
          * `scope`が`test`または`provided`の場合。
          * `optional`が`true`の場合。
          * `version`が未解決（`"ERROR"`など、特別なプレースホルダー値が設定されている場合）または不正な形式の場合。
      * 上記条件を満たさない各依存関係に対し、その座標（`groupId:artifactId:version`）を構築し、**`resolveRecursive`を再帰的に呼び出します**。
      * この再帰呼び出しによって`emit`される依存関係もすべて`emit`します。

### `DependencyRepositoryImpl.resolveDependencies` の変更点

  * `DependencyRepositoryImpl.resolveDependencies` メソッドは、引数`coordinate`を受け取り、その`coordinate`を直接`resolveRecursive`に渡す形になります。`resolveRecursive`の内部で、親チェーンの解決と結合が行われます。
