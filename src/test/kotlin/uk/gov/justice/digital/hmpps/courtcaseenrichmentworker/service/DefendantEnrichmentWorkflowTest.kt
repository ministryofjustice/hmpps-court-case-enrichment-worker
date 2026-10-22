package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
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
  private val logger = LoggerFactory.getLogger("ServiceClientLogging") as Logger
  private val appender = ListAppender<ILoggingEvent>()

  @BeforeEach
  fun start() {
    server.start()
    appender.start()
    logger.addAppender(appender)
  }

  @AfterEach
  fun stop() {
    logger.detachAppender(appender)
    appender.stop()
    server.stop()
  }

  @Test
  fun `job enriches both sources and posts one clean result per target`() {
    server.stubFor(post(urlEqualTo("/enrichment-results")).willReturn(aResponse().withStatus(204)))
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
      CourtCaseServiceClient(webClient, timeout),
    )

    val result = job.enrichDefendants()

    val summaries = appender.list.map { it.formattedMessage }.filter { it.startsWith("Connector summary") }
    assertThat(summaries).hasSize(2)
    assertThat(summaries[0]).contains("runId=${result.runId}", "connector=CPR", "calls=2 completed=2 emptyResults=0 failures=0")
    assertThat(summaries[1]).contains("runId=${result.runId}", "connector=OFFENDER_SEARCH", "calls=1 completed=1 emptyResults=0 failures=0")

    assertEquals("ENRICHMENT_COMPLETED", result.status)
    assertEquals("B03DE", result.courtCode)
    assertEquals(1L, result.defendantIdsFetched)
    assertEquals(1L, result.cidsFetched)
    assertEquals(1L, result.defendantIdsProcessed)
    assertEquals(1L, result.cidsProcessed)
    assertEquals(2, result.results.size)
    server.verify(
      1,
      getRequestedFor(urlPathEqualTo("/enrichment-targets/defendant-ids"))
        .withQueryParam("fromDate", equalTo("2026-10-14"))
        .withQueryParam("toDate", equalTo("2026-10-28"))
        .withQueryParam("courtCode", equalTo("B03DE")),
    )
    server.verify(1, getRequestedFor(urlPathEqualTo("/enrichment-targets/cids")))
    assertEquals(7, server.allServeEvents.size)
    assertTrue(
      server.allServeEvents.filter { it.request.url.startsWith("/enrichment-targets/") }
        .all { it.request.queryParameter("courtCode").firstValue() == "B03DE" },
    )
    server.verify(1, getRequestedFor(urlEqualTo("/person/commonplatform/cp-123")))
    server.verify(1, getRequestedFor(urlEqualTo("/person/libra/libra-123")))
    server.verify(1, postRequestedFor(urlEqualTo("/search")))
    assertEquals(2L, result.persistedResults)
    server.verify(2, postRequestedFor(urlEqualTo("/enrichment-results")))
    server.verify(
      1,
      postRequestedFor(urlEqualTo("/enrichment-results")).withRequestBody(
        equalToJson(
          """
          {
            "sourceType":"COMMON_PLATFORM", "targetId":"cp-123", "status":"ENRICHED",
            "jobRunId":"${result.runId}", "enrichedBy":"court-case-enrichment-worker",
            "defendant":{"cprUUID":"record-cp","name":{"forename1":"Alex"},"dateOfBirth":"1990-01-02"},
            "offender":{"crn":"X123","pnc":"pnc"},
            "probation":{"probationStatus":"CURRENT","awaitingPsr":true,"breach":true,"preSentenceActivity":false},
            "groupedOffenderMatches":{"matches":[{
              "matchIdentifiers":{"crn":"X123","pnc":"pnc"},
              "matchType":"NAME_DOB_ALIAS","confirmed":false,"rejected":false
            }]}
          }
          """.trimIndent(),
          true,
          true,
        ),
      ),
    )
    server.verify(
      1,
      postRequestedFor(urlEqualTo("/enrichment-results")).withRequestBody(
        equalToJson(
          """{"sourceType":"LIBRA","targetId":"libra-123","status":"ENRICHED","jobRunId":"${result.runId}","defendant":{"cprUUID":"record-libra"}}""",
          true,
          true,
        ),
      ),
    )

    val results = result.results

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
    server.verify(2, postRequestedFor(urlEqualTo("/enrichment-results")))
    server.verify(0, postRequestedFor(urlEqualTo("/match")))
    assertEquals(7, server.allServeEvents.size)
  }
}
