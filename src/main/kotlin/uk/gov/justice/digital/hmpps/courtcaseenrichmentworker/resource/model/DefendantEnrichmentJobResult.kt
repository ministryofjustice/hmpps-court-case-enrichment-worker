package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource.model

import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import java.time.LocalDate

data class DefendantEnrichmentJobResult(
  val runId: String,
  val status: String,
  val fromDate: LocalDate,
  val toDate: LocalDate,
  val courtCode: String?,
  val defendantIdsFetched: Long,
  val cidsFetched: Long,
  val defendantIdsProcessed: Long,
  val cidsProcessed: Long,
  val persistedResults: Long,
  val failedResults: Long,
  val results: List<EnrichmentResult> = emptyList(),
)
