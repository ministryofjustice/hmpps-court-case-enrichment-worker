package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CourtCaseServiceClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.CidsPage
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.DefendantIdsPage
import java.time.LocalDate

class EnrichmentTargetServiceTest {
  private val ccs = mock(CourtCaseServiceClient::class.java)
  private val service = EnrichmentTargetService(ccs)
  private val from = LocalDate.of(2026, 10, 14)
  private val to = from.plusDays(14)

  @Test
  fun `fetches all Common Platform pages in order with the court filter`() {
    `when`(ccs.getDefendantIds(from, to, "COURT", 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp1"), 0, 200, 2, 2, true)))
    `when`(ccs.getDefendantIds(from, to, "COURT", 1, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp2"), 1, 200, 2, 2, false)))

    assertEquals(listOf("cp1", "cp2"), service.fetchAllDefendantIds("run", from, to, "COURT"))

    verify(ccs).getDefendantIds(from, to, "COURT", 0, 200)
    verify(ccs).getDefendantIds(from, to, "COURT", 1, 200)
    verifyNoMoreInteractions(ccs)
  }

  @Test
  fun `fetches all Libra pages without a court filter`() {
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.just(CidsPage(listOf("libra1"), 0, 200, 2, 2, true)))
    `when`(ccs.getCids(from, to, null, 1, 200)).thenReturn(Mono.just(CidsPage(listOf("libra2"), 1, 200, 2, 2, false)))

    assertEquals(listOf("libra1", "libra2"), service.fetchAllCids("run", from, to, null))

    verify(ccs).getCids(from, to, null, 0, 200)
    verify(ccs).getCids(from, to, null, 1, 200)
    verifyNoMoreInteractions(ccs)
  }

  @Test
  fun `empty final pages return no targets`() {
    `when`(ccs.getDefendantIds(from, to, null, 0, 200)).thenReturn(Mono.just(DefendantIdsPage(emptyList(), 0, 200, 0, 0, false)))
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.just(CidsPage(emptyList(), 0, 200, 0, 0, false)))

    assertEquals(emptyList<String>(), service.fetchAllDefendantIds("run", from, to, null))
    assertEquals(emptyList<String>(), service.fetchAllCids("run", from, to, null))
  }

  @Test
  fun `rejects empty non-final pages for both sources`() {
    `when`(ccs.getDefendantIds(from, to, null, 0, 200)).thenReturn(Mono.just(DefendantIdsPage(emptyList(), 0, 200, 1, 2, true)))
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.just(CidsPage(emptyList(), 0, 200, 1, 2, true)))

    assertThrows(IllegalStateException::class.java) { service.fetchAllDefendantIds("run", from, to, null) }
    assertThrows(IllegalStateException::class.java) { service.fetchAllCids("run", from, to, null) }
  }

  @Test
  fun `rejects unexpected page numbers for both sources`() {
    `when`(ccs.getDefendantIds(from, to, null, 0, 200)).thenReturn(Mono.just(DefendantIdsPage(listOf("cp"), 1, 200, 1, 1, false)))
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.just(CidsPage(listOf("libra"), 1, 200, 1, 1, false)))

    assertThrows(IllegalStateException::class.java) { service.fetchAllDefendantIds("run", from, to, null) }
    assertThrows(IllegalStateException::class.java) { service.fetchAllCids("run", from, to, null) }
  }

  @Test
  fun `propagates downstream failures for both sources`() {
    val error = IllegalStateException("CCS unavailable")
    `when`(ccs.getDefendantIds(from, to, null, 0, 200)).thenReturn(Mono.error(error))
    `when`(ccs.getCids(from, to, null, 0, 200)).thenReturn(Mono.error(error))

    assertSame(error, assertThrows(IllegalStateException::class.java) { service.fetchAllDefendantIds("run", from, to, null) })
    assertSame(error, assertThrows(IllegalStateException::class.java) { service.fetchAllCids("run", from, to, null) })
  }
}
