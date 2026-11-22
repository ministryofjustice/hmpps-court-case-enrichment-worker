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
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.CidsResponse
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase.DefendantIdsResponse
import java.time.LocalDate

class EnrichmentTargetServiceTest {
  private val ccs = mock(CourtCaseServiceClient::class.java)
  private val service = EnrichmentTargetService(ccs)
  private val from = LocalDate.of(2026, 10, 14)
  private val to = from.plusDays(14)

  @Test
  fun `fetches all Common Platform targets for one court in one request`() {
    `when`(ccs.getDefendantIds(from, to, "COURT"))
      .thenReturn(Mono.just(DefendantIdsResponse(listOf("cp1", "cp2"))))

    assertEquals(listOf("cp1", "cp2"), service.fetchDefendantIds("run", from, to, "COURT"))

    verify(ccs).getDefendantIds(from, to, "COURT")
    verifyNoMoreInteractions(ccs)
  }

  @Test
  fun `fetches all Libra targets for one court in one request`() {
    `when`(ccs.getCids(from, to, "COURT"))
      .thenReturn(Mono.just(CidsResponse(listOf("libra1", "libra2"))))

    assertEquals(listOf("libra1", "libra2"), service.fetchCids("run", from, to, "COURT"))

    verify(ccs).getCids(from, to, "COURT")
    verifyNoMoreInteractions(ccs)
  }

  @Test
  fun `empty target responses return no targets`() {
    `when`(ccs.getDefendantIds(from, to, "COURT")).thenReturn(Mono.just(DefendantIdsResponse(emptyList())))
    `when`(ccs.getCids(from, to, "COURT")).thenReturn(Mono.just(CidsResponse(emptyList())))

    assertEquals(emptyList<String>(), service.fetchDefendantIds("run", from, to, "COURT"))
    assertEquals(emptyList<String>(), service.fetchCids("run", from, to, "COURT"))
  }

  @Test
  fun `propagates downstream failures`() {
    val error = IllegalStateException("CCS unavailable")
    `when`(ccs.getDefendantIds(from, to, "COURT")).thenReturn(Mono.error(error))
    `when`(ccs.getCids(from, to, "COURT")).thenReturn(Mono.error(error))

    assertSame(error, assertThrows(IllegalStateException::class.java) { service.fetchDefendantIds("run", from, to, "COURT") })
    assertSame(error, assertThrows(IllegalStateException::class.java) { service.fetchCids("run", from, to, "COURT") })
  }
}
