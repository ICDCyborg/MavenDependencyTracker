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
    @JacksonXmlElementWrapper(localName = "dependencies")
    @JacksonXmlProperty(localName = "dependency")
    val dependencies: List<Dependency> = emptyList(),
    @JacksonXmlProperty(localName = "properties")
    val propertiesSection: PropertiesSection? = null,
    @JacksonXmlProperty(localName = "dependencyManagement")
    val dependencyManagementSection: DependencyManagementSection? = null
) {
    val properties: Map<String, String> get() = propertiesSection?.map ?: emptyMap()
    val dependencyManagement: List<Dependency> get() = dependencyManagementSection?.dependencies ?: emptyList()
}

data class PropertiesSection(
    val map: MutableMap<String, String> = mutableMapOf()
) {
    @JsonAnySetter
    fun set(name: String, value: String) {
        map[name] = value
    }
}

data class DependencyManagementSection(
    @JacksonXmlElementWrapper(localName = "dependencies")
    @JacksonXmlProperty(localName = "dependency")
    val dependencies: List<Dependency> = emptyList()
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
    val version: String
)

/**
 * 依存関係の情報（groupId, artifactId, version, scope, optional）を保持するデータクラスです。
 */
data class Dependency(
    @JsonProperty("groupId")
    val groupId: String,
    @JsonProperty("artifactId")
    val artifactId: String,
    @JsonProperty("version")
    val version: String? = null,
    @JsonProperty("scope")
    val scope: String? = null,
    @JsonProperty("optional")
    val optional: Boolean = false
)
