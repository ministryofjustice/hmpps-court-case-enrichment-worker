package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import java.time.LocalDate

@Service
class EnrichmentTargetService(
  private val courtCaseServiceClient: CourtCaseServiceClient,
) {
  fun fetchAllDefendantIds(
    runId: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    courtCode: String?,
  ): List<String> = fetchAllPages(
    source = "COMMON_PLATFORM",
    runId = runId,
  ) { page ->
    val response = courtCaseServiceClient
      .getDefendantIds(
        fromDate = fromDate,
        toDate = toDate,
        courtCode = courtCode,
        page = page,
        size = PAGE_SIZE,
      )
      .block() ?: error("Court Case Service returned null defendant IDs page")

    PageResult(
      page = response.page,
      targets = response.defendantIds,
      hasNext = response.hasNext,
      totalElements = response.totalElements,
    )
  }

  fun fetchAllCids(
    runId: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    courtCode: String?,
  ): List<String> = fetchAllPages(
    source = "LIBRA",
    runId = runId,
  ) { page ->
    val response = courtCaseServiceClient
      .getCids(
        fromDate = fromDate,
        toDate = toDate,
        courtCode = courtCode,
        page = page,
        size = PAGE_SIZE,
      )
      .block() ?: error("Court Case Service returned null cIds page")

    PageResult(
      page = response.page,
      targets = response.cIds,
      hasNext = response.hasNext,
      totalElements = response.totalElements,
    )
  }

  private fun fetchAllPages(
    source: String,
    runId: String,
    fetchPage: (page: Int) -> PageResult,
  ): List<String> {
    var page = 0
    val targets = mutableListOf<String>()

    while (true) {
      check(page < MAX_PAGES) {
        "Exceeded $MAX_PAGES pages while fetching $source targets for runId=$runId"
      }

      val result = fetchPage(page)

      check(result.page == page) {
        "Expected page $page but got ${result.page} while fetching $source targets for runId=$runId"
      }

      check(result.targets.isNotEmpty() || !result.hasNext) {
        "Empty non-final page while fetching $source targets for runId=$runId"
      }
      targets.addAll(result.targets)

      log.info(
        "Fetched page of enrichment targets runId={} source={} page={} itemCount={} totalElements={} hasNext={}",
        runId,
        source,
        result.page,
        result.targets.size,
        result.totalElements,
        result.hasNext,
      )

      if (!result.hasNext) {
        return targets
      }

      page++
    }
  }

  private data class PageResult(
    val page: Int,
    val targets: List<String>,
    val hasNext: Boolean,
    val totalElements: Long,
  )

  companion object {
    private val log = LoggerFactory.getLogger(EnrichmentTargetService::class.java)
    private const val PAGE_SIZE = 200
    private const val MAX_PAGES = 1_000
  }
}
