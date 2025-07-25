package net.icdcyborg.mavenDependencyTracker.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import net.icdcyborg.mavenDependencyTracker.data.PomDataSource
import net.icdcyborg.mavenDependencyTracker.data.PomParser
import java.lang.Exception
import java.lang.IllegalArgumentException

class CircularReferenceException(
    message: String,
) : Exception(message)

/**
 * [DependencyRepository]インターフェースの実装クラスです。
 * Mavenの依存関係を解決し、推移的な依存関係を追跡します。
 *
 * @param pomDataSource POMデータを取得するための[PomDataSource]インスタンス。
 * @param pomParser POMのXML文字列を解析するための[PomParser]インスタンス。
 */
class DependencyRepositoryImpl(
    private val pomDataSource: PomDataSource,
    private val pomParser: PomParser,
) : DependencyRepository {
    private val resolvedDependencies = mutableSetOf<String>()

    override fun resolveDependencies(coordinate: String): Flow<String> =
        flow {
            emitAll(resolveRecursive(coordinate, resolvedDependencies))
        }

    private suspend fun resolveRecursive(
        coordinate: String,
        resolvedDependencies: MutableSet<String>,
    ): Flow<String> =
        flow {
            if (resolvedDependencies.contains(coordinate)) {
                return@flow
            }

            val parentChainResult = resolveParentChain(coordinate)
            parentChainResult.onFailure {
                throw it // Propagate error to the collector
            }

            val pomDataList = parentChainResult.getOrThrow()
            if (pomDataList.isEmpty()) return@flow
            resolvedDependencies.add(coordinate)

            val mergedPomData = mergePomData(pomDataList)

            val currentCoordinate = "${mergedPomData.groupId}:${mergedPomData.artifactId}:${mergedPomData.version}"
            emit(currentCoordinate)

            val dependencies =
                mergedPomData.dependencies
                    .orEmpty()
                    .map { resolveProperties(it, mergedPomData.properties) }
                    .map { resolveVersionFromDependencyManagement(it, mergedPomData.dependencyManagement) }
                    .filterNot { it.scope == "test" || it.scope == "provided" || it.optional == true }
                    .filterNot { it.groupId.isNullOrBlank() || it.artifactId.isNullOrBlank() || it.version.isNullOrBlank() }

            for (dependency in dependencies) {
                val depCoordinate = "${dependency.groupId}:${dependency.artifactId}:${dependency.version}"
                emitAll(resolveRecursive(depCoordinate, resolvedDependencies))
            }
        }

    private suspend fun resolveParentChain(
        coordinate: String,
        resolvedParents: MutableSet<String> = mutableSetOf(),
    ): Result<List<PomData>> {
        if (resolvedParents.contains(coordinate)) {
            return Result.failure(CircularReferenceException("Circular reference detected for $coordinate"))
        }
        resolvedParents.add(coordinate)

        val pomXmlResult = pomDataSource.getPomXml(coordinate)

        val pomDataResult = pomXmlResult.mapCatching {
            pomParser.parse(it).getOrThrow()
        }

        pomDataResult.onFailure { return Result.failure(it) }
        val pomData = pomDataResult.getOrThrow()

        return if (
            pomData.parent != null &&
            "${pomData.parent.groupId}:${pomData.parent.artifactId}:${pomData.parent.version}" != coordinate
        ) {
            val parentCoordinate = "${pomData.parent.groupId}:${pomData.parent.artifactId}:${pomData.parent.version}"
            resolveParentChain(parentCoordinate, resolvedParents).map { parentChainList ->
                parentChainList + pomData
            }
        } else {
            Result.success(listOf(pomData))
        }
    }

    private fun mergePomData(pomDataList: List<PomData>): PomData {
        if (pomDataList.isEmpty()) {
            throw IllegalArgumentException("pomDataList cannot be empty")
        }
        return pomDataList.reduce { parent, child ->
            val mergedProperties = (parent.properties?.map.orEmpty()) + (child.properties?.map.orEmpty())

            val parentDepManagement = parent.dependencyManagement?.dependencies.orEmpty()
            val childDepManagement = child.dependencyManagement?.dependencies.orEmpty()
            val finalDepManagementMap = mutableMapOf<Pair<String?, String?>, Dependency>()
            parentDepManagement.forEach { finalDepManagementMap[it.groupId to it.artifactId] = it }
            childDepManagement.forEach { finalDepManagementMap[it.groupId to it.artifactId] = it }

            val parentDeps = parent.dependencies.orEmpty()
            val childDeps = child.dependencies.orEmpty()
            val finalDepsMap = mutableMapOf<Pair<String?, String?>, Dependency>()
            parentDeps.forEach { finalDepsMap[it.groupId to it.artifactId] = it }
            childDeps.forEach { finalDepsMap[it.groupId to it.artifactId] = it }

            PomData(
                groupId = child.groupId ?: parent.groupId,
                artifactId = child.artifactId ?: parent.artifactId,
                version = child.version ?: parent.version,
                parent = child.parent,
                properties = PropertiesSection(mergedProperties.toMutableMap()),
                dependencyManagement = DependencyManagementSection(finalDepManagementMap.values.toList()),
                dependencies = finalDepsMap.values.toList(),
            )
        }
    }

    private fun resolveProperties(
        dependency: Dependency,
        properties: PropertiesSection?,
    ): Dependency {
        if (properties == null) return dependency
        val props = properties.map
        return dependency.copy(
            groupId = resolveProperty(dependency.groupId, props),
            artifactId = resolveProperty(dependency.artifactId, props),
            version = resolveProperty(dependency.version, props),
            scope = resolveProperty(dependency.scope, props),
        )
    }

    private fun resolveProperty(
        value: String?,
        properties: Map<String, String>,
    ): String? {
        if (value == null || !value.startsWith("\${") || !value.endsWith("}")) {
            return value
        }
        val key = value.substring(2, value.length - 1)
        return properties[key] ?: value
    }

    private fun resolveVersionFromDependencyManagement(
        dependency: Dependency,
        dependencyManagement: DependencyManagementSection?,
    ): Dependency {
        if (dependency.version != null) {
            return dependency
        }
        val managedVersion =
            dependencyManagement
                ?.dependencies
                .orEmpty()
                .find { it.groupId == dependency.groupId && it.artifactId == dependency.artifactId }
                ?.version
        return dependency.copy(version = managedVersion)
    }
}

