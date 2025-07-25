## 単体テストの設計

### 1. `resolveParentChain` の単体テスト

`resolveParentChain` は、指定された座標のPOMデータとその親チェーンを正しく取得・解析し、リストとして返すことを保証します。また、循環参照を検出する能力もテストします。

**テスト対象メソッド:** `private suspend fun resolveParentChain(coordinate: String, resolvedParents: MutableSet<String> = mutableSetOf()): Result<List<PomData>>`

**依存モック:**
* `PomCache` (モック): `get`, `put`, `contains` メソッド
* `MavenRemoteDataSource` (モック): `getPomXml` メソッド
* `PomParser` (モック): `parse` メソッド

**主要なテストケース:**

* **成功ケース:**
    * **最上位のPOMの解決:**
        * 与えられた座標が親を持たない場合、そのPOMデータのみを含むリストを返すこと。
        * `PomCache`から取得できる場合、リモートへのリクエストが発生しないこと。
        * `PomCache`に存在しない場合、リモートから取得し、`PomParser`で解析し、`PomCache`に保存すること。
    * **単一階層の親子の解決:**
        * 子POMと親POMのデータが正しく取得され、`[親PomData, 子PomData]`の順序でリストに格納されること。
        * 各POMの取得時にキャッシュが適切に利用されること。
    * **複数階層の親子の解決:**
        * `[最遠親PomData, ... , 中間親PomData, 現在のPomData]` の順序でリストに格納されること。
        * すべてのPOMデータが正しく取得・解析されること。
* **失敗ケース:**
    * **リモートからのPOM XML取得失敗:**
        * `MavenRemoteDataSource.getPomXml`が`Result.failure`を返した場合、`resolveParentChain`も`Result.failure`を返すこと。
        * エラーメッセージが適切に含まれること。
    * **POM XML解析失敗:**
        * `PomParser.parse`が`Result.failure`を返した場合、`resolveParentChain`も`Result.failure`を返すこと。
        * エラーメッセージが適切に含まれること。
    * **循環参照の検出:**
        * `resolvedParents`セットに既に存在する座標が渡された場合、`Result.failure(CircularReferenceException)`を返すこと。
        * エラーログが適切に出力されること。
    * **親座標が無効な形式:**
        * 親の座標がPOMデータ内で不正な形式の場合、処理が適切に中断またはエラーとして扱われること（これは主に`PomParser`の責務だが、`resolveParentChain`がそのエラーを受け取れるか確認）。

### 2. `mergePomData` の単体テスト

`mergePomData` は、`PomData`のリストを正しい優先順位で結合し、単一の結合済み`PomData`を生成することを保証します。

**テスト対象メソッド:** `private fun mergePomData(pomDataList: List<PomData>): PomData`

**依存モック:** なし (純粋関数)

**主要なテストケース:**

* **成功ケース:**
    * **空のリストが渡された場合:**
        * 適切な例外（例: `IllegalArgumentException`）をスローすること。
    * **単一のPOMデータリスト:**
        * リストが1つの`PomData`のみを含む場合、その`PomData`がそのまま返されること。
    * **基本的な結合（親がデフォルト、子が全てを持つ）:**
        * 親が最低限のデータ（例: `groupId`のみ）を持ち、子が完全なデータを持つ場合、子のデータが優先されること。
        * `properties`, `dependencyManagement`, `dependencies`が正しく結合され、子が優先されること。
    * **基本的な結合（親が全てを持ち、子が一部）:**
        * 親が完全なデータを持ち、子が一部のデータのみを持つ場合、子のデータがある場合は子が優先され、ない場合は親のデータが採用されること。
    * **`properties`の結合:**
        * 重複するキーを持つプロパティがある場合、子の値で上書きされること。
        * 子にしかないプロパティ、親にしかないプロパティが両方含まれること。
    * **`dependencyManagement`の結合:**
        * `groupId`と`artifactId`が重複する依存関係がある場合、子の`dependencyManagement`のエントリが優先されること。
        * 親、子それぞれにしかない依存関係が両方含まれること。
    * **`dependencies`の結合:**
        * `groupId`と`artifactId`が重複する依存関係がある場合、子の`dependencies`のエントリが優先されること。
        * 親、子それぞれにしかない依存関係が両方含まれること。
    * **複数階層の結合:**
        * `[祖父母, 親, 子]`のように3つ以上の`PomData`を持つリストを渡し、最も遠い親から順に正しく結合されること。
* **エッジケース:**
    * `groupId`, `artifactId`, `version`がいずれかまたは全てがnullの`PomData`が混ざっている場合の結合の振る舞い（`PomData`のデータクラスのnullableなプロパティの考慮）。

### 3. `resolveRecursive` の単体テスト

`resolveRecursive` は、初期座標から始まり、親チェーンの解決と結合、プロパティ解決、`dependencyManagement`からのバージョン解決、フィルタリング、そして推移的依存関係の再帰的解決を通じて、最終的な依存関係リストをリアルタイムで`Flow`として`emit`することを保証します。

**テスト対象メソッド:** `private suspend fun resolveRecursive(coordinate: String, resolvedDependencies: MutableSet<String>): Flow<String>`

**依存モック:**
* `resolveParentChain` (モック): 親チェーンの取得結果を制御。
* `mergePomData` (モック): `PomData`の結合結果を制御。
* `resolveProperties` (モック): プロパティ解決結果を制御。
* `resolveVersionFromDependencyManagement` (モック): バージョン解決結果を制御。

**主要なテストケース:**

* **成功ケース:**
    * **単一の依存関係（推移的依存なし）:**
        * 与えられた座標のみが`emit`され、他の依存関係は解決されないこと。
    * **単純な推移的依存関係:**
        * `A -> B` の関係で、`A`と`B`の座標が順に`emit`されること。
    * **複数階層の推移的依存関係:**
        * `A -> B -> C` の関係で、`A`, `B`, `C`の座標が適切に`emit`されること。
    * **`properties`の解決:**
        * 依存関係のバージョンがプロパティで定義されている場合、正しく解決されて`emit`されること。
        * 未解決のプロパティがある場合、警告ログが出力され、その依存関係の推移的解決は行われないこと。
    * **`dependencyManagement`からのバージョン解決:**
        * バージョンが`dependencyManagement`で定義されている依存関係について、正しくバージョンが解決されて`emit`されること。
        * `dependencyManagement`で解決できない場合、警告ログが出力され、その依存関係の推移的解決は行われないこと。
    * **フィルタリング（`scope`, `optional`）:**
        * `scope = "test"`または`"provided"`の依存関係が`emit`されないこと。
        * `optional = true`の依存関係が`emit`されないこと。
    * **循環参照の防止:**
        * `A -> B -> A`のような循環参照が検出された場合、`A`が二重に`emit`されないこと。
        * 内部的に`resolvedDependencies`セットが正しく機能していること。
* **失敗ケース:**
    * **`resolveParentChain`が失敗した場合:**
        * `resolveParentChain`が`Result.failure`を返した場合、`resolveRecursive`もエラーを示す`Flow`（例: `Flow<String>`だが例外をスローする、または特定のエラー文字列を`emit`する）を返すこと。
    * **無効な座標が渡された場合:**
        * 形式が不正な座標が渡された場合、エラーログが出力され、適切に処理が終了すること（これは`MainViewModel`のバリデーションでも捕捉されるが、念のため）。

---

これらのテストケースを網羅することで、各メソッドが意図した通りに動作し、全体の依存関係解決ロジックが堅牢であることを確認できるでしょう。
