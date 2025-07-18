package net.icdcyborg.mavenDependencyTracker.data

import net.icdcyborg.mavenDependencyTracker.domain.PomData
import java.util.concurrent.ConcurrentHashMap

/**
 * 解析されたPOMデータ（[PomData]）をメモリ上にキャッシュするクラスです。
 * キーとしてMaven座標（String）、値として[PomData]を保持します。
 */
class PomCache {
    private val cache = ConcurrentHashMap<String, PomData>()

    /**
     * 指定されたMaven座標に対応する[PomData]をキャッシュから取得します。
     *
     * @param coordinate 取得するPOMデータのMaven座標。
     * @return キャッシュに存在する[PomData]、存在しない場合はnull。
     */
    fun get(coordinate: String): PomData? {
        return cache[coordinate]
    }

    /**
     * 指定されたMaven座標と[PomData]をキャッシュに保存します。
     *
     * @param coordinate 保存するPOMデータのMaven座標。
     * @param pomData 保存する[PomData]オブジェクト。
     */
    fun put(coordinate: String, pomData: PomData) {
        cache[coordinate] = pomData
    }

    /**
     * 指定されたMaven座標がキャッシュに存在するかどうかを確認します。
     *
     * @param coordinate 確認するMaven座標。
     * @return キャッシュに存在する場合はtrue、それ以外の場合はfalse。
     */
    fun contains(coordinate: String): Boolean {
        return cache.containsKey(coordinate)
    }
}
