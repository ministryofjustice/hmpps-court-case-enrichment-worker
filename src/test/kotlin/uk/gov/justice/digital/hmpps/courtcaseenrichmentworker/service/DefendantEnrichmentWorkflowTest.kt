package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CprServiceClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.OffenderSearchRestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class DefendantEnrichmentWorkflowTest {
  private val server = WireMockServer(wireMockConfig().dynamicPort())

  @BeforeEach
  fun start() {
    server.start()
  }

  @AfterEach
  fun stop() {
    server.stop()
  }

  @Test
  fun `job enriches Common Platform while Libra enrichment remains available for explicit invocation`() {
    server.stubFor(
      get(urlPathEqualTo("/enrichment-targets/defendant-ids"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withBody("""{"defendantIds":["cp-123"],"page":0,"size":200,"totalElements":1,"totalPages":1,"hasNext":false}"""),
        ),
    )
    server.stubFor(
      get(urlPathEqualTo("/enrichment-targets/cids"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withBody("""{"cIds":["libra-123"],"page":0,"size":200,"totalElements":1,"totalPages":1,"hasNext":false}"""),
        ),
    )
    server.stubFor(
      get(urlEqualTo("/person/commonplatform/cp-123"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withBody("""{"cprUUID":"record-cp","firstName":"Alex","dateOfBirth":"1990-01-02","identifiers":{"crns":["X123"]}}"""),
        ),
    )
    server.stubFor(
      get(urlEqualTo("/person/libra/libra-123"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withBody("""{"cprUUID":"record-libra","firstName":"Libra","identifiers":{"crns":[]}}"""),
        ),
    )
    server.stubFor(
      post(urlEqualTo("/search")).withRequestBody(equalToJson("""{"crn":"X123"}"""))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withBody("""[{"otherIds":{"crn":"X123","pncNumber":"pnc"},"probationStatus":{"status":"CURRENT","awaitingPsr":true,"inBreach":true}}]"""),
        ),
    )
    val webClient = WebClient.builder().baseUrl(server.baseUrl()).build()
    val timeout = Duration.ofSeconds(10)
    val enrichment = DefendantEnrichmentService(
      CprServiceClient(webClient, true, timeout),
      OffenderSearchRestClient(webClient, "/match", "/search", true, timeout),
    )
    val job = DefendantEnrichmentJobService(
      EnrichmentTargetService(CourtCaseServiceClient(webClient, timeout)),
      Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"), ZoneOffset.UTC),
      enrichment,
    )

    val result = job.enrichDefendants()

    assertEquals("COMMON_PLATFORM_ENRICHMENT_COMPLETED", result.status)
    assertEquals("B03DE", result.courtCode)
    assertEquals(1L, result.defendantIdsFetched)
    assertEquals(1L, result.cidsFetched)
    assertEquals(1L, result.defendantIdsProcessed)
    assertEquals(0L, result.cidsProcessed)
    assertEquals(1, result.results.size)
    server.verify(
      1,
      getRequestedFor(urlPathEqualTo("/enrichment-targets/defendant-ids"))
        .withQueryParam("fromDate", equalTo("2026-10-14"))
        .withQueryParam("toDate", equalTo("2026-10-28"))
        .withQueryParam("courtCode", equalTo("B03DE")),
    )
    server.verify(1, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
    assertEquals(4, server.allServeEvents.size)
    assertTrue(
      server.allServeEvents.filter { it.request.url.startsWith("/enrichment-targets/") }
        .all { it.request.queryParameter("courtCode").firstValue() == "B03DE" },
    )
    server.verify(1, getRequestedFor(urlEqualTo("/person/commonplatform/cp-123")))
    server.verify(0, getRequestedFor(urlEqualTo("/person/libra/libra-123")))
    server.verify(1, postRequestedFor(urlEqualTo("/search")))
    server.verify(0, postRequestedFor(urlEqualTo("/enrichment-results")))

    val results = result.results + enrichment.enrichLibraDefendant("libra-123")

    assertEquals(listOf(EnrichmentSourceType.COMMON_PLATFORM, EnrichmentSourceType.LIBRA), results.map { it.sourceType })
    assertEquals(listOf(EnrichmentStatus.ENRICHED, EnrichmentStatus.ENRICHED), results.map { it.status })
    assertEquals("record-cp", results.first().defendant?.cprUUID)
    assertEquals("X123", results.first().offender?.crn)
    assertEquals("pnc", results.first().offender?.pnc)
    assertEquals("CURRENT", results.first().probation?.probationStatus)
    assertEquals(true, results.first().probation?.awaitingPsr)
    assertEquals(true, results.first().probation?.breach)
    assertEquals("record-libra", results.last().defendant?.cprUUID)
    server.verify(1, getRequestedFor(urlEqualTo("/person/commonplatform/cp-123")))
    server.verify(1, getRequestedFor(urlEqualTo("/person/libra/libra-123")))
    server.verify(1, postRequestedFor(urlEqualTo("/search")))
    server.verify(0, postRequestedFor(urlEqualTo("/enrichment-results")))
    server.verify(0, postRequestedFor(urlEqualTo("/match")))
    assertEquals(5, server.allServeEvents.size)
  }
}
