package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.integration

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource.model.DefendantEnrichmentJobResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service.DefendantEnrichmentJobService
import java.time.LocalDate

class DefendantEnrichmentJobResourceTest : IntegrationTestBase() {
  @MockitoBean
  private lateinit var jobService: DefendantEnrichmentJobService

  @Test
  fun `rejects an invalid bearer token`() {
    webTestClient.post().uri("/jobs/enrich-defendants")
      .headers { it.setBearerAuth("invalid-token") }
      .exchange().expectStatus().isUnauthorized
  }

  @Test
  fun `GET remains protected without a token`() {
    webTestClient.get().uri("/jobs/enrich-defendants")
      .exchange().expectStatus().isUnauthorized
  }

  @Test
  fun `OpenAPI documents anonymous access for the job only`() {
    webTestClient.get().uri("/v3/api-docs")
      .exchange().expectStatus().isOk
      .expectBody()
      .jsonPath("$.paths['/jobs/enrich-defendants'].post.security").isArray
      .jsonPath("$.paths['/jobs/enrich-defendants'].post.security").isEmpty
      .jsonPath("$.paths['/ping'].get.security[0].bearerAuth").isArray
  }

  @Test
  fun `anonymous invocation returns the enrichment job result`() {
    val date = LocalDate.of(2026, 10, 14)
    `when`(jobService.enrichDefendants()).thenReturn(
      DefendantEnrichmentJobResult("run-123", "COMMON_PLATFORM_ENRICHMENT_COMPLETED", date, date.plusDays(14), "B03DE", 2, 1, 2, 0, 0, 0),
    )
    webTestClient.post().uri("/jobs/enrich-defendants")
      .exchange().expectStatus().isOk
      .expectBody()
      .jsonPath("$.status").isEqualTo("COMMON_PLATFORM_ENRICHMENT_COMPLETED")
      .jsonPath("$.runId").isEqualTo("run-123")
      .jsonPath("$.courtCode").isEqualTo("B03DE")
      .jsonPath("$.defendantIdsFetched").isEqualTo(2)
      .jsonPath("$.cidsFetched").isEqualTo(1)
      .jsonPath("$.defendantIdsProcessed").isEqualTo(2)
      .jsonPath("$.cidsProcessed").isEqualTo(0)
      .jsonPath("$.persistedResults").isEqualTo(0)
      .jsonPath("$.results").isEmpty
  }

  @Test
  fun `does not allow GET to trigger the job`() {
    webTestClient.get().uri("/jobs/enrich-defendants")
      .headers(setAuthorisation())
      .exchange().expectStatus().isEqualTo(405)
  }
}
