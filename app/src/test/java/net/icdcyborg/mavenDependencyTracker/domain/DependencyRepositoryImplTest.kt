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
        fun `should return flow of dependencies`() =
            runTest {
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
        fun `should resolve single dependency`() =
            runTest {
                val rootCoordinate = "a:b:c"
                val depCoordinate = "d:e:f"
                val rootPomXml =
                    "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version></dependency></dependencies></project>"
                val depPomXml = "<project><groupId>d</groupId><artifactId>e</artifactId><version>f</version></project>"

                coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
                coEvery { pomParser.parse(rootPomXml) } returns
                    Result.success(PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f")), null))
                coEvery { pomDataSource.getPomXml(depCoordinate) } returns Result.success(depPomXml)
                coEvery { pomParser.parse(depPomXml) } returns Result.success(PomData("d", "e", "f", null, emptyList(), null))

                repository.resolveDependencies(rootCoordinate).test {
                    assertThat(awaitItem()).isEqualTo("a:b:c")
                    assertThat(awaitItem()).isEqualTo("d:e:f")
                    awaitComplete()
                }
            }

        @Test
        fun `should resolve transitive dependency`() =
            runTest {
                val rootCoordinate = "a:b:c"
                val dep1Coordinate = "d:e:f"
                val dep2Coordinate = "g:h:i"
                val rootPomXml =
                    "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version></dependency></dependencies></project>"
                val dep1PomXml =
                    "<project><groupId>d</groupId><artifactId>e</artifactId><version>f</version><dependencies><dependency><groupId>g</groupId><artifactId>h</artifactId><version>i</version></dependency></dependencies></project>"
                val dep2PomXml = "<project><groupId>g</groupId><artifactId>h</artifactId><version>i</version></project>"

                coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
                coEvery { pomParser.parse(rootPomXml) } returns
                    Result.success(PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f")), null))
                coEvery { pomDataSource.getPomXml(dep1Coordinate) } returns Result.success(dep1PomXml)
                coEvery { pomParser.parse(dep1PomXml) } returns
                    Result.success(PomData("d", "e", "f", null, listOf(Dependency("g", "h", "i")), null))
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
        fun `should not resolve optional dependency`() =
            runTest {
                val rootCoordinate = "a:b:c"
                val rootPomXml =
                    "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version><optional>true</optional></dependency></dependencies></project>"

                coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
                coEvery { pomParser.parse(rootPomXml) } returns
                    Result.success(
                        PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f", optional = true)), null),
                    )

                repository.resolveDependencies(rootCoordinate).test {
                    assertThat(awaitItem()).isEqualTo("a:b:c")
                    awaitComplete()
                }
            }

        @Test
        fun `should not resolve already resolved dependency`() =
            runTest {
                val rootCoordinate = "a:b:c"
                val depCoordinate = "d:e:f"
                val rootPomXml =
                    "<project><groupId>a</groupId><artifactId>b</artifactId><version>c</version><dependencies><dependency><groupId>d</groupId><artifactId>e</artifactId><version>f</version></dependency></dependencies></project>"
                val depPomXml = "<project><groupId>d</groupId><artifactId>e</artifactId><version>f</version></project>"
                val repository = DependencyRepositoryImpl(pomDataSource, pomParser)

                coEvery { pomDataSource.getPomXml(rootCoordinate) } returns Result.success(rootPomXml)
                coEvery { pomParser.parse(rootPomXml) } returns
                    Result.success(PomData("a", "b", "c", null, listOf(Dependency("d", "e", "f")), null))
                coEvery { pomDataSource.getPomXml(depCoordinate) } returns Result.success(depPomXml)
                coEvery { pomParser.parse(depPomXml) } returns Result.success(PomData("d", "e", "f", null, emptyList(), null))

                repository.resolveDependencies(rootCoordinate).test {
                    assertThat(awaitItem()).isEqualTo("a:b:c")
                    assertThat(awaitItem()).isEqualTo("d:e:f")
                    awaitComplete()
                }
            }

        @Test
        fun `should emit error when pom fetch fails`() =
            runTest {
                val coordinate = "a:b:c"
                val exception = RuntimeException("Failed to fetch")

                coEvery { pomDataSource.getPomXml(coordinate) } returns Result.failure(exception)

                repository.resolveDependencies(coordinate).test {
                    assertThat(awaitError()).isEqualTo(exception)
                }
            }

        @Test
        fun `should emit error when pom parsing fails`() =
            runTest {
                val coordinate = "a:b:c"
                val pomXml = "<project></project>"
                val exception = RuntimeException("Failed to parse")

                coEvery { pomDataSource.getPomXml(coordinate) } returns Result.success(pomXml)
                coEvery { pomParser.parse(pomXml) } returns Result.failure(exception)

                repository.resolveDependencies(coordinate).test {
                    assertThat(awaitError()).isEqualTo(exception)
                }
            }

        @Test
        fun `should emit parent pom when resolving child with parent`() =
            runTest {
                val childCoordinate = "org.ow2.asm:asm:9.6"
                val parentCoordinate = "org.ow2:ow2:1.5.1"

                // Mock child POM XML and its parsed PomData
                val childPomXml = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.ow2.asm</groupId>
  <artifactId>asm</artifactId>
  <version>9.6</version>
  <name>asm</name>
  <description>ASM, a very small and fast Java bytecode manipulation framework</description>
  <url>http://asm.ow2.io/</url>
  <inceptionYear>2000</inceptionYear>
  <organization>
    <name>OW2</name>
    <url>http://www.ow2.org/</url>
  </organization>
  <licenses>
    <license>
      <name>BSD-3-Clause</name>
      <url>https://asm.ow2.io/license.html</url>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>ebruneton</id>
      <name>Eric Bruneton</name>
      <email>ebruneton@free.fr</email>
      <roles>
        <role>Creator</role>
        <role>Java Developer</role>
      </roles>
    </developer>
    <developer>
      <id>eu</id>
      <name>Eugene Kuleshov</name>
      <email>eu@javatx.org</email>
      <roles>
        <role>Java Developer</role>
      </roles>
    </developer>
    <developer>
      <id>forax</id>
      <name>Remi Forax</name>
      <email>forax@univ-mlv.fr</email>
      <roles>
        <role>Java Developer</role>
      </roles>
    </developer>
  </developers>
  <mailingLists>
    <mailingList>
      <name>ASM Users List</name>
      <subscribe>https://mail.ow2.org/wws/subscribe/asm</subscribe>
      <post>asm@objectweb.org</post>
      <archive>https://mail.ow2.org/wws/arc/asm/</archive>
    </mailingList>
    <mailingList>
      <name>ASM Team List</name>
      <subscribe>https://mail.ow2.org/wws/subscribe/asm-team</subscribe>
      <post>asm-team@objectweb.org</post>
      <archive>https://mail.ow2.org/wws/arc/asm-team/</archive>
    </mailingList>
  </mailingLists>
  <scm>
    <connection>scm:git:https://gitlab.ow2.org/asm/asm/</connection>
    <developerConnection>scm:git:https://gitlab.ow2.org/asm/asm/</developerConnection>
    <url>https://gitlab.ow2.org/asm/asm/</url>
  </scm>
  <issueManagement>
    <url>https://gitlab.ow2.org/asm/asm/issues</url>
  </issueManagement>
  <parent>
    <groupId>org.ow2</groupId>
    <artifactId>ow2</artifactId>
    <version>1.5.1</version>
  </parent>
</project>"""
                val childPomData =
                    PomData(
                        groupId = "org.ow2.asm",
                        artifactId = "asm",
                        version = "9.6",
                        parent =
                            ParentData(
                                groupId = "org.ow2",
                                artifactId = "ow2",
                                version = "1.5.1",
                            ),
                        dependencies = emptyList(),
                        properties = null,
                    )

                // Mock parent POM XML and its parsed PomData
                val parentPomXml = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.ow2</groupId>
    <artifactId>ow2</artifactId>
    <packaging>pom</packaging>
    <version>1.5.1</version>
    <name>OW2</name>
    <url>http://www.ow2.org</url>
</project>"""
                val parentPomData =
                    PomData(
                        groupId = "org.ow2",
                        artifactId = "ow2",
                        version = "1.5.1",
                        parent = null, // Assuming no further parent for simplicity
                        dependencies = emptyList(),
                        properties = null,
                    )

                coEvery { pomDataSource.getPomXml(childCoordinate) } returns Result.success(childPomXml)
                coEvery { pomParser.parse(childPomXml) } returns Result.success(childPomData)
                coEvery { pomDataSource.getPomXml(parentCoordinate) } returns Result.success(parentPomXml)
                coEvery { pomParser.parse(parentPomXml) } returns Result.success(parentPomData)

                repository.resolveDependencies(childCoordinate).test {
                    assertThat(awaitItem()).isEqualTo(childCoordinate)
                    assertThat(awaitItem()).isEqualTo(parentCoordinate)
                    awaitComplete()
                }
            }
    }
}
