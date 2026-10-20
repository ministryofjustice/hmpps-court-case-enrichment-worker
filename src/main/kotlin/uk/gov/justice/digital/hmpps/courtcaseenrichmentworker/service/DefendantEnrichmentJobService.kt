package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource.model.DefendantEnrichmentJobResult
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class DefendantEnrichmentJobService(
  private val enrichmentTargetService: EnrichmentTargetService,
  private val clock: Clock,
  private val defendantEnrichmentService: DefendantEnrichmentService,
) {
  fun enrichDefendants(): DefendantEnrichmentJobResult {
    val courtCode = POC_COURT_CODE
    val runId = UUID.randomUUID().toString()

    // Includes 14 days starting today; toDate is exclusive.
    val fromDate = LocalDate.now(clock)
    val toDate = fromDate.plusDays(14)

    log.info(
      "Starting defendant enrichment target fetch runId={} fromDate={} toDate={} courtCode={}",
      runId,
      fromDate,
      toDate,
      courtCode,
    )

    val defendantIdsFetched = enrichmentTargetService.fetchAllDefendantIds(runId, fromDate, toDate, courtCode)
    val cidsFetched = enrichmentTargetService.fetchAllCids(runId, fromDate, toDate, courtCode)

    log.info(
      "Completed defendant enrichment target fetch runId={} defendantIdsFetched={} cidsFetched={}",
      runId,
      defendantIdsFetched.size,
      cidsFetched.size,
    )

    val results = defendantIdsFetched.map(defendantEnrichmentService::enrichCommonPlatformDefendant)
//      +
//      cidsFetched.map(defendantEnrichmentService::enrichLibraDefendant)

    log.info("Completed Common Platform enrichment runId={} processed={}", runId, results.size)

    return DefendantEnrichmentJobResult(
      runId = runId,
      status = "COMMON_PLATFORM_ENRICHMENT_COMPLETED",
      fromDate = fromDate,
      toDate = toDate,
      courtCode = courtCode,
      defendantIdsFetched = defendantIdsFetched.size.toLong(),
      cidsFetched = cidsFetched.size.toLong(),
      defendantIdsProcessed = defendantIdsFetched.size.toLong(),
      cidsProcessed = 0,
      persistedResults = 0,
      failedResults = 0,
      results = results,
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(DefendantEnrichmentJobService::class.java)

    // Remove the POC filter when the batch is ready to process all courts.
    private const val POC_COURT_CODE = "B03DE"
  }
}
