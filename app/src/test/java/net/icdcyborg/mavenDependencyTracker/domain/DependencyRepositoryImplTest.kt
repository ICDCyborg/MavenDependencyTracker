package net.icdcyborg.mavenDependencyTracker.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.icdcyborg.mavenDependencyTracker.data.PomDataSource
import net.icdcyborg.mavenDependencyTracker.data.PomParser
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DependencyRepositoryImplTest {

    private val pomDataSource: PomDataSource = mockk()
    private val pomParser: PomParser = mockk()

    private val repository = DependencyRepositoryImpl(pomDataSource, pomParser)

    @Nested
    inner class ResolveDependencies {
        @Test
        fun `should return flow of dependencies`() = runTest {
            val coordinate = "a:b:c"
            val pomXml = "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version></project>"

            coEvery { pomDataSource.getPomXml(coordinate) } returns Result.success(pomXml)
            coEvery { pomParser.parse(pomXml) } returns Result.success(PomData("a", "b", "c", null, emptyList(), null))

            repository.resolveDependencies(coordinate).test {
                assertThat(awaitItem()).isEqualTo("a:b:c")
                awaitComplete()
            }
        }

        @Test
        fun `should resolve single dependency`() = runTest {
            val rootCoordinate = "a:b:c"
            val depCoordinate = "d:e:f"
            val rootPomXml = "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version></dependency></dependencies></project>"
            val depPomXml = "<project><groupId>d</groupId><artifactId>e</artifactId><version>f</version></project>"

            coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
            coEvery { pomParser.parse(rootPomXml) } returns Result.success(PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f")), null))
            coEvery { pomDataSource.getPomXml(depCoordinate) } returns Result.success(depPomXml)
            coEvery { pomParser.parse(depPomXml) } returns Result.success(PomData("d", "e", "f", null, emptyList(), null))

            repository.resolveDependencies(rootCoordinate).test {
                assertThat(awaitItem()).isEqualTo("a:b:c")
                assertThat(awaitItem()).isEqualTo("d:e:f")
                awaitComplete()
            }
        }

        @Test
        fun `should resolve transitive dependency`() = runTest {
            val rootCoordinate = "a:b:c"
            val dep1Coordinate = "d:e:f"
            val dep2Coordinate = "g:h:i"
            val rootPomXml = "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version></dependency></dependencies></project>"
            val dep1PomXml = "<project><groupId>d</groupId><artifactId>e</artifactId><version>f</version><dependencies><dependency><groupId>g</groupId><artifactId>h</artifactId><version>i</version></dependency></dependencies></project>"
            val dep2PomXml = "<project><groupId>g</groupId><artifactId>h</artifactId><version>i</version></project>"

            coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
            coEvery { pomParser.parse(rootPomXml) } returns Result.success(PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f")), null))
            coEvery { pomDataSource.getPomXml(dep1Coordinate) } returns Result.success(dep1PomXml)
            coEvery { pomParser.parse(dep1PomXml) } returns Result.success(PomData("d", "e", "f", null, listOf(Dependency("g", "h", "i")), null))
            coEvery { pomDataSource.getPomXml(dep2Coordinate) } returns Result.success(dep2PomXml)
            coEvery { pomParser.parse(dep2PomXml) } returns Result.success(PomData("g", "h", "i", null, emptyList(), null))

            repository.resolveDependencies(rootCoordinate).test {
                assertThat(awaitItem()).isEqualTo("a:b:c")
                assertThat(awaitItem()).isEqualTo("d:e:f")
                assertThat(awaitItem()).isEqualTo("g:h:i")
                awaitComplete()
            }
        }

        @Test
        fun `should not resolve optional dependency`() = runTest {
            val rootCoordinate = "a:b:c"
            val depCoordinate = "d:e:f"
            val rootPomXml = "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version><optional>true</optional></dependency></dependencies></project>"

            coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
            coEvery { pomParser.parse(rootPomXml) } returns Result.success(PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f", optional = true)), null))

            repository.resolveDependencies(rootCoordinate).test {
                assertThat(awaitItem()).isEqualTo("a:b:c")
                awaitComplete()
            }
        }

        @Test
        fun `should not resolve already resolved dependency`() = runTest {
            val rootCoordinate = "a:b:c"
            val depCoordinate = "d:e:f"
            val rootPomXml = "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version></dependency></dependencies></project>"
            val depPomXml = "<project><groupId>d</groupId><artifactId>e</artifactId><version>f</version></project>"
            val repository = DependencyRepositoryImpl(pomDataSource, pomParser)

            coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
            coEvery { pomParser.parse(rootPomXml) } returns Result.success(PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f")), null))
            coEvery { pomDataSource.getPomXml(depCoordinate) } returns Result.success(depPomXml)
            coEvery { pomParser.parse(depPomXml) } returns Result.success(PomData("d", "e", "f", null, emptyList(), null))

            repository.resolveDependencies(rootCoordinate).test {
                assertThat(awaitItem()).isEqualTo("a:b:c")
                assertThat(awaitItem()).isEqualTo("d:e:f")
                awaitComplete()
            }

            // Call again and expect an empty flow because all dependencies are already resolved.
            repository.resolveDependencies(rootCoordinate).test {
                awaitComplete()
            }
        }

        @Test
        fun `should emit error when pom fetch fails`() = runTest {
            val coordinate = "a:b:c"
            val exception = RuntimeException("Failed to fetch")

            coEvery { pomDataSource.getPomXml(coordinate) } returns Result.failure(exception)

            repository.resolveDependencies(coordinate).test {
                assertThat(awaitError()).isEqualTo(exception)
            }
        }

        @Test
        fun `should emit error when pom parsing fails`() = runTest {
            val coordinate = "a:b:c"
            val pomXml = "<project></project>"
            val exception = RuntimeException("Failed to parse")

            coEvery { pomDataSource.getPomXml(coordinate) } returns Result.success(pomXml)
            coEvery { pomParser.parse(pomXml) } returns Result.failure(exception)

            repository.resolveDependencies(coordinate).test {
                assertThat(awaitError()).isEqualTo(exception)
            }
        }
    }
}
