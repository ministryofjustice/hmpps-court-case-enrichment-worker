package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import java.time.LocalDate

@Service
class EnrichmentTargetService(
  private val courtCaseServiceClient: CourtCaseServiceClient,
) {
  fun fetchDefendantIds(
    runId: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    courtCode: String,
  ): List<String> = courtCaseServiceClient
    .getDefendantIds(fromDate, toDate, courtCode)
    .block()?.defendantIds
    ?.also { logFetchedTargets(runId, "COMMON_PLATFORM", it.size) }
    ?: error("Court Case Service returned null defendant IDs")

  fun fetchCids(
    runId: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    courtCode: String,
  ): List<String> = courtCaseServiceClient
    .getCids(fromDate, toDate, courtCode)
    .block()?.cIds
    ?.also { logFetchedTargets(runId, "LIBRA", it.size) }
    ?: error("Court Case Service returned null cIDs")

  private fun logFetchedTargets(runId: String, source: String, itemCount: Int) {
    log.info("Fetched enrichment targets runId={} source={} itemCount={}", runId, source, itemCount)
  }

  companion object {
    private val log = LoggerFactory.getLogger(EnrichmentTargetService::class.java)
  }
}
