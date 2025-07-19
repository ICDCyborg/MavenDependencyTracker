# PROJECT.md: Maven Dependency Tracker

## 1. プロジェクト概要

### 1.1. プロジェクト名
Maven Dependency Tracker

### 1.2. 目的
指定されたMavenライブラリのPOMファイルを取得し、推移的な依存関係を再帰的に解決して一覧表示するAndroidアプリケーションを開発する。

### 1.3. 動作環境
- Android 15

### 1.4. 開発環境
- **OS:** Windows 11
- **IDE:** Android Studio
- **言語:** Kotlin
- **ビルドシステム:** Gradle (Kotlin DSL)

## 2. 要件定義

### 2.1. 機能要件

#### 2.1.1. 画面構成
- **画面上部:**
    - 依存関係の起点となるライブラリ座標を入力する一行のテキストインプット。
    - 検索を実行するボタン。
- **画面下部:**
    - 解決された依存関係のリストを表示する、縦スクロール可能なテキストボックス。

#### 2.1.2. 入出力
- **入力形式:** `(groupId):(artifactId):(version)` 形式の文字列。
    - 例: `org.jetbrains.kotlin:kotlin-stdlib:1.9.23`
- **出力形式:** 入力と同じ形式の文字列リスト。
- **データソース:** Maven Central Repository (`https://repo1.maven.org/maven2/`)

#### 2.1.3. 依存関係解決ロジック
1.  **POMファイル取得:**
    - リポジトリURL `https://repo1.maven.org/maven2/` に `(groupIdの.を/に置換)/(artifactId)/(version)/(artifactId)-(version).pom` を連結してPOMファイルのURLを生成し、HTTP GETリクエストで取得する。
2.  **基本情報抽出:**
    - `project.groupId` (または `project.parent.groupId`)
    - `project.artifactId`
    - `project.version` (または `project.parent.version`)
3.  **依存関係のフィルタリング:**
    - `project.dependencies` 内の各 `dependency` タグを走査する。
    - 以下の依存関係は追跡対象から**除外**する。
        - `optional` タグが `"true"` のもの。
        - `scope` タグが `"test"` または `"provided"` のもの。
4.  **親POMの解決:**
    - `project.parent` タグが存在する場合、親POMを依存関係の一つとして追加し、そのPOMファイルも解析対象とする。
5.  **バージョン解決 (`dependencyManagement`):**
    - `dependency` タグに `version` が指定されていない場合、自身または親POMの `project.dependencyManagement.dependencies`セクションを参照し、`groupId` と `artifactId` が一致するライブラリからバージョンを解決する。
6.  **プロパティ解決:**
    - `groupId` や `version` の値が `${...}` 形式の場合、`project.properties` 内の対応するタグの値で置換する。
    - `${project.groupId}` `${project.version}` は、そのPOMファイルの `groupId` `version` を参照する。
7.  **再帰的追跡:**
    - 解決された依存関係リストの各ライブラリについて、上記1〜6のプロセスを再帰的に実行する。
8.  **結果の整理:**
    - 最終的に得られた依存関係リストから、重複するライブラリ（`groupId:artifactId:version` が完全に同一）を排除する。

### 2.2. 非機能要件

#### 2.2.1. エラー処理とロギング
- **入力チェック:**
    - 入力文字列が正規表現 `^[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+:[a-zA-Z0-9\.\-]+$` にマッチしない場合、エラーダイアログを表示して処理を中止する。
- **HTTPリクエスト:**
    - リクエスト失敗時、1秒の間隔を置いて1回リトライする。
    - リトライに失敗した場合、エラーダイアログを表示して処理を中止する。
- **XML解析:**
    - 解析に失敗した場合、エラーダイアログを表示して処理を中止する。
- **必須情報:**
    - `groupId`, `artifactId`, `version` のいずれかがPOMから解決できない場合、エラーダイアログを表示して処理を中止する。
- **バージョン未解決:**
    - `dependencyManagement` を参照しても `version` が解決できない場合、該当ライブラリのバージョンを `"ERROR"` とし、警告ログを出力して処理は継続する。
- **プロパティ未解決:**
    - `${variable}` 形式のプロパティが解決できない場合、値は `${variable}` のまま格納し、警告ログを出力する。
- **追跡の停止:**
    - `version` が `"ERROR"` または `${...}` のままのライブラリは、それ以上の推移的依存関係の追跡を行わない。
- **ログ:**
    - HTTP通信の成否、XML解析の成否、および上記のエラー・警告に関する情報をデバッグログとして出力する。
    #### 2.2.2. アーキテクチャ
- 単一責任の原則（SOLID原則）に基づき、UI (`ViewModel`)、ビジネスロジック (`Repository`)、データソース (`DataSource`)、解析 (`Parser`) の責務を分離したクラス設計を行う。

### 2.3. テスト方針

- **テストフレームワーク:** JUnit 5
- **カバレッジ計測:** JaCoCo を使用し、カバレッジレポートを出力する。
- **テストケース:**
    - **単体テスト:**
        - `MavenRemoteDataSource`: 正しいURLでPOMファイルを取得できることを確認する。
        - `PomParser`: 各種パターンのローカルPOMファイルを正しく解析できること、および解析エラー時に例外をスローすることを確認する。
    - **結合テスト/E2Eテスト:**
        - 不正な入力文字列でエラーダイアログが表示されることを確認する。
        - 依存関係が0個、または1個の単純なライブラリで期待通りの出力が得られることを確認する。
        - 親POMを持つライブラリで、親の情報が正しく依存関係に追加されることを確認する。
