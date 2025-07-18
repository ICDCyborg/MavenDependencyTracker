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
}