package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichedMatchType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentResult
import java.util.Locale

fun EnrichmentResult.toPersistenceRequest(jobRunId: String): PersistEnrichmentResultRequest = PersistEnrichmentResultRequest(
  sourceType = sourceType,
  targetId = targetId,
  status = status,
  defendant = defendant?.let {
    PersistedDefendantDetails(
      cprUUID = it.cprUUID,
      name = it.name.let { name -> PersistedName(name.title, name.forename1, name.forename2, name.surname) },
      dateOfBirth = it.dateOfBirth,
      sex = it.sex?.toPersistedSex(jobRunId, sourceType.name),
      address = it.address?.let { address ->
        PersistedAddress(address.line1, address.line2, address.line3, address.line4, address.line5, address.postcode)
      },
    )
  },
  offender = offender?.let { PersistedOffenderDetails(it.crn, it.pnc, it.cro) },
  probation = probation?.let {
    PersistedProbationDetails(it.probationStatus, it.awaitingPsr, it.breach, it.previouslyKnownTerminationDate, it.preSentenceActivity)
  },
  groupedOffenderMatches = groupedOffenderMatches?.let {
    PersistedGroupedOffenderMatches(
      it.matches.map { match ->
        PersistedOffenderMatch(
          matchIdentifiers = match.matchIdentifiers.let { ids ->
            PersistedMatchIdentifiers(
              ids.crn,
              ids.pnc,
              ids.cro,
              ids.aliases?.map { alias -> PersistedAlias(alias.firstName, alias.surname, alias.middleNames) },
            )
          },
          matchType = when (match.matchType) {
            EnrichedMatchType.NAME_DOB_ALIAS -> PersistedMatchType.NAME_DOB_ALIAS
          },
          confirmed = match.confirmed,
          rejected = match.rejected,
        )
      },
    )
  },
  warnings = warnings,
  errors = errors,
  jobRunId = jobRunId,
)

private val log = LoggerFactory.getLogger("EnrichmentResultMapping")

private fun String.toPersistedSex(jobRunId: String, sourceType: String): String = when (trim().uppercase(Locale.ROOT).replace(' ', '_')) {
  "MALE", "M" -> "MALE"
  "FEMALE", "F" -> "FEMALE"
  "NOT_KNOWN", "N" -> "NOT_KNOWN"
  "NOT_SPECIFIED", "NS" -> "NOT_SPECIFIED"
  else -> {
    log.error(
      "Unrecognised defendant.sex mapped to NOT_KNOWN runId={} sourceType={}",
      jobRunId,
      sourceType,
    )
    "NOT_KNOWN"
  }
}
