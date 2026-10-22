package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedAddress
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedAlias
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedDefendant
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedGroupedOffenderMatches
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedMatchIdentifiers
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedName
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedOffender
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedOffenderMatch
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedProbation
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.toPersistenceRequest
import java.time.LocalDate

class EnrichmentResultMappingTest {
  @Test
  fun `maps every clean result field without losing nulls flags dates or aliases`() {
    val result = EnrichmentResult(
      sourceType = EnrichmentSourceType.LIBRA,
      targetId = "libra",
      status = EnrichmentStatus.NEEDS_REVIEW,
      defendant = EnrichedDefendant(
        "record",
        EnrichedName("Ms", "First", "Middle", "Last"),
        LocalDate.of(1990, 1, 2),
        "Female",
        EnrichedAddress("one", "two", "three", "four", "five", "postcode"),
      ),
      offender = EnrichedOffender("crn", "pnc", "cro"),
      probation = EnrichedProbation("PREVIOUSLY_KNOWN", false, true, LocalDate.of(2025, 2, 3), true),
      groupedOffenderMatches = EnrichedGroupedOffenderMatches(
        listOf(
          EnrichedOffenderMatch(
            EnrichedMatchIdentifiers("crn", "pnc", "cro", listOf(EnrichedAlias("Alias", "Surname", listOf("Middle")))),
            confirmed = true,
            rejected = true,
          ),
        ),
      ),
      warnings = listOf("Review required"),
      errors = listOf("Example error"),
    )

    val request = result.toPersistenceRequest("run")

    assertThat(request).usingRecursiveComparison()
      .ignoringFields("enrichedBy", "jobRunId")
      .isEqualTo(result)
    assertThat(request.enrichedBy).isEqualTo("court-case-enrichment-worker")
    assertThat(request.jobRunId).isEqualTo("run")
  }

  @Test
  fun `no match retains absent details rather than inventing enrichment`() {
    val result = EnrichmentResult(EnrichmentSourceType.COMMON_PLATFORM, "cp", EnrichmentStatus.NO_MATCH)

    assertThat(result.toPersistenceRequest("run")).usingRecursiveComparison()
      .ignoringFields("enrichedBy", "jobRunId")
      .isEqualTo(result)
  }
}
