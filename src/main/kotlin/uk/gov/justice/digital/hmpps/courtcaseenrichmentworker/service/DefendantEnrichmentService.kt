package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedAddress
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedAlias
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedDefendant
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedGroupedOffenderMatches
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedMatchIdentifiers
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedName
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedOffender
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedOffenderMatch
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedProbation
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.CprClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.OffenderSearchRestClient
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.cprservice.CprDefendant
import java.time.LocalDate

@Service
class DefendantEnrichmentService(
  private val cprClient: CprClient,
  private val offenderSearchClient: OffenderSearchRestClient,
) {
  fun enrichCommonPlatformDefendant(defendantId: String): EnrichmentResult {
    require(defendantId.isNotBlank()) { "defendantId must not be blank" }
    return enrich(EnrichmentSourceType.COMMON_PLATFORM, defendantId, cprClient.getCommonPlatformPerson(defendantId).block())
  }

  fun enrichLibraDefendant(cId: String): EnrichmentResult {
    require(cId.isNotBlank()) { "cId must not be blank" }
    return enrich(EnrichmentSourceType.LIBRA, cId, cprClient.getLibraPerson(cId).block())
  }

  private fun enrich(source: EnrichmentSourceType, targetId: String, person: CprDefendant?): EnrichmentResult {
    if (person == null) {
      log.info("No CPR record found for enrichment source={}", source)
      return EnrichmentResult(source, targetId, EnrichmentStatus.NO_MATCH)
    }

    val defendant = mapDefendant(person)
    val crns = person.identifiers?.crns.orEmpty()
    check(crns.all { it.isNotBlank() }) { "CPR returned a blank CRN" }
    val searches = crns.distinct().associateWith { crn ->
      offenderSearchClient.search(crn).block()?.searchResponses
        ?: error("Offender Search returned no response")
    }
    val aliases = person.aliases?.takeIf { it.isNotEmpty() }?.map {
      EnrichedAlias(it.firstName, it.lastName, it.middleNames?.let(::listOf))
    }
    val matches = crns.map { crn ->
      val ids = searches.getValue(crn).firstOrNull()?.otherIds
      EnrichedOffenderMatch(
        EnrichedMatchIdentifiers(crn, ids?.pncNumber, ids?.croNumber, aliases),
      )
    }
    val exactResponse = if (crns.size == 1) searches.getValue(crns.single()).singleOrNull() else null
    val exactIds = exactResponse?.otherIds
    if (exactResponse != null) {
      check(exactIds?.crn == crns.single()) { "Offender Search returned missing or inconsistent CRN identifiers" }
    }
    val probation = exactResponse?.let {
      EnrichedProbation(
        probationStatus = it.probationStatusDetail?.status,
        awaitingPsr = it.probationStatusDetail?.awaitingPsr ?: false,
        breach = it.probationStatusDetail?.inBreach,
        previouslyKnownTerminationDate = it.probationStatusDetail?.previouslyKnownTerminationDate,
        preSentenceActivity = it.probationStatusDetail?.preSentenceActivity ?: false,
      )
    }
    val warnings = buildList {
      if (crns.size > 1) add("CPR has multiple CRNs; no exact offender or probation details were selected")
      if (searches.values.any { it.size != 1 }) add("Offender Search did not return exactly one response for every CPR CRN")
    }
    return EnrichmentResult(
      sourceType = source,
      targetId = targetId,
      status = if (warnings.isEmpty()) EnrichmentStatus.ENRICHED else EnrichmentStatus.NEEDS_REVIEW,
      defendant = defendant,
      offender = exactIds?.let { EnrichedOffender(it.crn, it.pncNumber, it.croNumber) },
      probation = probation,
      groupedOffenderMatches = matches.takeIf { it.isNotEmpty() }?.let(::EnrichedGroupedOffenderMatches),
      warnings = warnings,
    )
  }

  private fun mapDefendant(person: CprDefendant): EnrichedDefendant {
    val address = person.addresses?.firstOrNull { it.endDate.isNullOrEmpty() }
    return EnrichedDefendant(
      cprUUID = person.cprUUID,
      name = EnrichedName(person.title?.description, person.firstName, person.middleNames, person.lastName),
      dateOfBirth = person.dateOfBirth?.let(LocalDate::parse),
      sex = person.sex?.description,
      address = address?.let {
        EnrichedAddress(it.buildingName, it.buildingNumber, it.thoroughfareName, it.dependentLocality, it.postTown, it.postcode)
      },
    )
  }

  private companion object {
    val log = LoggerFactory.getLogger(DefendantEnrichmentService::class.java)
  }
}
