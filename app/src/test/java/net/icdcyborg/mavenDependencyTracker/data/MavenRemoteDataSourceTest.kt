package net.icdcyborg.mavenDependencyTracker.data

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MavenRemoteDataSourceTest {

    @Test
    fun `getPomXml should return success when API call is successful`() = runTest {
        val expectedXml = "<project></project>"
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(expectedXml),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/xml")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dataSource = MavenRemoteDataSource(httpClient)

        val result = dataSource.getPomXml("group:artifact:version")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(expectedXml)
    }

    @Test
    fun `getPomXml should return failure when API call fails`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("Error"),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dataSource = MavenRemoteDataSource(httpClient)

        val result = dataSource.getPomXml("group:artifact:version")

        assertThat(result.isFailure).isTrue()
    }
}
