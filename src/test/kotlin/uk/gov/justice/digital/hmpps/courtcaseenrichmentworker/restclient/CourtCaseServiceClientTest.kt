package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.codec.DecodingException
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config.CourtCaseServiceWebClientConfiguration
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistEnrichmentResultRequest
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedAddress
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedAlias
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedDefendantDetails
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedGroupedOffenderMatches
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedMatchIdentifiers
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedName
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedOffenderDetails
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedOffenderMatch
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistedProbationDetails
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeoutException

class CourtCaseServiceClientTest {
  private val server = WireMockServer(wireMockConfig().dynamicPort())
  private val fromDate = LocalDate.of(2026, 10, 9)
  private val toDate = LocalDate.of(2026, 10, 23)
  private lateinit var client: CourtCaseServiceClient

  @BeforeEach
  fun startServer() {
    server.start()
    server.stubFor(
      post(urlEqualTo("/oauth/token")).willReturn(
        aResponse().withHeader("Content-Type", "application/json")
          .withBody("""{"access_token":"service-token","token_type":"bearer","expires_in":3600}"""),
      ),
    )
    createClient()
  }

  @AfterEach
  fun stopServer() {
    server.stop()
  }

  @Test
  fun `gets defendant IDs with court filter and service token`() {
    stubResponse(
      "/enrichment-targets/defendant-ids",
      """{"defendantIds":["defendant-id"],"page":0,"size":200,"totalElements":340,"totalPages":2,"hasNext":true}""",
    )

    val result = client.getDefendantIds(fromDate, toDate, courtCode = "B10JQ").block()!!

    assertEquals(listOf("defendant-id"), result.defendantIds)
    assertEquals(340L, result.totalElements)
    assertEquals(2, result.totalPages)
    assertTrue(result.hasNext)
    server.verify(
      getRequestedFor(urlPathEqualTo("/enrichment-targets/defendant-ids"))
        .withQueryParam("fromDate", equalTo("2026-10-09"))
        .withQueryParam("toDate", equalTo("2026-10-23"))
        .withQueryParam("courtCode", equalTo("B10JQ"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("200"))
        .withHeader("Authorization", equalTo("Bearer service-token")),
    )
    server.verify(postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(equalTo("grant_type=client_credentials")))
  }

  @Test
  fun `gets CIDs without court filter and supports maximum page size`() {
    stubResponse(
      "/enrichment-targets/cids",
      """{"cIds":["c12345"],"page":1,"size":500,"totalElements":501,"totalPages":2,"hasNext":false}""",
    )

    val result = client.getCids(fromDate, toDate, page = 1, size = 500).block()!!

    assertEquals(listOf("c12345"), result.cIds)
    assertEquals(1, result.page)
    assertEquals(false, result.hasNext)
    assertTrue(server.allServeEvents.filter { it.request.method.value() == "GET" }.all { !it.request.queryParams.containsKey("courtCode") })
    server.verify(getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")).withQueryParam("size", equalTo("500")))
  }

  @Test
  fun `rejects invalid query parameters without sending requests`() {
    assertThrows(IllegalArgumentException::class.java) { client.getCids(fromDate, fromDate).block() }
    assertThrows(IllegalArgumentException::class.java) { client.getCids(toDate, fromDate).block() }
    assertThrows(IllegalArgumentException::class.java) { client.getCids(fromDate, toDate, page = -1).block() }
    assertThrows(IllegalArgumentException::class.java) { client.getCids(fromDate, toDate, size = 501).block() }
    assertThrows(IllegalArgumentException::class.java) { client.getCids(fromDate, toDate, size = 0).block() }
    assertThrows(IllegalArgumentException::class.java) { client.getCids(fromDate, toDate, courtCode = " ").block() }
    assertTrue(server.allServeEvents.isEmpty())
  }

  @Test
  fun `retries server failure only three times`() {
    server.stubFor(get(urlPathEqualTo("/enrichment-targets/cids")).willReturn(aResponse().withStatus(503)))

    assertThrows(WebClientResponseException.ServiceUnavailable::class.java) { client.getCids(fromDate, toDate).block() }

    server.verify(4, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
  }

  @Test
  fun `does not hide or retry a missing endpoint`() {
    server.stubFor(get(urlPathEqualTo("/enrichment-targets/cids")).willReturn(aResponse().withStatus(404)))

    assertThrows(WebClientResponseException.NotFound::class.java) { client.getCids(fromDate, toDate).block() }

    server.verify(1, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
  }

  @Test
  fun `does not treat an empty response as an empty page`() {
    server.stubFor(get(urlPathEqualTo("/enrichment-targets/cids")).willReturn(aResponse().withStatus(200)))

    assertThrows(IllegalStateException::class.java) { client.getCids(fromDate, toDate).block() }

    server.verify(1, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
  }

  @Test
  fun `returns an empty page when the service has no targets`() {
    stubResponse(
      "/enrichment-targets/cids",
      """{"cIds":[],"page":0,"size":200,"totalElements":0,"totalPages":0,"hasNext":false}""",
    )

    val result = client.getCids(fromDate, toDate).block()!!

    assertTrue(result.cIds.isEmpty())
    assertEquals(false, result.hasNext)
    server.verify(1, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
  }

  @Test
  fun `does not retry malformed response data`() {
    stubResponse("/enrichment-targets/cids", """{"cIds":""")

    assertThrows(DecodingException::class.java) { client.getCids(fromDate, toDate).block() }

    server.verify(1, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
  }

  @Test
  fun `times out delayed responses and retries three times`() {
    createClient(Duration.ofMillis(300))
    server.stubFor(
      get(urlPathEqualTo("/enrichment-targets/cids"))
        .willReturn(aResponse().withFixedDelay(1000).withStatus(200)),
    )

    val error = assertThrows(RuntimeException::class.java) { client.getCids(fromDate, toDate).block() }

    assertTrue(error.cause is TimeoutException)
    server.verify(4, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `persists one clean result for each source using a service token`(source: EnrichmentSourceType) {
    server.stubFor(post(urlEqualTo("/enrichment-results")).willReturn(aResponse().withStatus(204)))

    client.persistEnrichmentResult(
      persistenceRequest(source).copy(
        defendant = PersistedDefendantDetails(
          cprUUID = "record-123",
          name = PersistedName("Mr", "Alex", "Middle", "Example"),
          dateOfBirth = LocalDate.of(1990, 1, 2),
          sex = "Male",
          address = PersistedAddress("Building", "12", "Street", "Locality", "Town", "AB1 2CD"),
        ),
        offender = PersistedOffenderDetails("X12345", "pnc-123", "cro-123"),
        probation = PersistedProbationDetails(
          probationStatus = "PREVIOUSLY_KNOWN",
          awaitingPsr = false,
          breach = true,
          previouslyKnownTerminationDate = LocalDate.of(2025, 3, 4),
          preSentenceActivity = true,
        ),
        groupedOffenderMatches = PersistedGroupedOffenderMatches(
          listOf(
            PersistedOffenderMatch(
              PersistedMatchIdentifiers(
                crn = "X12345",
                pnc = "pnc-123",
                cro = "cro-123",
                aliases = listOf(PersistedAlias("Alias", "Example", listOf("Middle Names"))),
              ),
            ),
          ),
        ),
      ),
    ).block()

    server.verify(
      1,
      postRequestedFor(urlEqualTo("/enrichment-results"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withHeader("Authorization", equalTo("Bearer service-token"))
        .withRequestBody(
          equalToJson(
            """
            {
              "sourceType": "$source",
              "targetId": "target-123",
              "status": "ENRICHED",
              "defendant": {
                "cprUUID": "record-123",
                "name": {"title":"Mr","forename1":"Alex","forename2":"Middle","surname":"Example"},
                "dateOfBirth": "1990-01-02",
                "sex": "Male",
                "address": {"line1":"Building","line2":"12","line3":"Street","line4":"Locality","line5":"Town","postcode":"AB1 2CD"}
              },
              "offender": {"crn":"X12345","pnc":"pnc-123","cro":"cro-123"},
              "probation": {
                "probationStatus":"PREVIOUSLY_KNOWN","awaitingPsr":false,"breach":true,
                "previouslyKnownTerminationDate":"2025-03-04","preSentenceActivity":true
              },
              "groupedOffenderMatches": {"matches":[{
                "matchIdentifiers": {
                  "crn":"X12345","pnc":"pnc-123","cro":"cro-123",
                  "aliases":[{"firstName":"Alias","surname":"Example","middleNames":["Middle Names"]}]
                },
                "matchType":"NAME_DOB_ALIAS","confirmed":false,"rejected":false
              }]},
              "warnings": [], "errors": [],
              "enrichedBy": "court-case-enrichment-worker",
              "jobRunId": "run-123"
            }
            """.trimIndent(),
          ),
        ),
    )
    server.verify(1, postRequestedFor(urlEqualTo("/oauth/token")))
  }

  @ParameterizedTest
  @ValueSource(ints = [200, 201, 204])
  fun `accepts successful acknowledgements without requiring a response body`(status: Int) {
    server.stubFor(post(urlEqualTo("/enrichment-results")).willReturn(aResponse().withStatus(status)))

    client.persistEnrichmentResult(persistenceRequest()).block()

    server.verify(1, postRequestedFor(urlEqualTo("/enrichment-results")))
  }

  @ParameterizedTest
  @ValueSource(ints = [302, 400, 401, 403, 404, 409, 429, 500, 503])
  fun `propagates unsuccessful writes without retrying`(status: Int) {
    server.stubFor(
      post(urlEqualTo("/enrichment-results"))
        .willReturn(aResponse().withStatus(status).withBody("write rejected")),
    )

    val error = assertThrows(WebClientResponseException::class.java) {
      client.persistEnrichmentResult(persistenceRequest()).block()
    }

    assertEquals(status, error.statusCode.value())
    assertEquals("write rejected", error.responseBodyAsString)
    server.verify(1, postRequestedFor(urlEqualTo("/enrichment-results")))
  }

  @Test
  fun `supports a configurable CCS persistence path`() {
    createClient(postEnrichmentUrl = "/draft/enrichment")
    server.stubFor(post(urlEqualTo("/draft/enrichment")).willReturn(aResponse().withStatus(204)))

    client.persistEnrichmentResult(persistenceRequest()).block()

    server.verify(1, postRequestedFor(urlEqualTo("/draft/enrichment")))
    server.verify(0, postRequestedFor(urlEqualTo("/enrichment-results")))
  }

  @Test
  fun `rejects invalid persistence metadata without sending requests`() {
    assertThrows(IllegalArgumentException::class.java) { client.persistEnrichmentResult(persistenceRequest().copy(targetId = " ")).block() }
    assertThrows(IllegalArgumentException::class.java) { client.persistEnrichmentResult(persistenceRequest().copy(enrichedBy = " ")).block() }
    assertThrows(IllegalArgumentException::class.java) { client.persistEnrichmentResult(persistenceRequest().copy(jobRunId = " ")).block() }
    assertTrue(server.allServeEvents.isEmpty())
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "https://other-service/enrichment", "//other-service/enrichment"])
  fun `rejects non CCS relative persistence paths`(path: String) {
    createClient(postEnrichmentUrl = path)

    assertThrows(IllegalArgumentException::class.java) { client.persistEnrichmentResult(persistenceRequest()).block() }

    assertTrue(server.allServeEvents.isEmpty())
  }

  @Test
  fun `times out writes without retrying a potentially completed update`() {
    stubResponse("/enrichment-targets/cids", """{"cIds":[],"page":0,"size":200,"totalElements":0,"totalPages":0,"hasNext":false}""")
    createClient(Duration.ofMillis(500))
    client.getCids(fromDate, toDate).block()
    server.resetRequests()
    server.stubFor(
      post(urlEqualTo("/enrichment-results"))
        .willReturn(aResponse().withFixedDelay(2000).withStatus(204)),
    )

    val error = assertThrows(RuntimeException::class.java) { client.persistEnrichmentResult(persistenceRequest()).block() }

    assertTrue(error.cause is TimeoutException)
    server.verify(1, postRequestedFor(urlEqualTo("/enrichment-results")))
  }

  private fun persistenceRequest(source: EnrichmentSourceType = EnrichmentSourceType.COMMON_PLATFORM) = PersistEnrichmentResultRequest(
    sourceType = source,
    targetId = "target-123",
    status = EnrichmentStatus.ENRICHED,
    jobRunId = "run-123",
  )

  private fun stubResponse(path: String, body: String) {
    server.stubFor(
      get(urlPathEqualTo(path)).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)),
    )
  }

  private fun createClient(timeout: Duration = Duration.ofSeconds(10), postEnrichmentUrl: String = "/enrichment-results") {
    val registration = ClientRegistration.withRegistrationId("court-case-service-client")
      .clientId("worker-client")
      .clientSecret("test-secret")
      .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
      .tokenUri("${server.baseUrl()}/oauth/token")
      .build()
    val repository = InMemoryClientRegistrationRepository(registration)
    val configuration = CourtCaseServiceWebClientConfiguration(
      server.baseUrl(),
      repository,
      InMemoryOAuth2AuthorizedClientService(repository),
    )
    client = CourtCaseServiceClient(configuration.courtCaseServiceWebClient(WebClient.builder()), timeout, postEnrichmentUrl)
  }
}
