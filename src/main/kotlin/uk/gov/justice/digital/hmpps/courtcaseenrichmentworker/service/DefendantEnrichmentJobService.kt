package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.resource.model.DefendantEnrichmentJobResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.ServiceCallSummary
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.toPersistenceRequest
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

@Service
class DefendantEnrichmentJobService(
  private val enrichmentTargetService: EnrichmentTargetService,
  private val clock: Clock,
  private val defendantEnrichmentService: DefendantEnrichmentService,
  private val courtCaseServiceClient: CourtCaseServiceClient,
) {
  fun enrichDefendants(): DefendantEnrichmentJobResult {
    val courtCode = POC_COURT_CODE
    val runId = UUID.randomUUID().toString()

    return ServiceCallSummary(runId).collecting { enrichDefendants(runId, courtCode) }
  }

  private fun enrichDefendants(runId: String, courtCode: String): DefendantEnrichmentJobResult {
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

    val results = defendantIdsFetched.map { defendantId ->
      defendantEnrichmentService.enrichCommonPlatformDefendant(defendantId).also {
        courtCaseServiceClient.persistEnrichmentResult(it.toPersistenceRequest(runId)).block()
      }
    } + cidsFetched.map { cId ->
      defendantEnrichmentService.enrichLibraDefendant(cId).also {
        courtCaseServiceClient.persistEnrichmentResult(it.toPersistenceRequest(runId)).block()
      }
    }

    log.info(
      "Completed defendant enrichment runId={} defendantIdsProcessed={} cidsProcessed={} persistedResults={}",
      runId,
      defendantIdsFetched.size,
      cidsFetched.size,
      results.size,
    )

    return DefendantEnrichmentJobResult(
      runId = runId,
      status = "ENRICHMENT_COMPLETED",
      fromDate = fromDate,
      toDate = toDate,
      courtCode = courtCode,
      defendantIdsFetched = defendantIdsFetched.size.toLong(),
      cidsFetched = cidsFetched.size.toLong(),
      defendantIdsProcessed = defendantIdsFetched.size.toLong(),
      cidsProcessed = cidsFetched.size.toLong(),
      persistedResults = results.size.toLong(),
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
