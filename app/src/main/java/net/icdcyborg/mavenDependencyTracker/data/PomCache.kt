package net.icdcyborg.mavenDependencyTracker.data

import java.util.concurrent.ConcurrentHashMap

/**
 * POMのXML文字列をメモリ上にキャッシュするクラスです。
 * キーとしてMaven座標（String）、値としてXML文字列（String）を保持します。
 */
class PomCache {
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * 指定されたMaven座標に対応するPOMのXML文字列をキャッシュから取得します。
     *
     * @param coordinate 取得するPOMのMaven座標。
     * @return キャッシュに存在するXML文字列、存在しない場合はnull。
     */
    fun get(coordinate: String): String? {
        return cache[coordinate]
    }

    /**
     * 指定されたMaven座標とPOMのXML文字列をキャッシュに保存します。
     *
     * @param coordinate 保存するPOMのMaven座標。
     * @param xmlString 保存するXML文字列。
     */
    fun put(coordinate: String, xmlString: String) {
        cache[coordinate] = xmlString
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
