package net.icdcyborg.mavenDependencyTracker.data

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PomCacheTest {

    private lateinit var pomCache: PomCache

    @BeforeEach
    fun setUp() {
        pomCache = PomCache()
    }

    @Test
    fun `put and get should store and retrieve pom xml`() {
        val coordinate = "group:artifact:version"
        val pomXml = "<project></project>"

        pomCache.put(coordinate, pomXml)
        val retrievedPomXml = pomCache.get(coordinate)

        assertThat(retrievedPomXml).isEqualTo(pomXml)
    }

    @Test
    fun `get should return null if coordinate not in cache`() {
        val coordinate = "nonexistent:coordinate:1.0"
        val retrievedPomXml = pomCache.get(coordinate)

        assertThat(retrievedPomXml).isNull()
    }

    @Test
    fun `contains should return true if coordinate in cache`() {
        val coordinate = "group:artifact:version"
        val pomXml = "<project></project>"

        pomCache.put(coordinate, pomXml)

        assertThat(pomCache.contains(coordinate)).isTrue()
    }

    @Test
    fun `contains should return false if coordinate not in cache`() {
        val coordinate = "nonexistent:coordinate:1.0"

        assertThat(pomCache.contains(coordinate)).isFalse()
    }
}
