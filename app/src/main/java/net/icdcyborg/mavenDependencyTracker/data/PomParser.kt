package net.icdcyborg.mavenDependencyTracker.data

import com.ctc.wstx.stax.WstxInputFactory
import com.ctc.wstx.stax.WstxOutputFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlFactory
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.icdcyborg.mavenDependencyTracker.domain.PomData

/**
 * POMのXML文字列を解析し、[PomData]オブジェクトを生成するクラスです。
 */
class PomParser {
    private val xmlMapper: XmlMapper =
        XmlMapper
            .builder(XmlFactory(WstxInputFactory(), WstxOutputFactory()))
            .defaultUseWrapper(false)
            .build()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) as XmlMapper

    /**
     * XML文字列を解析し、[PomData]オブジェクトを返します。
     *
     * @param xmlString 解析するPOMのXML文字列。
     * @return 成功した場合は[PomData]を含む[Result.success]、失敗した場合は例外を含む[Result.failure]。
     */
    fun parse(xmlString: String): Result<PomData> =
        try {
            val pomData = xmlMapper.readValue(xmlString, PomData::class.java)
            val finalGroupId = pomData.groupId ?: pomData.parent?.groupId
            val finalVersion = pomData.version ?: pomData.parent?.version

            if (finalGroupId == null || pomData.artifactId == null || finalVersion == null) {
                Result.failure(IllegalStateException("Missing required POM fields: groupId, artifactId, or version"))
            } else {
                Result.success(
                    pomData.copy(
                        groupId = finalGroupId,
                        version = finalVersion,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
}
