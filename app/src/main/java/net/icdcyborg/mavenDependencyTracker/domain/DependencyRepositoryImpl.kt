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
    override fun resolveDependencies(coordinate: String): Flow<String> =
        flow {
            emitAll(resolveRecursive(coordinate, mutableSetOf<String>()))
        }

    override fun getPom(coordinate: String): Flow<String> =
        flow {
            val pomXmlResult = pomDataSource.getPomXml(coordinate)
            pomXmlResult.onSuccess { pomXml ->
                emit(pomXml)
            }
        }

    override suspend fun checkJarExists(coordinate: String): Boolean = pomDataSource.checkJarExists(coordinate)

    private suspend fun resolveRecursive(
        coordinate: String,
        resolvedDependencies: MutableSet<String>,
    ): Flow<String> =
        flow {
            // バリデーションに失敗したらそのまま出力して中断
            val regex = "^[a-zA-Z0-9.-]+:[a-zA-Z0-9.-]+:[a-zA-Z0-9.-]+".toRegex()
            if (!regex.matches(coordinate)) {
                emit(coordinate)
                resolvedDependencies.add(coordinate)
                return@flow
            }
            // 座標が解決済み（循環参照または依存関係の重複）ならストップ
            if (resolvedDependencies.contains(coordinate)) {
                return@flow
            }

            val parentChainResult = resolveParentChain(coordinate)
            val pomDataList = parentChainResult.getOrThrow()
            if (pomDataList.isEmpty()) return@flow

            // 親チェーンの各POMをemit
            pomDataList.forEach { pomData ->
                val currentCoordinate = pomData.coordinate
                if (!resolvedDependencies.contains(currentCoordinate)) {
                    emit(currentCoordinate)
                    resolvedDependencies.add(currentCoordinate)
                }
            }

            val mergedPomData = mergePomData(pomDataList)

            val mergedCoordinate = mergedPomData.coordinate
            if (!resolvedDependencies.contains(mergedCoordinate)) {
                emit(mergedCoordinate)
                resolvedDependencies.add(mergedCoordinate)
            }
            println("pom merged. $mergedPomData")

            val dependencies =
                mergedPomData.dependencies
                    .orEmpty()
                    .map { resolveVersionFromDependencyManagement(it, mergedPomData.dependencyManagement) }
                    .map { resolveProperties(it, mergedPomData) }
                    .filterNot { it.scope == "test" || it.scope == "provided" || it.optional == true }
                    .filterNot { it.groupId.isNullOrBlank() || it.artifactId.isNullOrBlank() || it.version.isNullOrBlank() }

            for (dependency in dependencies) {
                val depCoordinate = dependency.coordinate
                emitAll(resolveRecursive(depCoordinate, resolvedDependencies))
            }
        }

    private suspend fun resolveParentChain(
        coordinate: String,
        resolvedParents: MutableSet<String> = mutableSetOf(),
    ): Result<List<PomData>> {
        // 座標が既に親子関係に含まれる（循環参照）ならエラー返却
        if (resolvedParents.contains(coordinate)) {
            return Result.failure(CircularReferenceException("Circular reference detected for $coordinate"))
        }
        resolvedParents.add(coordinate)

        val pomXmlResult = pomDataSource.getPomXml(coordinate)

        val pomDataResult =
            pomXmlResult.mapCatching {
                pomParser.parse(it).getOrThrow()
            }

        pomDataResult.onFailure { return Result.failure(it) }
        val pomData = pomDataResult.getOrThrow()
        println("PomData successfully parsed. $pomData")

        return if (
            pomData.parent != null
        ) {
            val parentCoordinate = pomData.parent.coordinate
            resolveParentChain(parentCoordinate, resolvedParents).mapCatching {
                listOf(pomData) + it
            }
        } else {
            Result.success(listOf(pomData))
        }
    }

    private fun mergePomData(pomDataList: List<PomData>): PomData =
        pomDataList.reduce { child, parent ->
            val mergedProperties = (child.properties?.map.orEmpty()) + (parent.properties?.map.orEmpty())

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

    private fun resolveProperties(
        dependency: Dependency,
        pomData: PomData,
    ): Dependency =
        dependency.copy(
            groupId = resolveProperty(dependency.groupId, pomData),
            artifactId = resolveProperty(dependency.artifactId, pomData),
            version = resolveProperty(dependency.version, pomData),
        )

    private fun resolveProperty(
        value: String?,
        pomData: PomData,
    ): String? {
        if (value == null || !value.startsWith("\${") || !value.endsWith("}")) {
            return value
        }
        println("resolving property: $value")
        val properties = pomData.properties?.map
        return when (val key = value.substring(2, value.length - 1)) {
            "project.version" -> pomData.version
            "project.groupId" -> pomData.groupId
            "project.artifactId" -> pomData.artifactId
            else -> properties?.get(key) ?: value
        }
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

    override suspend fun getUrlFromCoordinate(coordinate: String): String? = pomDataSource.getUrlFromCoordinate(coordinate, ".pom")
}
