package net.icdcyborg.mavenDependencyTracker.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay

/**
 * リモートのMavenリポジトリからPOMファイルを取得するためのデータソースクラスです。
 *
 * @param httpClient Ktorの[HttpClient]インスタンス。
 */
class MavenRemoteDataSource(private val httpClient: HttpClient) {

    private val BASE_URL = "https://repo1.maven.org/maven2/"

    /**
     * 指定されたMaven座標に対応するPOMファイルのXML文字列を取得します。
     * リクエストが失敗した場合、1秒間隔で1回リトライします。
     *
     * @param coordinate 取得するPOMファイルのMaven座標 (例: "group:artifact:version")。
     * @return 成功した場合はXML文字列を含む[Result.success]、失敗した場合は例外を含む[Result.failure]。
     */
    suspend fun getPomXml(coordinate: String): Result<String> {
        val parts = coordinate.split(":")
        if (parts.size != 3) {
            return Result.failure(IllegalArgumentException("Invalid coordinate format"))
        }
        val groupId = parts[0]
        val artifactId = parts[1]
        val version = parts[2]

        val path = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version.pom"
        val url = "$BASE_URL$path"

        return try {
            val response: HttpResponse = httpClient.get(url)
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body<String>())
            } else {
                // Retry once after 1 second
                delay(1000)
                val retryResponse: HttpResponse = httpClient.get(url)
                if (retryResponse.status == HttpStatusCode.OK) {
                    Result.success(retryResponse.body<String>())
                } else {
                    Result.failure(Exception("Failed to fetch POM for $coordinate: ${retryResponse.status.value}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
