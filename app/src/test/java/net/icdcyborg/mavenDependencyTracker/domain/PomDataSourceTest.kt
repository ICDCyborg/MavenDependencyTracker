package net.icdcyborg.mavenDependencyTracker.domain

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.icdcyborg.mavenDependencyTracker.data.MavenRemoteDataSource
import net.icdcyborg.mavenDependencyTracker.data.PomCache
import net.icdcyborg.mavenDependencyTracker.data.PomDataSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PomDataSourceTest {

    private lateinit var pomCache: PomCache
    private lateinit var mavenRemoteDataSource: MavenRemoteDataSource
    private lateinit var pomDataSource: PomDataSource

    @BeforeEach
    fun setUp() {
        pomCache = mockk(relaxed = true)
        mavenRemoteDataSource = mockk()
        pomDataSource = PomDataSource(pomCache, mavenRemoteDataSource)
    }

    @Test
    fun `getPomXml should return cached XML when available`() = runTest {
        val coordinate = "a:b:c"
        val cachedXml = "<project></project>"
        coEvery { pomCache.get(coordinate) } returns cachedXml

        val result = pomDataSource.getPomXml(coordinate)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(cachedXml)
        coVerify(exactly = 0) { mavenRemoteDataSource.getPomXml(any()) }
    }

    @Test
    fun `getPomXml should fetch from remote and cache when not in cache`() = runTest {
        val coordinate = "a:b:c"
        val remoteXml = "<project></project>"
        coEvery { pomCache.get(coordinate) } returns null
        coEvery { mavenRemoteDataSource.getPomXml(coordinate) } returns Result.success(remoteXml)

        val result = pomDataSource.getPomXml(coordinate)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(remoteXml)
        coVerify { pomCache.put(coordinate, remoteXml) }
    }

    @Test
    fun `getPomXml should return failure when remote fetch fails`() = runTest {
        val coordinate = "a:b:c"
        val exception = RuntimeException("Failed to fetch")
        coEvery { pomCache.get(coordinate) } returns null
        coEvery { mavenRemoteDataSource.getPomXml(coordinate) } returns Result.failure(exception)

        val result = pomDataSource.getPomXml(coordinate)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isEqualTo(exception)
        coVerify(exactly = 0) { pomCache.put(any(), any()) }
    }
}
