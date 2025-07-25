package net.icdcyborg.mavenDependencyTracker.domain

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * 解析されたPOMファイルの情報（groupId, artifactId, version, parent, dependencies, propertiesなど）を保持するデータクラスです。
 */
@JacksonXmlRootElement(localName = "project")
data class PomData(
    @JsonProperty("groupId")
    val groupId: String? = null,
    @JsonProperty("artifactId")
    val artifactId: String? = null,
    @JsonProperty("version")
    val version: String? = null,
    @JacksonXmlProperty(localName = "parent")
    val parent: ParentData? = null,
    @JacksonXmlElementWrapper(useWrapping = true)
    @JacksonXmlProperty(localName = "dependencies")
    val dependencies: List<Dependency>? = emptyList(),
    @JacksonXmlProperty(localName = "properties")
    val properties: PropertiesSection? = null,
    @JacksonXmlProperty(localName = "dependencyManagement")
    val dependencyManagement: DependencyManagementSection? = null,
) {
    val coordinate: String get() = "$groupId:$artifactId:$version"
}

data class PropertiesSection(
    val map: MutableMap<String, String> = mutableMapOf(),
) {
    @JsonAnySetter
    fun set(
        name: String,
        value: String,
    ) {
        map[name] = value
    }

    fun get() {
        map
    }

    operator fun get(key: String): String? = map[key]

    val length: Int get() = map.size
}

data class DependencyManagementSection(
    @JacksonXmlElementWrapper(useWrapping = true)
    @JacksonXmlProperty(localName = "dependencies")
    val dependencies: List<Dependency>? = emptyList(),
)

/**
 * 親POMの情報（groupId, artifactId, version）を保持するデータクラスです。
 */
data class ParentData(
    @JsonProperty("groupId")
    val groupId: String,
    @JsonProperty("artifactId")
    val artifactId: String,
    @JsonProperty("version")
    val version: String,
) {
    val coordinate: String get() = "$groupId:$artifactId:$version"
}

/**
 * 依存関係の情報（groupId, artifactId, version, scope, optional）を保持するデータクラスです。
 */
data class Dependency(
    @JsonProperty("groupId")
    val groupId: String?,
    @JsonProperty("artifactId")
    val artifactId: String?,
    @JacksonXmlProperty(localName = "version")
    val version: String? = null,
    @JacksonXmlProperty(localName = "scope")
    val scope: String? = null,
    @JacksonXmlProperty(localName = "optional")
    val optional: Boolean? = null,
)
