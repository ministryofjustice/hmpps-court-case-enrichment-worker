package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.CidsPage
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.DefendantIdsPage
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DefendantEnrichmentJobServiceTest {
  private val ccs = mock(CourtCaseServiceClient::class.java)
  private val enrichment = mock(DefendantEnrichmentService::class.java)
  private val clock = Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"), ZoneOffset.UTC)
  private val service = DefendantEnrichmentJobService(EnrichmentTargetService(ccs), clock, enrichment)
  private val from = LocalDate.of(2026, 10, 14)
  private val to = from.plusDays(1)

  @Test
  fun `fetches all pages across all courts without enrichment or persistence`() {
    `when`(ccs.getDefendantIds(from, to, null, 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp1"), 0, 200, 2, 2, true)))
    `when`(ccs.getDefendantIds(from, to, null, 1, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp2"), 1, 200, 2, 2, false)))
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.just(CidsPage(listOf("libra"), 0, 200, 1, 1, false)))

    val result = service.enrichDefendants()

    assertEquals("TARGETS_FETCHED", result.status)
    assertNull(result.courtCode)
    assertEquals(from, result.fromDate)
    assertEquals(to, result.toDate)
    assertEquals(2L, result.defendantIdsFetched)
    assertEquals(1L, result.cidsFetched)
    assertEquals(0L, result.defendantIdsProcessed)
    assertEquals(0L, result.cidsProcessed)
    assertEquals(0L, result.persistedResults)
    assertEquals(0L, result.failedResults)
    assertEquals(emptyList<EnrichmentResult>(), result.results)
    val order = inOrder(ccs)
    order.verify(ccs).getDefendantIds(from, to, null, 0, 200)
    order.verify(ccs).getDefendantIds(from, to, null, 1, 200)
    order.verify(ccs).getCids(from, to, null, 0, 200)
    verifyNoMoreInteractions(ccs)
    verifyNoInteractions(enrichment)
  }

  @Test
  fun `empty target pages do not invoke enrichment`() {
    `when`(ccs.getDefendantIds(from, to, null, 0, 200)).thenReturn(Mono.just(DefendantIdsPage(emptyList(), 0, 200, 0, 0, false)))
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.just(CidsPage(emptyList(), 0, 200, 0, 0, false)))

    val result = service.enrichDefendants()

    assertEquals(emptyList<EnrichmentResult>(), result.results)
    assertEquals(0L, result.defendantIdsProcessed)
    assertEquals(0L, result.cidsProcessed)
    verifyNoInteractions(enrichment)
  }

  @Test
  fun `target fetch errors fail the job without invoking enrichment`() {
    `when`(ccs.getDefendantIds(from, to, null, 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp"), 0, 200, 1, 1, false)))
    val error = IllegalStateException("CCS unavailable")
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.error(error))

    assertSame(error, assertThrows(IllegalStateException::class.java) { service.enrichDefendants() })

    verifyNoInteractions(enrichment)
  }
}
