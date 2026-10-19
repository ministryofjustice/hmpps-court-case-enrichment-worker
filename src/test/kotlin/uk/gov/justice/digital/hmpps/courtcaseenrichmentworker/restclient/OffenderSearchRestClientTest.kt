package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.codec.DecodingException
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config.OffenderSearchWebClientConfiguration
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.MatchRequest
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.MatchResponse
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.OffenderSearchMatchType
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeoutException

class OffenderSearchRestClientTest {
  private val server = WireMockServer(wireMockConfig().dynamicPort())
  private lateinit var client: OffenderSearchRestClient
  private val request = MatchRequest(firstName = "Alex", surname = "Example", dateOfBirth = "1990-01-01")

  @BeforeEach
  fun startServer() {
    server.start()
    server.stubFor(
      post(urlEqualTo("/oauth/token")).willReturn(
        aResponse().withHeader("Content-Type", "application/json")
          .withBody("""{"access_token":"test-service-token","token_type":"bearer","expires_in":3600}"""),
      ),
    )
    createClient()
  }

  @AfterEach
  fun stopServer() {
    server.stop()
  }

  @Test
  fun `matches using JSON and a service token with nested response data`() {
    stubResponse(
      "/match",
      """
      {
        "matchedBy": "ALL_SUPPLIED",
        "matches": [{
          "offender": {
            "firstName": "Alex", "surname": "Example", "dateOfBirth": "1990-01-01",
            "otherIds": {"crn":"X12345", "pncNumber":"123", "extra":"ignored"},
            "probationStatus": {"status":"CURRENT", "inBreach":true, "preSentenceActivity":true, "awaitingPsr":false},
            "offenderAliases": [{"dateOfBirth":"1991-02-03", "firstName":"Alias", "middleNames":["Middle"]}]
          }
        }]
      }
      """.trimIndent(),
    )

    val result = client.match(request).block()!!

    assertEquals(1, result.matchCount)
    assertTrue(result.isExactOffenderMatch)
    assertEquals("X12345", result.matches?.single()?.offender?.otherIds?.crn)
    assertEquals(true, result.matches?.single()?.offender?.probationStatus?.inBreach)
    assertEquals(LocalDate.of(1991, 2, 3), result.matches?.single()?.offender?.offenderAliases?.single()?.dateOfBirth)
    server.verify(
      postRequestedFor(urlEqualTo("/match"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("Authorization", equalTo("Bearer test-service-token"))
        .withRequestBody(equalToJson("""{"firstName":"Alex","surname":"Example","dateOfBirth":"1990-01-01"}""")),
    )
    server.verify(
      postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(equalTo("grant_type=client_credentials")),
    )
  }

  @Test
  fun `search deserializes array and safely escapes CRN in JSON`() {
    stubResponse(
      "/search",
      """
      [{
        "offenderId":123,
        "otherIds":{"crn":"X12345"},
        "probationStatus":{"status":"PREVIOUSLY_KNOWN","previouslyKnownTerminationDate":"2025-03-04"},
        "extra":"ignored"
      }]
      """.trimIndent(),
    )

    val result = client.search("X\"123").block()!!

    assertEquals(123L, result.searchResponses.single().offenderId)
    assertEquals(LocalDate.of(2025, 3, 4), result.searchResponses.single().probationStatusDetail?.previouslyKnownTerminationDate)
    server.verify(
      postRequestedFor(urlEqualTo("/search"))
        .withHeader("Authorization", equalTo("Bearer test-service-token"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(equalToJson("""{"crn":"X\"123"}""")),
    )
  }

  @Test
  fun `accepts valid empty search and match results`() {
    stubResponse("/search", "[]")
    stubResponse("/match", """{"matches":[],"matchedBy":"NOTHING"}""")

    assertTrue(client.search("X12345").block()!!.searchResponses.isEmpty())
    assertFalse(client.match(request).block()!!.isExactOffenderMatch)
    assertEquals(0, MatchResponse().matchCount)
    assertFalse(MatchResponse(matchedBy = OffenderSearchMatchType.ALL_SUPPLIED).isExactOffenderMatch)
  }

  @Test
  fun `can disable authentication for local mocking`() {
    createClient(disableAuthentication = true)
    stubResponse("/search", "[]")

    client.search("X12345").block()

    server.verify(postRequestedFor(urlEqualTo("/search")).withHeader("Authorization", absent()))
    server.verify(0, postRequestedFor(urlEqualTo("/oauth/token")))
  }

  @Test
  fun `invalid inputs fail without making HTTP requests`() {
    assertThrows(IllegalArgumentException::class.java) { client.search(" ").block() }
    assertThrows(IllegalArgumentException::class.java) { client.match(MatchRequest(surname = "")).block() }
    assertTrue(server.allServeEvents.isEmpty())
  }

  @Test
  fun `both operations retry server failures three times and preserve original error`() {
    listOf("/match", "/search").forEach { path ->
      server.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(503).withBody("unavailable")))
      val error = assertThrows(WebClientResponseException.ServiceUnavailable::class.java) { call(path) }
      assertEquals("unavailable", error.responseBodyAsString)
      server.verify(4, postRequestedFor(urlEqualTo(path)))
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [400, 401, 403, 404, 429])
  fun `both operations propagate client errors without retry`(status: Int) {
    listOf("/match", "/search").forEach { path ->
      server.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))
      val error = assertThrows(WebClientResponseException::class.java) { call(path) }
      assertEquals(status, error.statusCode.value())
      server.verify(1, postRequestedFor(urlEqualTo(path)))
    }
  }

  @Test
  fun `both operations reject empty bodies without retry`() {
    listOf("/match", "/search").forEach { path ->
      server.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(200)))
      assertThrows(IllegalStateException::class.java) { call(path) }
      server.verify(1, postRequestedFor(urlEqualTo(path)))
    }
  }

  @Test
  fun `both operations reject malformed JSON without retry`() {
    listOf("/match", "/search").forEach { path ->
      stubResponse(path, "{")
      assertThrows(DecodingException::class.java) { call(path) }
      server.verify(1, postRequestedFor(urlEqualTo(path)))
    }
  }

  @Test
  fun `both operations time out each attempt and retry three times`() {
    // Acquire the service token before using a short timeout in this test.
    stubResponse("/search", "[]")
    client.search("X12345").block()
    server.resetRequests()
    client = OffenderSearchRestClient(webClient, "/match", "/search", false, Duration.ofMillis(300))
    listOf("/match", "/search").forEach { path ->
      server.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withFixedDelay(1000)))
      val error = assertThrows(RuntimeException::class.java) { call(path) }
      assertTrue(error.cause is TimeoutException)
      server.verify(4, postRequestedFor(urlEqualTo(path)))
    }
  }

  private lateinit var webClient: WebClient

  private fun createClient(disableAuthentication: Boolean = false) {
    val registration = ClientRegistration.withRegistrationId("offender-search-client")
      .clientId("worker-client").clientSecret("test-secret")
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
      .tokenUri("${server.baseUrl()}/oauth/token").build()
    val repository = InMemoryClientRegistrationRepository(registration)
    webClient = OffenderSearchWebClientConfiguration(
      server.baseUrl(),
      repository,
      InMemoryOAuth2AuthorizedClientService(repository),
    ).offenderSearchWebClient(WebClient.builder())
    client = OffenderSearchRestClient(webClient, "/match", "/search", disableAuthentication, Duration.ofSeconds(10))
  }

  private fun call(path: String) {
    if (path == "/match") client.match(request).block() else client.search("X12345").block()
  }

  private fun stubResponse(path: String, body: String) {
    server.stubFor(
      post(urlEqualTo(path)).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)),
    )
  }
}
