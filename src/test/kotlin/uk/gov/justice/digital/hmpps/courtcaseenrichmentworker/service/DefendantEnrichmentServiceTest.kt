package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CprClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.OffenderSearchRestClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprAddress
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprAlias
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprDefendant
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprIdentifier
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprSex
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprTitle
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.OtherIds
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.ProbationStatusDetail
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.SearchResponse
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.offendersearch.SearchResponses
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DefendantEnrichmentServiceTest {
  private val cpr = mock(CprClient::class.java)
  private val search = mock(OffenderSearchRestClient::class.java)
  private val service = DefendantEnrichmentService(cpr, search)

  @Test
  fun `maps all CPR and exact probation details and searches the CRN only once`() {
    val person = CprDefendant(
      cprUUID = "record",
      firstName = "Alex",
      middleNames = "Middle Names",
      lastName = "Example",
      dateOfBirth = "1990-01-02",
      title = CprTitle(description = "Mr"),
      sex = CprSex(description = "Male"),
      addresses = listOf(
        CprAddress(endDate = "2020-01-01", buildingName = "Old"),
        CprAddress(buildingName = "Current", buildingNumber = "12", thoroughfareName = "Street", dependentLocality = "Locality", postTown = "Town", postcode = "AB1 2CD"),
        CprAddress(endDate = "", buildingName = "Later"),
      ),
      aliases = listOf(CprAlias(firstName = "Alias", lastName = "Example", middleNames = "Two Names")),
      identifiers = CprIdentifier(crns = listOf("X123")),
    )
    `when`(cpr.getCommonPlatformPerson("cp")).thenReturn(Mono.just(person))
    val date = LocalDate.of(2025, 3, 4)
    `when`(search.search("X123")).thenReturn(
      Mono.just(SearchResponses(listOf(SearchResponse(otherIds = OtherIds("X123", "cro", "pnc"), probationStatusDetail = ProbationStatusDetail("CURRENT", date, true, true, true))))),
    )

    val result = service.enrichCommonPlatformDefendant("cp")

    assertEquals(EnrichmentSourceType.COMMON_PLATFORM, result.sourceType)
    assertEquals("cp", result.targetId)
    assertEquals(EnrichmentStatus.ENRICHED, result.status)
    assertEquals("record", result.defendant?.cprUUID)
    assertEquals("Mr", result.defendant?.name?.title)
    assertEquals("Alex", result.defendant?.name?.forename1)
    assertEquals("Middle Names", result.defendant?.name?.forename2)
    assertEquals("Example", result.defendant?.name?.surname)
    assertEquals(LocalDate.of(1990, 1, 2), result.defendant?.dateOfBirth)
    assertEquals("Male", result.defendant?.sex)
    val address = result.defendant?.address
    assertEquals(listOf("Current", "12", "Street", "Locality", "Town", "AB1 2CD"), listOf(address?.line1, address?.line2, address?.line3, address?.line4, address?.line5, address?.postcode))
    assertEquals("X123", result.offender?.crn)
    assertEquals("pnc", result.offender?.pnc)
    assertEquals("cro", result.offender?.cro)
    assertEquals("CURRENT", result.probation?.probationStatus)
    assertEquals(date, result.probation?.previouslyKnownTerminationDate)
    assertEquals(true, result.probation?.awaitingPsr)
    assertEquals(true, result.probation?.breach)
    assertEquals(true, result.probation?.preSentenceActivity)
    val match = result.groupedOffenderMatches!!.matches.single()
    assertEquals("NAME_DOB_ALIAS", match.matchType.name)
    assertFalse(match.confirmed)
    assertFalse(match.rejected)
    assertEquals(listOf("Two Names"), match.matchIdentifiers.aliases!!.single().middleNames)
    assertEquals("Alias", match.matchIdentifiers.aliases.single().firstName)
    verify(search).search("X123")
  }

  @Test
  fun `Libra uses only the Libra CPR lookup and handles optional CPR fields`() {
    `when`(cpr.getLibraPerson("libra")).thenReturn(Mono.just(CprDefendant(firstName = "Alex")))

    val result = service.enrichLibraDefendant("libra")

    assertEquals(EnrichmentSourceType.LIBRA, result.sourceType)
    assertEquals(EnrichmentStatus.ENRICHED, result.status)
    assertNull(result.defendant?.dateOfBirth)
    assertNull(result.defendant?.address)
    assertNull(result.probation)
    assertNull(result.offender)
    assertNull(result.groupedOffenderMatches)
    verify(cpr).getLibraPerson("libra")
    verifyNoInteractions(search)
  }

  @Test
  fun `missing CPR record is no match and does not call offender search`() {
    `when`(cpr.getCommonPlatformPerson("cp")).thenReturn(Mono.empty())
    `when`(cpr.getLibraPerson("libra")).thenReturn(Mono.empty())

    assertEquals(EnrichmentStatus.NO_MATCH, service.enrichCommonPlatformDefendant("cp").status)
    assertEquals(EnrichmentStatus.NO_MATCH, service.enrichLibraDefendant("libra").status)
    verifyNoInteractions(search)
  }

  @Test
  fun `multiple CRNs create candidates without selecting exact details`() {
    stubPerson(listOf("X123", "X456"))
    stubSearch("X123", listOf(SearchResponse(otherIds = OtherIds("X123", "cro", "pnc"))))
    stubSearch("X456", emptyList())

    val result = service.enrichCommonPlatformDefendant("cp")

    assertEquals(EnrichmentStatus.NEEDS_REVIEW, result.status)
    assertNull(result.offender)
    assertNull(result.probation)
    assertEquals(listOf("X123", "X456"), result.groupedOffenderMatches!!.matches.map { it.matchIdentifiers.crn })
    assertEquals("pnc", result.groupedOffenderMatches.matches.first().matchIdentifiers.pnc)
    assertNull(result.groupedOffenderMatches.matches.last().matchIdentifiers.pnc)
    assertTrue(result.warnings.isNotEmpty())
  }

  @Test
  fun `ambiguous offender search retains first candidate identifiers but never selects exact details`() {
    stubPerson(listOf("X123"))
    stubSearch("X123", listOf(SearchResponse(otherIds = OtherIds("X123", pncNumber = "first")), SearchResponse(otherIds = OtherIds("X123", pncNumber = "second"))))

    val result = service.enrichCommonPlatformDefendant("cp")

    assertEquals(EnrichmentStatus.NEEDS_REVIEW, result.status)
    assertNull(result.offender)
    assertNull(result.probation)
    assertEquals("first", result.groupedOffenderMatches!!.matches.single().matchIdentifiers.pnc)
  }

  @Test
  fun `no offender search response retains the CPR candidate and requires review`() {
    stubPerson(listOf("X123"))
    stubSearch("X123", emptyList())

    val result = service.enrichCommonPlatformDefendant("cp")

    assertEquals(EnrichmentStatus.NEEDS_REVIEW, result.status)
    assertNull(result.offender)
    assertNull(result.probation)
    val identifiers = result.groupedOffenderMatches!!.matches.single().matchIdentifiers
    assertEquals("X123", identifiers.crn)
    assertNull(identifiers.pnc)
    assertNull(identifiers.cro)
    assertNull(identifiers.aliases)
  }

  @Test
  fun `ended addresses are not selected and absent alias middle names remain null`() {
    `when`(cpr.getCommonPlatformPerson("cp")).thenReturn(
      Mono.just(
        CprDefendant(
          addresses = listOf(CprAddress(endDate = "2020-01-01", buildingName = "Old")),
          aliases = listOf(CprAlias(firstName = "Alias")),
          identifiers = CprIdentifier(crns = listOf("X123")),
        ),
      ),
    )
    stubSearch("X123", listOf(SearchResponse(otherIds = OtherIds("X123"))))

    val result = service.enrichCommonPlatformDefendant("cp")

    assertNull(result.defendant?.address)
    assertNull(result.groupedOffenderMatches!!.matches.single().matchIdentifiers.aliases!!.single().middleNames)
  }

  @Test
  fun `absent probation details use the existing false defaults`() {
    stubPerson(listOf("X123"))
    stubSearch("X123", listOf(SearchResponse(otherIds = OtherIds("X123"))))

    val result = service.enrichCommonPlatformDefendant("cp")

    assertEquals("X123", result.offender?.crn)
    assertNull(result.probation?.probationStatus)
    assertNull(result.probation?.breach)
    assertNull(result.probation?.previouslyKnownTerminationDate)
    assertEquals(false, result.probation?.awaitingPsr)
    assertEquals(false, result.probation?.preSentenceActivity)
  }

  @Test
  fun `duplicate CPR CRNs are searched only once without changing candidate cardinality`() {
    stubPerson(listOf("X123", "X123"))
    stubSearch("X123", listOf(SearchResponse(otherIds = OtherIds("X123"))))

    val result = service.enrichCommonPlatformDefendant("cp")

    assertEquals(2, result.groupedOffenderMatches!!.matches.size)
    assertNull(result.offender)
    verify(search).search("X123")
  }

  @Test
  fun `invalid targets fail before calling downstream services`() {
    assertThrows(IllegalArgumentException::class.java) { service.enrichCommonPlatformDefendant(" ") }
    assertThrows(IllegalArgumentException::class.java) { service.enrichLibraDefendant("") }
    verifyNoInteractions(cpr, search)
  }

  @Test
  fun `malformed CPR dates and blank CRNs fail explicitly`() {
    `when`(cpr.getCommonPlatformPerson("cp")).thenReturn(Mono.just(CprDefendant(dateOfBirth = "invalid")))
    assertThrows(DateTimeParseException::class.java) { service.enrichCommonPlatformDefendant("cp") }
    stubPerson(listOf(" "))
    assertThrows(IllegalStateException::class.java) { service.enrichCommonPlatformDefendant("cp") }
    verifyNoInteractions(search)
  }

  @Test
  fun `missing or inconsistent exact offender identifiers fail explicitly`() {
    stubPerson(listOf("X123"))
    stubSearch("X123", listOf(SearchResponse()))
    assertThrows(IllegalStateException::class.java) { service.enrichCommonPlatformDefendant("cp") }
    stubSearch("X123", listOf(SearchResponse(otherIds = OtherIds("OTHER"))))
    assertThrows(IllegalStateException::class.java) { service.enrichCommonPlatformDefendant("cp") }
  }

  @Test
  fun `downstream errors propagate instead of becoming no match`() {
    val error = IllegalStateException("downstream unavailable")
    `when`(cpr.getCommonPlatformPerson("cp")).thenReturn(Mono.error(error))
    assertSame(error, assertThrows(IllegalStateException::class.java) { service.enrichCommonPlatformDefendant("cp") })
    stubPerson(listOf("X123"))
    `when`(search.search("X123")).thenReturn(Mono.error(error))
    assertSame(error, assertThrows(IllegalStateException::class.java) { service.enrichCommonPlatformDefendant("cp") })
  }

  private fun stubPerson(crns: List<String>) {
    `when`(cpr.getCommonPlatformPerson("cp")).thenReturn(Mono.just(CprDefendant(identifiers = CprIdentifier(crns = crns))))
  }

  private fun stubSearch(crn: String, responses: List<SearchResponse>) {
    `when`(search.search(crn)).thenReturn(Mono.just(SearchResponses(responses)))
  }
}
