package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.config.DefendantEnrichmentProperties
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
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
  private val defendantEnrichmentProperties: DefendantEnrichmentProperties,
) {
  fun enrichDefendants(): DefendantEnrichmentJobResult {
    val runId = UUID.randomUUID().toString()
    val courtCodes = defendantEnrichmentProperties.courtCodes
    require(courtCodes.isNotEmpty()) { "At least one defendant-enrichment court code must be configured" }
    require(courtCodes.all { it.isNotBlank() }) { "Defendant-enrichment court codes must not be blank" }

    return ServiceCallSummary(runId).collecting {
      val fromDate = LocalDate.now(clock)
      val toDate = fromDate.plusDays(14)
      val courtResults = courtCodes.map { courtCode ->
        enrichDefendants(runId, fromDate, toDate, courtCode)
      }
      val results = courtResults.flatMap { it.results }
      val defendantIdsFetched = courtResults.sumOf { it.defendantIdsFetched }
      val cidsFetched = courtResults.sumOf { it.cidsFetched }

      val result = DefendantEnrichmentJobResult(
        runId = runId,
        status = "ENRICHMENT_COMPLETED",
        fromDate = fromDate,
        toDate = toDate,
        courtCodes = courtCodes,
        defendantIdsFetched = defendantIdsFetched.toLong(),
        cidsFetched = cidsFetched.toLong(),
        defendantIdsProcessed = defendantIdsFetched.toLong(),
        cidsProcessed = cidsFetched.toLong(),
        persistedResults = results.size.toLong(),
        failedResults = 0,
        results = results,
      )

      log.info(
        "Completed defendant enrichment job runId={} courtCount={} defendantIdsFetched={} cidsFetched={} defendantIdsProcessed={} cidsProcessed={} persistedResults={}",
        runId,
        courtCodes.size,
        result.defendantIdsFetched,
        result.cidsFetched,
        result.defendantIdsProcessed,
        result.cidsProcessed,
        result.persistedResults,
      )

      result
    }
  }

  private fun enrichDefendants(runId: String, fromDate: LocalDate, toDate: LocalDate, courtCode: String): CourtEnrichmentResult {
    log.info(
      "Starting defendant enrichment target fetch runId={} fromDate={} toDate={} courtCode={}",
      runId,
      fromDate,
      toDate,
      courtCode,
    )

    val defendantIds = enrichmentTargetService.fetchDefendantIds(runId, fromDate, toDate, courtCode)
    val cids = enrichmentTargetService.fetchCids(runId, fromDate, toDate, courtCode)

    log.info(
      "Completed defendant enrichment target fetch runId={} defendantIdsFetched={} cidsFetched={}",
      runId,
      defendantIds.size,
      cids.size,
    )

    val results = defendantIds.map { defendantId ->
      defendantEnrichmentService.enrichCommonPlatformDefendant(defendantId).also {
        courtCaseServiceClient.persistEnrichmentResult(it.toPersistenceRequest(runId)).block()
      }
    } + cids.map { cId ->
      defendantEnrichmentService.enrichLibraDefendant(cId).also {
        courtCaseServiceClient.persistEnrichmentResult(it.toPersistenceRequest(runId)).block()
      }
    }

    log.info(
      "Completed court enrichment runId={} courtCode={} defendantIdsFetched={} cidsFetched={} defendantIdsProcessed={} cidsProcessed={} persistedResults={}",
      runId,
      courtCode,
      defendantIds.size,
      cids.size,
      defendantIds.size,
      cids.size,
      results.size,
    )

    return CourtEnrichmentResult(defendantIds.size, cids.size, results)
  }

  companion object {
    private val log = LoggerFactory.getLogger(DefendantEnrichmentJobService::class.java)
  }

  private data class CourtEnrichmentResult(
    val defendantIdsFetched: Int,
    val cidsFetched: Int,
    val results: List<EnrichmentResult>,
  )
}
