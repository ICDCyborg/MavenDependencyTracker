package net.icdcyborg.mavenDependencyTracker.data

import com.google.common.truth.Truth.assertThat
import net.icdcyborg.mavenDependencyTracker.domain.PomData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PomCacheTest {

    private lateinit var pomCache: PomCache

    @BeforeEach
    fun setUp() {
        pomCache = PomCache()
    }

    @Test
    fun `put and get should store and retrieve PomData`() {
        val coordinate = "group:artifact:version"
        val pomData = PomData("group", "artifact", "version", null, emptyList(), propertiesSection = null)

        pomCache.put(coordinate, pomData)
        val retrievedPomData = pomCache.get(coordinate)

        assertThat(retrievedPomData).isEqualTo(pomData)
    }

    @Test
    fun `get should return null if coordinate not in cache`() {
        val coordinate = "nonexistent:coordinate:1.0"
        val retrievedPomData = pomCache.get(coordinate)

        assertThat(retrievedPomData).isNull()
    }

    @Test
    fun `contains should return true if coordinate in cache`() {
        val coordinate = "group:artifact:version"
        val pomData = PomData("group", "artifact", "version", null, emptyList(), propertiesSection = null)

        pomCache.put(coordinate, pomData)

        assertThat(pomCache.contains(coordinate)).isTrue()
    }

    @Test
    fun `contains should return false if coordinate not in cache`() {
        val coordinate = "nonexistent:coordinate:1.0"

        assertThat(pomCache.contains(coordinate)).isFalse()
    }
}
