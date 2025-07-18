package net.icdcyborg.mavenDependencyTracker.data

import com.google.common.truth.Truth.assertThat
import net.icdcyborg.mavenDependencyTracker.domain.Dependency
import net.icdcyborg.mavenDependencyTracker.domain.ParentData
import org.junit.jupiter.api.Test

class PomParserTest {

    private val parser = PomParser()

    @Test
    fun `parse should correctly parse basic POM data`() {
        val xmlString = """
            <project>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
            </project>
        """.trimIndent()

        val result = parser.parse(xmlString)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            println("error message: $error")
        }
        assertThat(result.isSuccess).isTrue()
        val pomData = result.getOrThrow()
        assertThat(pomData.groupId).isEqualTo("com.example")
        assertThat(pomData.artifactId).isEqualTo("my-app")
        assertThat(pomData.version).isEqualTo("1.0.0")
        assertThat(pomData.parent).isNull()
        assertThat(pomData.dependencies).isEmpty()
        assertThat(pomData.properties).isEmpty()
        assertThat(pomData.dependencyManagement).isEmpty()
    }

    @Test
    fun `parse should correctly parse POM with parent`() {
        val xmlString = """
            <project>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.7.0</version>
                </parent>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
            </project>
        """.trimIndent()

        val result = parser.parse(xmlString)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            println("error message: $error")
        }
        assertThat(result.isSuccess).isTrue()
        val pomData = result.getOrThrow()
        assertThat(pomData.groupId).isEqualTo("com.example")
        assertThat(pomData.artifactId).isEqualTo("my-app")
        assertThat(pomData.version).isEqualTo("1.0.0")
        assertThat(pomData.parent).isEqualTo(ParentData("org.springframework.boot", "spring-boot-starter-parent", "2.7.0"))
    }

    @Test
    fun `parse should correctly parse POM with dependencies`() {
        val xmlString = """
            <project>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>4.13.2</version>
                        <scope>test</scope>
                        <optional>true</optional>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlinx</groupId>
                        <artifactId>kotlinx-coroutines-core</artifactId>
                        <version>1.6.0</version>
                    </dependency>
                </dependencies>
            </project>
        """.trimIndent()

        val result = parser.parse(xmlString)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            println("error message: $error")
        }
        assertThat(result.isSuccess).isTrue()
        val pomData = result.getOrThrow()
        assertThat(pomData.dependencies).hasSize(2)
        assertThat(pomData.dependencies[0]).isEqualTo(Dependency("junit", "junit", "4.13.2", "test", true))
        assertThat(pomData.dependencies[1]).isEqualTo(Dependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.6.0", null, false))
    }

    @Test
    fun `parse should correctly parse POM with properties`() {
        val xmlString = """
            <project>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <properties>
                    <kotlin.version>1.6.0</kotlin.version>
                    <spring.version>5.3.18</spring.version>
                </properties>
            </project>
        """.trimIndent()

        val result = parser.parse(xmlString)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            println("error message: $error")
        }
        assertThat(result.isSuccess).isTrue()
        val pomData = result.getOrThrow()
        assertThat(pomData.properties).hasSize(2)
        assertThat(pomData.properties["kotlin.version"]).isEqualTo("1.6.0")
        assertThat(pomData.properties["spring.version"]).isEqualTo("5.3.18")
    }

    @Test
    fun `parse should correctly parse POM with dependencyManagement`() {
        val xmlString = """
            <project>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
                <version>1.0.0</version>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>2.7.0</version>
                            <scope>import</scope>
                            <type>pom</type>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """.trimIndent()

        val result = parser.parse(xmlString)
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            println("error message: $error")
        }
        assertThat(result.isSuccess).isTrue()
        val pomData = result.getOrThrow()
        assertThat(pomData.dependencyManagement).hasSize(1)
        assertThat(pomData.dependencyManagement[0]).isEqualTo(Dependency("org.springframework.boot", "spring-boot-dependencies", "2.7.0", "import", false))
    }

    @Test
    fun `parse should return failure for invalid XML`() {
        val xmlString = "<invalid-xml>"

        val result = parser.parse(xmlString)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `parse should return failure if required fields are missing`() {
        val xmlString = """
            <project>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
            </project>
        """.trimIndent()

        val result = parser.parse(xmlString)

        assertThat(result.isFailure).isTrue()
    }
}