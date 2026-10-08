package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.integration

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class PingTest : IntegrationTestBase() {
  @Test
  fun `ping returns a simple response for an authenticated request`() {
    webTestClient.get()
      .uri("/ping")
      .headers(setAuthorisation())
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody()
      .jsonPath("message").isEqualTo("HMPPS Court Case Enrichment Worker is running")
  }

  @Test
  fun `ping rejects requests without a token`() {
    webTestClient.get()
      .uri("/ping")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `ping rejects requests with an invalid token`() {
    webTestClient.get()
      .uri("/ping")
      .headers { it.setBearerAuth("invalid-token") }
      .exchange()
      .expectStatus().isUnauthorized
  }
}
