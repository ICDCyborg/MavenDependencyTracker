package net.icdcyborg.mavenDependencyTracker.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.icdcyborg.mavenDependencyTracker.data.MavenRemoteDataSource
import net.icdcyborg.mavenDependencyTracker.data.PomCache
import net.icdcyborg.mavenDependencyTracker.data.PomParser
import org.junit.jupiter.api.Test

class DependencyRepositoryImplTest {

    private val pomCache: PomCache = mockk(relaxed = true)
    private val mavenRemoteDataSource: MavenRemoteDataSource = mockk()
    private val pomParser: PomParser = mockk()

    private val repository = DependencyRepositoryImpl(pomCache, mavenRemoteDataSource, pomParser)

    @Test
    fun `resolveDependencies should return flow of dependencies`() = runTest {
        val coordinate = "a:b:c"
        val pomXml = "<project></project>"
        val pomData = PomData("a", "b", "c", null, emptyList(), properties = null)

        coEvery { mavenRemoteDataSource.getPomXml(coordinate) } returns Result.success(pomXml)
        coEvery { pomParser.parse(pomXml) } returns Result.success(pomData)

        repository.resolveDependencies(coordinate).test {
            assertThat(awaitItem()).isEqualTo("a:b:c")
            awaitComplete()
        }
    }
}
