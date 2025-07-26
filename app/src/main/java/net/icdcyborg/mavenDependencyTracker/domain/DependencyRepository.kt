package net.icdcyborg.mavenDependencyTracker.domain

import kotlinx.coroutines.flow.Flow

/**
 * 依存関係の解決に関するビジネスロジックを定義するインターフェースです。
 */
interface DependencyRepository {
    /**
     * 指定されたMaven座標から推移的な依存関係を解決し、結果をFlowとして返します。
     * 依存関係が一つ解決されるたびに、その結果がFlowにemitされます。
     *
     * @param coordinate 解決を開始するMaven座標 (例: "group:artifact:version")。
     * @return 解決された依存関係の座標を順次emitするFlow。
     */
    fun resolveDependencies(coordinate: String): Flow<String>

    /**
     * 指定されたMaven座標のPOMファイルの内容を取得します。
     *
     * @param coordinate POMファイルを取得するMaven座標。
     * @return POMファイルの内容をemitするFlow。POMが見つからない場合は空のFlow。
     */
    fun getPom(coordinate: String): Flow<String>

    /**
     * 指定されたMaven座標に対応するJARファイルが存在するかどうかをチェックします。
     *
     * @param coordinate チェックするJARのMaven座標。
     * @return JARファイルが存在する場合はtrue、それ以外の場合はfalse。
     */
    suspend fun checkJarExists(coordinate: String): Boolean

    /**
     * 指定されたMaven座標に対応するPomのURLを返します。
     *
     * @param coordinate チェックするJARのMaven座標。
     * @return URLもしくはnull
     */
    suspend fun getUrlFromCoordinate(coordinate: String): String?
}
