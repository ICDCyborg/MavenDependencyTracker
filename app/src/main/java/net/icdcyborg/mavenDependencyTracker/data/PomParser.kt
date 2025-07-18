package net.icdcyborg.mavenDependencyTracker.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.icdcyborg.mavenDependencyTracker.domain.PomData

/**
 * POMのXML文字列を解析し、[PomData]オブジェクトを生成するクラスです。
 */
class PomParser {

    private val xmlMapper = XmlMapper().apply {
        registerModule(KotlinModule.Builder().build())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    /**
     * XML文字列を解析し、[PomData]オブジェクトを返します。
     *
     * @param xmlString 解析するPOMのXML文字列。
     * @return 成功した場合は[PomData]を含む[Result.success]、失敗した場合は例外を含む[Result.failure]。
     */
    fun parse(xmlString: String): Result<PomData> {
        return try {
            val pomData = xmlMapper.readValue(xmlString, PomData::class.java)

            // Handle groupId and version inheritance from parent if not explicitly defined
            val finalGroupId = pomData.groupId ?: pomData.parent?.groupId
            val finalVersion = pomData.version ?: pomData.parent?.version

            if (finalGroupId == null || pomData.artifactId == null || finalVersion == null) {
                Result.failure(IllegalStateException("Missing required POM fields: groupId, artifactId, or version"))
            } else {
                Result.success(pomData.copy(groupId = finalGroupId, version = finalVersion))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}