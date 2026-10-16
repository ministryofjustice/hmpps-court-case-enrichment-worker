package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.restclient.model.courtcase

import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentSourceType
import uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model.EnrichmentStatus
import java.time.LocalDate

data class PersistEnrichmentResultRequest(
  val sourceType: EnrichmentSourceType,
  val targetId: String,
  val status: EnrichmentStatus,
  val defendant: PersistedDefendantDetails? = null,
  val offender: PersistedOffenderDetails? = null,
  val probation: PersistedProbationDetails? = null,
  val groupedOffenderMatches: PersistedGroupedOffenderMatches? = null,
  val warnings: List<String> = emptyList(),
  val errors: List<String> = emptyList(),
  val enrichedBy: String = "court-case-enrichment-worker",
  val jobRunId: String? = null,
)

data class PersistedOffenderDetails(
  val crn: String? = null,
  val pnc: String? = null,
  val cro: String? = null,
)

data class PersistedProbationDetails(
  val probationStatus: String? = null,
  val awaitingPsr: Boolean? = null,
  val breach: Boolean? = null,
  val previouslyKnownTerminationDate: LocalDate? = null,
  val preSentenceActivity: Boolean? = null,
)

data class PersistedDefendantDetails(
  val cprUUID: String? = null,
  val name: PersistedName? = null,
  val dateOfBirth: LocalDate? = null,
  val sex: String? = null,
  val address: PersistedAddress? = null,
)

data class PersistedName(
  val title: String? = null,
  val forename1: String? = null,
  val forename2: String? = null,
  val surname: String? = null,
)

data class PersistedAddress(
  val line1: String? = null,
  val line2: String? = null,
  val line3: String? = null,
  val line4: String? = null,
  val line5: String? = null,
  val postcode: String? = null,
)

data class PersistedGroupedOffenderMatches(
  val matches: List<PersistedOffenderMatch>,
)

data class PersistedOffenderMatch(
  val matchIdentifiers: PersistedMatchIdentifiers,
  val matchType: PersistedMatchType = PersistedMatchType.NAME_DOB_ALIAS,
  val confirmed: Boolean = false,
  val rejected: Boolean = false,
)

enum class PersistedMatchType {
  NAME_DOB_ALIAS,
}

data class PersistedMatchIdentifiers(
  val crn: String,
  val pnc: String? = null,
  val cro: String? = null,
  val aliases: List<PersistedAlias>? = null,
)

data class PersistedAlias(
  val firstName: String? = null,
  val surname: String? = null,
  val middleNames: List<String>? = null,
)
