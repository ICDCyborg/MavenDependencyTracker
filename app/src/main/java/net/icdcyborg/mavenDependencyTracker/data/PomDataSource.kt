package net.icdcyborg.mavenDependencyTracker.data

import kotlin.Result

/**
 * POMファイルを取得するためのデータソースクラス。
 *
 * このクラスは、指定されたMaven座標に対応するPOMのXML文字列を取得します。
 * まず[PomCache]を検索し、キャッシュに存在しない場合は[MavenRemoteDataSource]からリモートで取得を試みます。
 * リモート取得に成功した場合、その結果をキャッシュに保存してから返します。
 *
 * @property pomCache POMのXML文字列をキャッシュするための[PomCache]インスタンス。
 * @property mavenRemoteDataSource MavenリポジトリからリモートでPOMファイルを取得するための[MavenRemoteDataSource]インスタンス。
 */
class PomDataSource(
    private val pomCache: PomCache,
    private val mavenRemoteDataSource: MavenRemoteDataSource,
) {
    /**
     * 指定されたMaven座標に対応するPOMのXML文字列を取得します。
     *
     * @param coordinate 取得するPOMのMaven座標（例: "com.google.code.gson:gson:2.8.8"）。
     * @return 取得したPOMのXML文字列を含む[Result]。成功した場合は[Result.success]にXML文字列が、
     *         失敗した場合は[Result.failure]に例外が含まれます。
     */
    suspend fun getPomXml(coordinate: String): Result<String> {
        // キャッシュを確認
        val cachedXml = pomCache.get(coordinate)
        if (cachedXml != null) {
            return Result.success(cachedXml)
        }

        // キャッシュにない場合はリモートから取得
        val result = mavenRemoteDataSource.getPomXml(coordinate)
        return result.fold(
            onSuccess = { xmlString ->
                // 取得に成功したらキャッシュに保存
                pomCache.put(coordinate, xmlString)
                Result.success(xmlString)
            },
            onFailure = { exception ->
                Result.failure(exception)
            },
        )
    }
}
