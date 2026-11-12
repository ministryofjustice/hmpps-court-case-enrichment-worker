package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CprClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.OffenderSearchRestClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprDefendant
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprIdentifier
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.OtherIds
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.ProbationStatusDetail
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.SearchResponse
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.SearchResponses
import java.time.LocalDate

class DefendantEnrichmentResourceTest : IntegrationTestBase() {
  @MockitoBean
  private lateinit var cpr: CprClient

  @MockitoBean
  private lateinit var search: OffenderSearchRestClient

  @MockitoBean
  private lateinit var courtCaseService: CourtCaseServiceClient

  @AfterEach
  fun `never fetches or persists CCS data`() {
    verifyNoInteractions(courtCaseService)
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `returns full enrichment using the correct CPR lookup`(source: EnrichmentSourceType) {
    stubPerson(source, Mono.just(CprDefendant(cprUUID = "record", firstName = "Alex", identifiers = CprIdentifier(crns = listOf("X123")))))
    `when`(search.search("X123")).thenReturn(
      Mono.just(
        SearchResponses(
          listOf(
            SearchResponse(
              otherIds = OtherIds("X123", "cro", "pnc"),
              probationStatusDetail = ProbationStatusDetail("CURRENT", LocalDate.of(2025, 3, 4), true, true, true),
            ),
          ),
        ),
      ),
    )

    webTestClient.get().uri(path(source))
      .headers(setAuthorisation(username = null))
      .exchange().expectStatus().isOk
      .expectHeader().valueMatches("Cache-Control", ".*no-store.*")
      .expectBody()
      .jsonPath("$.sourceType").isEqualTo(source.name)
      .jsonPath("$.targetId").isEqualTo("target-123")
      .jsonPath("$.status").isEqualTo("ENRICHED")
      .jsonPath("$.defendant.cprUUID").isEqualTo("record")
      .jsonPath("$.defendant.name.forename1").isEqualTo("Alex")
      .jsonPath("$.offender.crn").isEqualTo("X123")
      .jsonPath("$.offender.pnc").isEqualTo("pnc")
      .jsonPath("$.offender.cro").isEqualTo("cro")
      .jsonPath("$.probation.probationStatus").isEqualTo("CURRENT")
      .jsonPath("$.probation.previouslyKnownTerminationDate").isEqualTo("2025-03-04")
      .jsonPath("$.probation.preSentenceActivity").isEqualTo(true)
      .jsonPath("$.probation.awaitingPsr").isEqualTo(true)
      .jsonPath("$.probation.breach").isEqualTo(true)
      .jsonPath("$.groupedOffenderMatches.matches[0].matchIdentifiers.crn").isEqualTo("X123")
      .jsonPath("$.groupedOffenderMatches.matches[0].confirmed").isEqualTo(false)
      .jsonPath("$.warnings").isEmpty
      .jsonPath("$.errors").isEmpty

    verify(search).search("X123")
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `missing CPR record returns no match`(source: EnrichmentSourceType) {
    stubPerson(source, Mono.empty())

    webTestClient.get().uri(path(source)).headers(setAuthorisation())
      .exchange().expectStatus().isOk
      .expectBody()
      .jsonPath("$.sourceType").isEqualTo(source.name)
      .jsonPath("$.targetId").isEqualTo("target-123")
      .jsonPath("$.status").isEqualTo("NO_MATCH")
      .jsonPath("$.offender").doesNotExist()
      .jsonPath("$.probation").doesNotExist()

    verifyNoInteractions(search)
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `ambiguous matches return review outcome without selecting an offender`(source: EnrichmentSourceType) {
    stubPerson(source, Mono.just(CprDefendant(identifiers = CprIdentifier(crns = listOf("X123", "X456")))))
    listOf("X123", "X456").forEach {
      `when`(search.search(it)).thenReturn(Mono.just(SearchResponses(listOf(SearchResponse(otherIds = OtherIds(it))))))
    }

    webTestClient.get().uri(path(source)).headers(setAuthorisation())
      .exchange().expectStatus().isOk
      .expectBody()
      .jsonPath("$.status").isEqualTo("NEEDS_REVIEW")
      .jsonPath("$.offender").doesNotExist()
      .jsonPath("$.probation").doesNotExist()
      .jsonPath("$.groupedOffenderMatches.matches.length()").isEqualTo(2)
      .jsonPath("$.warnings").isNotEmpty
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `requires a valid bearer token before calling services`(source: EnrichmentSourceType) {
    webTestClient.get().uri(path(source))
      .exchange().expectStatus().isUnauthorized
    webTestClient.get().uri(path(source))
      .headers { it.setBearerAuth("invalid-token") }
      .exchange().expectStatus().isUnauthorized

    verifyNoInteractions(cpr, search)
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `blank identifiers fail validation before calling services`(source: EnrichmentSourceType) {
    webTestClient.get().uri(path(source, "   ")).headers(setAuthorisation())
      .exchange().expectStatus().isBadRequest
      .expectBody().jsonPath("$.status").isEqualTo(400)

    verifyNoInteractions(cpr, search)
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `CPR failures are not returned as no match`(source: EnrichmentSourceType) {
    stubPerson(source, Mono.error(IllegalStateException("CPR unavailable")))

    webTestClient.get().uri(path(source)).headers(setAuthorisation())
      .exchange().expectStatus().is5xxServerError
      .expectBody().jsonPath("$.status").isEqualTo(500)

    verifyNoInteractions(search)
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `offender search failures fail the request`(source: EnrichmentSourceType) {
    stubPerson(source, Mono.just(CprDefendant(identifiers = CprIdentifier(crns = listOf("X123")))))
    `when`(search.search("X123")).thenReturn(Mono.error(IllegalStateException("Offender Search unavailable")))

    webTestClient.get().uri(path(source)).headers(setAuthorisation())
      .exchange().expectStatus().is5xxServerError
      .expectBody().jsonPath("$.status").isEqualTo(500)
  }

  @ParameterizedTest
  @EnumSource(EnrichmentSourceType::class)
  fun `does not accept POST`(source: EnrichmentSourceType) {
    webTestClient.post().uri(path(source)).headers(setAuthorisation())
      .exchange().expectStatus().isEqualTo(405)

    verifyNoInteractions(cpr, search)
  }

  @Test
  fun `OpenAPI documents both authenticated routes and the complete result`() {
    webTestClient.get().uri("/v3/api-docs")
      .exchange().expectStatus().isOk
      .expectBody()
      .jsonPath("$.paths['/enrichment/common-platform/{defendantId}'].get.security[0].bearerAuth").isArray
      .jsonPath("$.paths['/enrichment/libra/{cId}'].get.security[0].bearerAuth").isArray
      .jsonPath("$.components.schemas.EnrichmentResult.properties.defendant").exists()
      .jsonPath("$.components.schemas.EnrichmentResult.properties.groupedOffenderMatches").exists()
  }

  private fun path(source: EnrichmentSourceType, id: String = "target-123"): String = when (source) {
    EnrichmentSourceType.COMMON_PLATFORM -> "/enrichment/common-platform/$id"
    EnrichmentSourceType.LIBRA -> "/enrichment/libra/$id"
  }

  private fun stubPerson(source: EnrichmentSourceType, response: Mono<CprDefendant>) {
    when (source) {
      EnrichmentSourceType.COMMON_PLATFORM -> `when`(cpr.getCommonPlatformPerson("target-123")).thenReturn(response)
      EnrichmentSourceType.LIBRA -> `when`(cpr.getLibraPerson("target-123")).thenReturn(response)
    }
  }
}
