package uk.gov.justice.digital.hmpps.courtcaseenrichmentworker.model

import java.time.LocalDate

enum class EnrichmentSourceType {
  COMMON_PLATFORM,
  LIBRA,
}

enum class EnrichmentStatus {
  ENRICHED,
  NO_MATCH,
  PARTIAL,
  FAILED,
  NEEDS_REVIEW,
}

data class EnrichmentResult(
  val sourceType: EnrichmentSourceType,
  val targetId: String,
  val status: EnrichmentStatus,
  val defendant: EnrichedDefendant? = null,
  val offender: EnrichedOffender? = null,
  val probation: EnrichedProbation? = null,
  val groupedOffenderMatches: EnrichedGroupedOffenderMatches? = null,
  val warnings: List<String> = emptyList(),
  val errors: List<String> = emptyList(),
)

data class EnrichedOffender(
  val crn: String? = null,
  val pnc: String? = null,
  val cro: String? = null,
)

data class EnrichedProbation(
  val probationStatus: String? = null,
  val awaitingPsr: Boolean? = null,
  val breach: Boolean? = null,
  val previouslyKnownTerminationDate: LocalDate? = null,
  val preSentenceActivity: Boolean = false,
)

data class EnrichedDefendant(
  val cprUUID: String? = null,
  val name: EnrichedName,
  val dateOfBirth: LocalDate? = null,
  val sex: String? = null,
  val address: EnrichedAddress? = null,
)

data class EnrichedName(
  val title: String? = null,
  val forename1: String? = null,
  val forename2: String? = null,
  val surname: String? = null,
)

data class EnrichedAddress(
  val line1: String? = null,
  val line2: String? = null,
  val line3: String? = null,
  val line4: String? = null,
  val line5: String? = null,
  val postcode: String? = null,
)

data class EnrichedGroupedOffenderMatches(
  val matches: List<EnrichedOffenderMatch>,
)

data class EnrichedOffenderMatch(
  val matchIdentifiers: EnrichedMatchIdentifiers,
  val matchType: EnrichedMatchType = EnrichedMatchType.NAME_DOB_ALIAS,
  val confirmed: Boolean = false,
  val rejected: Boolean = false,
)

enum class EnrichedMatchType {
  NAME_DOB_ALIAS,
}

data class EnrichedMatchIdentifiers(
  val crn: String,
  val pnc: String? = null,
  val cro: String? = null,
  val aliases: List<EnrichedAlias>? = null,
)

data class EnrichedAlias(
  val firstName: String? = null,
  val surname: String? = null,
  val middleNames: List<String>? = null,
)
