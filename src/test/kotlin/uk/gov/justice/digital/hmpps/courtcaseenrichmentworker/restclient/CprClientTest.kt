package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.netty.handler.timeout.ReadTimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.hmpps.kotlin.auth.unauthenticatedWebClient
import java.time.Duration

class CprClientTest {
  private val wireMock = WireMockServer(wireMockConfig().dynamicPort())
  private lateinit var client: CprClient

  @BeforeEach
  fun startServer() {
    wireMock.start()
    createClient()
  }

  @AfterEach
  fun stopServer() {
    wireMock.stop()
  }

  @Test
  fun `gets a common platform record and deserializes nested data`() {
    wireMock.stubFor(
      get(urlEqualTo("/person/commonplatform/defendant-123"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "cprUUID": "record-123",
                "firstName": "Alex",
                "title": {"code": "MR", "description": "Mr"},
                "identifiers": {"crns": ["X123456"]}
              }
              """.trimIndent(),
            ),
        ),
    )

    val result = client.getCommonPlatformPerson("defendant-123").block()

    assertEquals("record-123", result?.cprUUID)
    assertEquals("Alex", result?.firstName)
    assertEquals("MR", result?.title?.code)
    assertEquals(listOf("X123456"), result?.identifiers?.crns)
    wireMock.verify(getRequestedFor(urlEqualTo("/person/commonplatform/defendant-123")))
  }

  @Test
  fun `gets a libra record and returns empty when no record is found`() {
    wireMock.stubFor(
      get(urlEqualTo("/person/libra/12345"))
        .willReturn(aResponse().withStatus(404).withBody("""{"error":"not found"}""")),
    )

    val result = client.getLibraPerson("12345").block()

    assertNull(result)
    wireMock.verify(getRequestedFor(urlEqualTo("/person/libra/12345")))
  }

  @Test
  fun `empty successful CPR response is an error rather than no match`() {
    wireMock.stubFor(
      get(urlEqualTo("/person/commonplatform/defendant-123"))
        .willReturn(aResponse().withStatus(200)),
    )

    assertThrows(IllegalStateException::class.java) {
      client.getCommonPlatformPerson("defendant-123").block()
    }

    wireMock.verify(1, getRequestedFor(urlEqualTo("/person/commonplatform/defendant-123")))
  }

  @Test
  fun `retries server failures three times and propagates the final response error`() {
    wireMock.stubFor(
      get(urlEqualTo("/person/commonplatform/defendant-123"))
        .willReturn(aResponse().withStatus(503).withBody("temporarily unavailable")),
    )

    assertThrows(WebClientResponseException.ServiceUnavailable::class.java) {
      client.getCommonPlatformPerson("defendant-123").block()
    }

    wireMock.verify(4, getRequestedFor(urlEqualTo("/person/commonplatform/defendant-123")))
  }

  @Test
  fun `does not retry client errors`() {
    wireMock.stubFor(
      get(urlEqualTo("/person/commonplatform/defendant-123"))
        .willReturn(aResponse().withStatus(400).withBody("invalid identifier")),
    )

    assertThrows(WebClientResponseException.BadRequest::class.java) {
      client.getCommonPlatformPerson("defendant-123").block()
    }

    wireMock.verify(1, getRequestedFor(urlEqualTo("/person/commonplatform/defendant-123")))
  }

  @Test
  fun `times out each attempt and retries transient timeout failures`() {
    createClient(Duration.ofMillis(50))
    wireMock.stubFor(
      get(urlEqualTo("/person/commonplatform/defendant-123"))
        .willReturn(aResponse().withFixedDelay(200).withStatus(200).withBody("""{"firstName":"Alex"}""")),
    )

    val exception = assertThrows(RuntimeException::class.java) {
      client.getCommonPlatformPerson("defendant-123").block()
    }

    assertTrue(exception.cause is ReadTimeoutException)
    wireMock.verify(4, getRequestedFor(urlEqualTo("/person/commonplatform/defendant-123")))
  }

  private fun createClient(timeout: Duration = Duration.ofSeconds(10)) {
    client =
      CprClient(
        WebClient.builder().unauthenticatedWebClient(url = "http://localhost:${wireMock.port()}", timeout = timeout),
      )
  }
}
