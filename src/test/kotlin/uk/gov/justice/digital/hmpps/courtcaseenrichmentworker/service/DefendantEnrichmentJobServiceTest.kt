package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.CidsPage
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.DefendantIdsPage
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.PersistEnrichmentResultRequest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DefendantEnrichmentJobServiceTest {
  private val ccs = mock(CourtCaseServiceClient::class.java)
  private val enrichment = mock(DefendantEnrichmentService::class.java)
  private val clock = Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"), ZoneOffset.UTC)
  private val service = DefendantEnrichmentJobService(EnrichmentTargetService(ccs), clock, enrichment, ccs)
  private val from = LocalDate.of(2026, 10, 14)
  private val to = from.plusDays(14)
  private val placeholderRequest = PersistEnrichmentResultRequest(EnrichmentSourceType.COMMON_PLATFORM, "placeholder", EnrichmentStatus.ENRICHED)

  @Test
  fun `fetches all pages for B03DE and enriches and persists each target in order`() {
    `when`(ccs.getDefendantIds(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp1"), 0, 200, 2, 2, true)))
    `when`(ccs.getDefendantIds(from, to, "B03DE", 1, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp2"), 1, 200, 2, 2, false)))
    `when`(ccs.getCids(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(CidsPage(listOf("libra"), 0, 200, 1, 1, false)))
    val results = listOf(
      EnrichmentResult(EnrichmentSourceType.COMMON_PLATFORM, "cp1", EnrichmentStatus.ENRICHED),
      EnrichmentResult(EnrichmentSourceType.COMMON_PLATFORM, "cp2", EnrichmentStatus.NO_MATCH),
      EnrichmentResult(EnrichmentSourceType.LIBRA, "libra", EnrichmentStatus.ENRICHED),
    )
    `when`(enrichment.enrichCommonPlatformDefendant("cp1")).thenReturn(results[0])
    `when`(enrichment.enrichCommonPlatformDefendant("cp2")).thenReturn(results[1])
    `when`(enrichment.enrichLibraDefendant("libra")).thenReturn(results[2])
    `when`(ccs.persistEnrichmentResult(any(PersistEnrichmentResultRequest::class.java) ?: placeholderRequest)).thenReturn(Mono.empty())

    val result = service.enrichDefendants()

    assertEquals("ENRICHMENT_COMPLETED", result.status)
    assertEquals("B03DE", result.courtCode)
    assertEquals(from, result.fromDate)
    assertEquals(to, result.toDate)
    assertEquals(2L, result.defendantIdsFetched)
    assertEquals(1L, result.cidsFetched)
    assertEquals(2L, result.defendantIdsProcessed)
    assertEquals(1L, result.cidsProcessed)
    assertEquals(3L, result.persistedResults)
    assertEquals(0L, result.failedResults)
    assertEquals(results, result.results)
    val order = inOrder(ccs, enrichment)
    order.verify(ccs).getDefendantIds(from, to, "B03DE", 0, 200)
    order.verify(ccs).getDefendantIds(from, to, "B03DE", 1, 200)
    order.verify(ccs).getCids(from, to, "B03DE", 0, 200)
    order.verify(enrichment).enrichCommonPlatformDefendant("cp1")
    val requests = ArgumentCaptor.forClass(PersistEnrichmentResultRequest::class.java)
    order.verify(ccs).persistEnrichmentResult(requests.capture() ?: placeholderRequest)
    order.verify(enrichment).enrichCommonPlatformDefendant("cp2")
    order.verify(ccs).persistEnrichmentResult(requests.capture() ?: placeholderRequest)
    order.verify(enrichment).enrichLibraDefendant("libra")
    order.verify(ccs).persistEnrichmentResult(requests.capture() ?: placeholderRequest)
    assertEquals(results.map { it.targetId }, requests.allValues.map { it.targetId })
    assertEquals(results.map { it.sourceType }, requests.allValues.map { it.sourceType })
    assertEquals(results.map { it.status }, requests.allValues.map { it.status })
    assertEquals(List(3) { result.runId }, requests.allValues.map { it.jobRunId })
    verifyNoMoreInteractions(ccs, enrichment)
  }

  @Test
  fun `persistence failure stops the job before enriching the next target`() {
    `when`(ccs.getDefendantIds(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp1", "cp2"), 0, 200, 2, 1, false)))
    `when`(ccs.getCids(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(CidsPage(emptyList(), 0, 200, 0, 0, false)))
    `when`(enrichment.enrichCommonPlatformDefendant("cp1"))
      .thenReturn(EnrichmentResult(EnrichmentSourceType.COMMON_PLATFORM, "cp1", EnrichmentStatus.ENRICHED))
    val error = IllegalStateException("CCS write rejected")
    `when`(ccs.persistEnrichmentResult(any(PersistEnrichmentResultRequest::class.java) ?: placeholderRequest)).thenReturn(Mono.error(error))

    assertSame(error, assertThrows(IllegalStateException::class.java) { service.enrichDefendants() })

    val order = inOrder(enrichment, ccs)
    order.verify(enrichment).enrichCommonPlatformDefendant("cp1")
    order.verify(ccs).persistEnrichmentResult(any(PersistEnrichmentResultRequest::class.java) ?: placeholderRequest)
    verifyNoMoreInteractions(enrichment)
  }

  @Test
  fun `empty target pages do not invoke enrichment`() {
    `when`(ccs.getDefendantIds(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(DefendantIdsPage(emptyList(), 0, 200, 0, 0, false)))
    `when`(ccs.getCids(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(CidsPage(emptyList(), 0, 200, 0, 0, false)))

    val result = service.enrichDefendants()

    assertEquals(emptyList<EnrichmentResult>(), result.results)
    assertEquals(0L, result.defendantIdsProcessed)
    assertEquals(0L, result.cidsProcessed)
    verifyNoInteractions(enrichment)
  }

  @Test
  fun `target fetch errors fail the job without invoking enrichment`() {
    `when`(ccs.getDefendantIds(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp"), 0, 200, 1, 1, false)))
    val error = IllegalStateException("CCS unavailable")
    `when`(ccs.getCids(from, to, "B03DE", 0, 200)).thenReturn(Mono.error(error))

    assertSame(error, assertThrows(IllegalStateException::class.java) { service.enrichDefendants() })

    verifyNoInteractions(enrichment)
  }

  @Test
  fun `Libra errors propagate and stop further Libra processing`() {
    `when`(ccs.getDefendantIds(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(DefendantIdsPage(emptyList(), 0, 200, 0, 0, false)))
    `when`(ccs.getCids(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(CidsPage(listOf("libra1", "libra2"), 0, 200, 2, 1, false)))
    val error = IllegalStateException("Libra CPR unavailable")
    `when`(enrichment.enrichLibraDefendant("libra1")).thenThrow(error)

    assertSame(error, assertThrows(IllegalStateException::class.java) { service.enrichDefendants() })

    val order = inOrder(enrichment)
    order.verify(enrichment).enrichLibraDefendant("libra1")
    verifyNoMoreInteractions(enrichment)
  }

  @Test
  fun `enrichment errors propagate and stop further processing`() {
    `when`(ccs.getDefendantIds(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp1", "cp2"), 0, 200, 2, 1, false)))
    `when`(ccs.getCids(from, to, "B03DE", 0, 200)).thenReturn(Mono.just(CidsPage(listOf("libra"), 0, 200, 1, 1, false)))
    val error = IllegalStateException("CPR unavailable")
    `when`(enrichment.enrichCommonPlatformDefendant("cp1")).thenThrow(error)

    assertSame(error, assertThrows(IllegalStateException::class.java) { service.enrichDefendants() })

    val order = inOrder(enrichment)
    order.verify(enrichment).enrichCommonPlatformDefendant("cp1")
    verifyNoMoreInteractions(enrichment)
  }
}
